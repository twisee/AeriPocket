package com.controleco.app.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.controleco.app.BuildConfig
import com.controleco.app.capture.CaptureAppCatalog
import com.controleco.app.capture.CaptureCenter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal data class DiagnosticState(
    val enabled: Boolean = false, val startedAt: Long = 0, val summary: DiagnosticDiskSummary = DiagnosticDiskSummary(),
    val dropped: Long = 0, val failures: Long = 0, val error: String? = null,
)

/** Bounded, non-blocking capture calls; disk work and exports run in order on one IO worker. */
internal object CaptureDiagnostics {
    private var engine: Engine? = null
    private val stateValue = MutableStateFlow(DiagnosticState())
    val state: StateFlow<DiagnosticState> = stateValue
    fun init(application: Application) {
        engine?.close()
        engine = Engine(application)
    }
    fun enabled(): Boolean = stateValue.value.enabled
    fun setEnabled(value: Boolean) { engine?.enable(value) }
    fun record(kind: String, source: String, status: String, detail: String, fields: Map<String, String> = emptyMap()) {
        engine?.record(kind, source, status, detail, fields)
    }
    fun scopedPackage(packageName: String): Boolean = engine?.scopedPackage(packageName) == true
    fun identity(value: String): String = if (value.isBlank()) "" else MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    fun checkpoint(reason: String) { engine?.checkpoint(reason) }
    suspend fun refresh() { engine?.barrier { it.refresh() } }
    suspend fun clear() { engine?.clear() }
    suspend fun export(): File = checkNotNull(engine) { "诊断尚未初始化" }.export()

    private class Engine(private val app: Application) {
        private val preferences = app.getSharedPreferences("capture_diagnostics", Context.MODE_PRIVATE)
        private val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val disk = DiagnosticFiles(File(app.filesDir, "capture-diagnostics"))
        private val session = UUID.randomUUID().toString()
        private val epoch = AtomicLong()
        private val dropped = AtomicLong(preferences.getLong("dropped", 0))
        private val coalesced = AtomicLong(preferences.getLong("coalesced", 0))
        private var failures = preferences.getLong("failures", 0)
        private val queue = Channel<suspend () -> Unit>(512)
        private var lastSummary = DiagnosticDiskSummary()
        private val repeats = LinkedHashMap<String, Long>()
        private val settingListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in stateKeys) checkpoint("设置变更：${settingLabels[key] ?: "采集选项"}")
        }
        private val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { checkpoint("应用进入前台") }
            override fun onActivityPaused(activity: Activity) { record("service", "应用", "状态变化", "应用离开前台") }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }

        init {
            stateValue.value = DiagnosticState(preferences.getBoolean("enabled", false), preferences.getLong("started", 0))
            scope.launch {
                File(app.cacheDir, "diagnostic-exports").listFiles().orEmpty()
                    .filter { it.isFile && it.name.startsWith("AeriPocket-diagnostics-") && it.lastModified() < System.currentTimeMillis() - 86_400_000L }
                    .forEach { it.delete() }
                runCatching { lastSummary = disk.refresh(); publish() }.onFailure(::failed)
                for (task in queue) { runCatching { task() }.onFailure(::failed) }
            }
            settings.registerOnSharedPreferenceChangeListener(settingListener)
            app.registerActivityLifecycleCallbacks(callbacks)
            checkpoint("应用进程启动；此前未记录的时段可能存在采集空档")
            scope.launch {
                while (isActive) { delay(5 * 60_000L); checkpoint("进程存活检查（不代表支付一定可识别）") }
            }
        }

        fun close() {
            settings.unregisterOnSharedPreferenceChangeListener(settingListener)
            app.unregisterActivityLifecycleCallbacks(callbacks)
            queue.close(); scope.cancel()
        }

        private fun publish(error: String? = null) {
            stateValue.value = stateValue.value.copy(summary = lastSummary, dropped = dropped.get(), failures = failures, error = error)
        }
        private fun failed(error: Throwable) {
            failures++
            preferences.edit().putLong("failures", failures).putLong("dropped", dropped.get()).apply()
            publish("日志写入或导出失败：${error.javaClass.simpleName}")
        }

        fun enable(value: Boolean) {
            if (value == stateValue.value.enabled) return
            val started = if (value) System.currentTimeMillis() else stateValue.value.startedAt
            epoch.incrementAndGet()
            preferences.edit().putBoolean("enabled", value).putLong("started", started).apply()
            stateValue.value = stateValue.value.copy(enabled = value, startedAt = started)
            // Explicit stop/start markers are permitted even when normal capture writing is disabled.
            val record = DiagnosticRecord(System.currentTimeMillis(), "service", "诊断", "状态变化",
                if (value) "开启诊断；此前的支付无法追溯" else "关闭诊断；此后停止记录", mapOf("session" to session))
            submit { lastSummary = disk.append(record); publish() }
            if (value) checkpoint("诊断开启时的设置与权限")
        }

        private fun submit(task: suspend () -> Unit) {
            if (queue.trySend(task).isFailure) {
                dropped.incrementAndGet()
                preferences.edit().putLong("dropped", dropped.get()).apply()
                stateValue.value = stateValue.value.copy(dropped = dropped.get(), error = "记录繁忙，部分事件未能写入")
            }
        }

        fun scopedPackage(packageName: String): Boolean = runCatching {
            CaptureAppCatalog.pageCaptureAllowed(app, packageName)
        }.getOrDefault(false)

        fun record(kind: String, source: String, status: String, detail: String, fields: Map<String, String> = emptyMap()) {
            if (!enabled()) return
            val generation = epoch.get()
            val now = System.currentTimeMillis()
            // Coalesce identical events within 3 seconds, without conflating distinct transaction identities.
            val fingerprintFields = if (kind == "page") fields.filterKeys { it != "eventId" } else fields
            val stamp = "$kind|$source|$status|$detail|$fingerprintFields"
            synchronized(repeats) {
                if (now - (repeats[stamp] ?: 0) in 0 until 3_000) { coalesced.incrementAndGet(); return }
                repeats[stamp] = now
                while (repeats.size > 128) repeats.remove(repeats.keys.first())
            }
            val limited = fields.mapValues { it.value.take(24_000) } + ("session" to session) +
                ("truncatedFields" to fields.filterValues { it.length > 24_000 }.keys.joinToString(","))
            val record = DiagnosticRecord(now, kind, source, status, detail.take(24_000), limited)
            submit {
                if (generation == epoch.get() && enabled()) {
                    lastSummary = disk.append(record)
                    preferences.edit().putLong("dropped", dropped.get()).putLong("coalesced", coalesced.get()).apply()
                    publish()
                }
            }
        }

        fun checkpoint(reason: String) {
            if (enabled()) record("service", "设置与服务", "状态快照", reason, snapshot())
        }

        private fun snapshot(): Map<String, String> {
            val result = linkedMapOf<String, String>()
            for (key in stateKeys) result[key] = settings.all[key]?.toString() ?: "未显式设置（使用默认值）"
            runCatching {
                result["captureEnabledEffective"] = CaptureCenter.isCaptureEnabled().toString()
                result["pageCaptureEffective"] = CaptureCenter.isA11yCaptureEnabled().toString()
                result["autoEntryEffective"] = CaptureCenter.isAutoEntryEnabled().toString()
                result["overlayEffective"] = CaptureCenter.isOverlayConfirmEnabled().toString()
                result["entryNotificationEffective"] = CaptureCenter.isCaptureNotifyEnabled().toString()
                result["notificationListenerGranted"] = (app.packageName in NotificationManagerCompat.getEnabledListenerPackages(app)).toString()
                result["notificationListenerConnected"] = CaptureCenter.connected.value.toString()
                result["accessibilityConnected"] = CaptureCenter.a11yConnected.value.toString()
                result["overlayGranted"] = Settings.canDrawOverlays(app).toString()
                result["ownNotificationsGranted"] = NotificationManagerCompat.from(app).areNotificationsEnabled().toString()
                result["receiveSmsGranted"] = (ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECEIVE_SMS) == 0).toString()
                result["watchedPackages"] = CaptureAppCatalog.watched(app).sorted().joinToString(",")
                val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                result["batteryOptimizationExempt"] = power.isIgnoringBatteryOptimizations(app.packageName).toString()
            }.onFailure { result["snapshotError"] = it.javaClass.simpleName }
            return result
        }

        suspend fun barrier(action: (DiagnosticFiles) -> DiagnosticDiskSummary) {
            val completion = CompletableDeferred<Unit>()
            queue.send {
                try { lastSummary = action(disk); publish(); completion.complete(Unit) }
                catch (error: Exception) { completion.completeExceptionally(error); throw error }
            }
            completion.await()
        }

        suspend fun clear() {
            barrier {
                val cleared = it.clear()
                File(app.cacheDir, "diagnostic-exports").listFiles().orEmpty()
                    .filter { file -> file.isFile && file.name.startsWith("AeriPocket-diagnostics-") }.forEach { file -> file.delete() }
                dropped.set(0); coalesced.set(0); failures = 0
                preferences.edit().putLong("dropped", 0).putLong("coalesced", 0).putLong("failures", 0).apply()
                cleared
            }
        }

        suspend fun export(): File {
            val completion = CompletableDeferred<File>()
            val settingsNow = withContext(Dispatchers.IO) { snapshot() }
            queue.send {
                try {
                    val dir = File(app.cacheDir, "diagnostic-exports").apply { mkdirs() }
                    dir.listFiles().orEmpty().filter { it.name.startsWith("AeriPocket-diagnostics-") && it.isFile }
                        .sortedByDescending { it.lastModified() }.drop(2).forEach { it.delete() }
                    val file = File(dir, "AeriPocket-diagnostics-${System.currentTimeMillis()}.zip")
                    val metadata = JSONObject().put("formatVersion", 1).put("appVersion", BuildConfig.VERSION_NAME)
                        .put("versionCode", BuildConfig.VERSION_CODE).put("package", app.packageName)
                        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("android", Build.VERSION.RELEASE)
                        .put("sdk", Build.VERSION.SDK_INT).put("exportedAt", System.currentTimeMillis())
                        .put("timeZone", java.time.ZoneId.systemDefault().id).put("diagnosticEnabled", enabled())
                        .put("droppedEvents", dropped.get()).put("coalescedEvents", coalesced.get())
                        .put("writeFailures", failures).put("settings", JSONObject(settingsNow))
                    lastSummary = disk.export(file, metadata)
                    publish(); completion.complete(file)
                } catch (error: Exception) { completion.completeExceptionally(error); throw error }
            }
            return completion.await()
        }
    }

    private val stateKeys = setOf("auto_capture_enabled", "a11y_capture_enabled", "capture_notify_enabled", "auto_entry_enabled",
        "overlay_confirm_enabled", "bank_push_enabled", "sms_capture_enabled", "amount_reply_enabled", "refund_link_enabled",
        "debug_capture_all", "debug_capture_all_pages", "capture_trace_enabled", "watched_packages")
    private val settingLabels = mapOf("auto_capture_enabled" to "采集总开关", "a11y_capture_enabled" to "无障碍页面记账",
        "capture_notify_enabled" to "入账提醒", "auto_entry_enabled" to "自动入账", "overlay_confirm_enabled" to "悬浮窗入账",
        "bank_push_enabled" to "银行推送", "sms_capture_enabled" to "银行短信", "amount_reply_enabled" to "金额补录",
        "refund_link_enabled" to "退款处理", "watched_packages" to "监听的 App")
}
