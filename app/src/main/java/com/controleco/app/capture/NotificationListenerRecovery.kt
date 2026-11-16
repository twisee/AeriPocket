package com.controleco.app.capture

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.controleco.app.diagnostics.CaptureDiagnostics

/** 进程启动本身也能触发恢复；不依赖服务先存在并收到 onListenerDisconnected。 */
internal object NotificationListenerRecovery {
    private val handler = Handler(Looper.getMainLooper())
    private val policy = ListenerRecoveryPolicy()
    private var app: Context? = null
    private val check = Runnable { tick() }

    fun start(context: Context) {
        app = context.applicationContext
        checkNow()
    }

    fun checkNow() {
        handler.removeCallbacks(check)
        handler.post(check)
    }

    private fun tick() {
        val context = app ?: return
        val granted = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        val now = SystemClock.elapsedRealtime()
        val wait = policy.waitMillis(now, CaptureCenter.isCaptureEnabled() && granted, CaptureCenter.connected.value)
        if (wait == 0L) {
            policy.attempted(now)
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(context, NotificationCaptureService::class.java))
            }.onSuccess {
                CaptureDiagnostics.record("service", "通知恢复", "已请求重连", "已授权但未连接；等待系统连接回调",
                    mapOf("attempt" to policy.attempts.toString()))
            }.onFailure {
                CaptureDiagnostics.record("service", "通知恢复", "恢复请求失败", it.javaClass.simpleName,
                    mapOf("attempt" to policy.attempts.toString()))
            }
        }
        // 只用进程内计时，不唤醒设备；恢复后/关闭采集时低频检查，前台返回立即重查。
        val next = policy.waitMillis(SystemClock.elapsedRealtime(), CaptureCenter.isCaptureEnabled() && granted,
            CaptureCenter.connected.value) ?: 60_000L
        handler.postDelayed(check, next.coerceAtLeast(100L))
    }
}

internal class ListenerRecoveryPolicy {
    var attempts = 0
        private set
    private var nextAt = 0L

    fun waitMillis(now: Long, eligible: Boolean, connected: Boolean): Long? {
        if (!eligible || connected) { attempts = 0; nextAt = 0L; return null }
        return (nextAt - now).coerceAtLeast(0L)
    }

    fun attempted(now: Long) {
        val delays = longArrayOf(1_500L, 6_000L, 30_000L, 300_000L)
        nextAt = now + delays[attempts.coerceAtMost(delays.lastIndex)]
        attempts = (attempts + 1).coerceAtMost(1_000)
    }
}
