package com.controleco.app.ui.debug

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controleco.app.diagnostics.CaptureDiagnostics
import com.controleco.app.diagnostics.DiagnosticRecord
import com.controleco.app.ui.common.*
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun DiagnosticScreen(onBack: () -> Unit, onExport: () -> Unit) {
    val state by CaptureDiagnostics.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var clearAsk by remember { mutableStateOf(false) }
    val c = MaterialTheme.eco
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { runCatching { CaptureDiagnostics.refresh() } }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsPage(micro = "DIAGNOSTICS", title = "记账诊断", onBack = onBack) {
        SettingsLead("正常使用几天，再导出日志排查漏记、重复或金额识别问题。")
        Spacer(Modifier.height(12.dp))
        SettingsPane {
            SettingsSwitchRow(title = "开启记账诊断", subtitle = "只记录本机，开启后开始收集",
                modifier = Modifier.clickable { CaptureDiagnostics.setEnabled(!state.enabled) },
                checked = state.enabled, onCheckedChange = CaptureDiagnostics::setEnabled)
            SettingsDivider()
            SettingsValueRow("记录状态", if (state.enabled) "正在记录" else "已关闭 · 保留历史", valueColor = c.blueDeep)
            if (state.startedAt > 0) { SettingsDivider(); SettingsValueRow("本轮开启", diagnosticTime(state.startedAt)) }
            SettingsDivider()
            SettingsValueRow("本地日志", "${state.summary.count} 条 · ${diagnosticSize(state.summary.bytes)}")
            SettingsDivider()
            SettingsValueRow("保留时间", "最近 7 天 · 容量上限 32 MB")
        }
        if (state.error != null || state.dropped > 0 || state.failures > 0 || state.summary.evictedChunks > 0 || state.summary.damagedLines > 0) {
            SettingsNote(state.error ?: "记录可能不完整：队列遗漏 ${state.dropped} 次、写入失败 ${state.failures} 次，清理过 ${state.summary.evictedChunks} 个历史文件。导出包会注明缺失情况。")
        }
        SettingsSection("最近记录", aside = "LATEST")
        SettingsPane {
            if (state.summary.recent.isEmpty()) {
                SettingsRow(title = if (state.enabled) "等待新的捕获记录" else "开启后开始记录",
                    subtitle = "不会补录开启前的付款；不需要打开全量调试开关。", icon = EcoIcons.File)
            } else state.summary.recent.take(6).forEachIndexed { index, record ->
                if (index > 0) SettingsDivider()
                DiagnosticRecentRow(record)
            }
        }
        SettingsNote("仅记录已监听 App 的相关通知、页面与服务状态。手机号与银行卡号默认遮罩，保留必要的金额和商户文字。日志不代替系统采集权限。")
        Spacer(Modifier.height(14.dp))
        EcoPrimaryButton("导出诊断日志", onClick = onExport, leading = EcoIcons.Download, enabled = state.summary.count > 0)
        Spacer(Modifier.height(8.dp))
        SettingsDangerRow(title = "清空诊断记录", subtitle = "不影响账单", actionText = "清空", onClick = { clearAsk = true })
    }
    if (clearAsk) EcoConfirm(title = "清空诊断记录", body = "清除本地诊断日志及已生成的诊断包，不影响账单和待确认条目。",
        confirmText = "清空", danger = true, onDismiss = { clearAsk = false }, onConfirm = {
            clearAsk = false
            scope.launch {
                runCatching { CaptureDiagnostics.clear() }.onSuccess { EcoToast.show("已清空诊断记录") }
                    .onFailure { EcoToast.show("清空失败，请稍后重试") }
            }
        })
}

@Composable
private fun DiagnosticRecentRow(record: DiagnosticRecord) {
    val c = MaterialTheme.eco
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(diagnosticTime(record.time), style = EcoType.RowSub, color = c.quiet)
            Spacer(Modifier.weight(1f))
            Text(record.status, style = EcoType.RowTrail, color = when (record.status) {
                "已入账", "已合并" -> c.jade
                "未匹配", "异常或提示", "已跳过" -> c.amber
                else -> c.blueDeep
            })
        }
        Spacer(Modifier.height(6.dp))
        val source = when (record.source) {
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.tencent.mm" -> "微信"
            "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew" -> "美团"
            else -> record.source
        }
        Text(source, style = EcoType.RowTitle, color = c.ink, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(record.detail, fontSize = 10.sp, lineHeight = 15.sp, color = c.muted, maxLines = 3)
    }
}

@Composable
internal fun DiagnosticExportScreen(onBack: () -> Unit) {
    val state by CaptureDiagnostics.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.eco
    var busy by remember { mutableStateOf(false) }
    var exportPath by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val path = exportPath
        if (uri != null && path != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val file = File(path)
                    check(file.canonicalFile.parentFile == File(context.cacheDir, "diagnostic-exports").canonicalFile)
                    context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: error("无法打开保存位置")
                }
                EcoToast.show("诊断日志已保存")
            } catch (_: Exception) { EcoToast.show("保存失败，请重新导出") }
            finally { busy = false }
        }
    }
    fun generate(share: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val file = CaptureDiagnostics.export()
                exportPath = file.absolutePath
                if (share) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("记账诊断", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享诊断日志"))
                } else save.launch(file.name)
            } catch (_: Exception) { EcoToast.show("导出失败，请检查存储空间后重试") }
            finally { busy = false }
        }
    }
    SettingsPage(micro = "EXPORT", title = "导出诊断日志", onBack = onBack) {
        SettingsLead("将这几天的诊断记录整理成一个文件，便于排查记账问题。")
        SettingsSection("导出范围", aside = "RANGE")
        SettingsPane {
            SettingsValueRow("时间范围", "最近 7 天", valueColor = c.blueDeep)
            SettingsDivider()
            SettingsValueRow("已有记录", if (state.summary.count == 0L) "暂无" else "${diagnosticDate(state.summary.firstAt)} — ${diagnosticDate(state.summary.lastAt)}")
            SettingsDivider()
            SettingsValueRow("记录数量", "${state.summary.count} 条")
        }
        SettingsSection("文件包含", aside = "CONTENTS")
        SettingsPane {
            SettingsRow(title = "通知、页面与短信记录", subtitle = "接收时间、必要原文和解析结果", icon = EcoIcons.File)
            SettingsDivider()
            SettingsRow(title = "入账处理过程", subtitle = "入账、待确认、合并、跳过和失败原因", icon = EcoIcons.Receipt)
            SettingsDivider()
            SettingsRow(title = "设置与服务状态", subtitle = "相关开关、权限、连接与重连记录", icon = EcoIcons.Shield)
            SettingsDivider()
            SettingsRow(title = "设备与版本信息", subtitle = "手机型号、系统和软件版本", icon = EcoIcons.Clock)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.blueSoft).padding(14.dp)) {
            Text("由你手动分享", style = EcoType.RowTitle, color = c.blueDeep)
            Spacer(Modifier.height(5.dp))
            Text("日志可能包含消费金额、商户和支付页面文字。手机号与银行卡号默认遮罩，其他个人信息仍可能出现，导出后可先自行查看。",
                fontSize = 11.sp, lineHeight = 17.sp, color = c.ink)
        }
        Spacer(Modifier.height(18.dp))
        EcoPrimaryButton(if (busy) "正在整理日志…" else "导出并分享诊断包", { generate(true) },
            enabled = !busy && state.summary.count > 0, leading = EcoIcons.Download)
        Spacer(Modifier.height(8.dp))
        EcoPrimaryButton("保存到文件", { generate(false) }, enabled = !busy && state.summary.count > 0)
        SettingsNote("生成文件后可保存或分享，日志不会自动上传。关闭诊断后仍可导出已有记录。")
    }
}

private fun diagnosticTime(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
private fun diagnosticDate(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM月dd日"))
private fun diagnosticSize(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
