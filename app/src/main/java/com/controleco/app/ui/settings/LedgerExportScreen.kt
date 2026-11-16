package com.controleco.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleco.app.csv.LedgerCsvExporter
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.ui.common.*
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.theme.eco
import com.controleco.app.ui.transactions.TxDateField
import kotlinx.coroutines.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun LedgerExportScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val c = MaterialTheme.eco
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by rememberSaveable { mutableStateOf(true) }
    var fromText by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var toText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val from = LocalDate.parse(fromText)
    val to = LocalDate.parse(toText)
    var count by remember { mutableStateOf<Int?>(null) }
    var range by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var path by rememberSaveable { mutableStateOf<String?>(null) }
    val valid = all || from <= to
    fun bounds(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        return if(all) Long.MIN_VALUE to Long.MAX_VALUE else
            from.atStartOfDay(zone).toInstant().toEpochMilli() to to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    LaunchedEffect(all, fromText, toText) {
        count = null
        if(valid) {
            val (start, end) = bounds()
            val snapshot = vm.csvExportSnapshot(start, end)
            count = snapshot.transactions.size
            val dates = snapshot.transactions.map { it.timestamp }
            range = if(dates.isEmpty()) "暂无账单" else {
                val zone = ZoneId.systemDefault()
                "${Instant.ofEpochMilli(dates.min()).atZone(zone).toLocalDate()} — ${Instant.ofEpochMilli(dates.max()).atZone(zone).toLocalDate()}"
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val selectedPath = path
        if(uri == null) { busy = false; path = null }
        else if(selectedPath != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = File(selectedPath)
                    check(file.canonicalFile.parentFile == File(context.cacheDir, "ledger-exports").canonicalFile)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                        ?: error("无法打开保存位置")
                }
                EcoToast.show("CSV账单已保存")
            } catch (_: Exception) { EcoToast.show("保存失败，请重新导出") }
            finally { busy = false; path = null }
        }
    }
    SettingsPage(micro = "EXPORT", title = "导出账单", onBack = onBack) {
        SettingsLead("将账单保存为 CSV 文件，用于表格查看或导入其他记账软件。")
        SettingsSection("导出范围", aside = "RANGE")
        EcoSegmented(listOf("全部账单", "指定时间段"), if(all) 0 else 1, { all = it == 0 }, Modifier.fillMaxWidth(), height = 40.dp, enabled = !busy)
        if(!all) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TxDateField(from, { fromText = it.toString() }, Modifier.weight(1f), label = "开始日期")
                TxDateField(to, { toText = it.toString() }, Modifier.weight(1f), label = "结束日期")
            }
        }
        if(!valid) SettingsNote("结束日期不能早于开始日期")
        Spacer(Modifier.height(16.dp))
        SettingsPane {
            SettingsValueRow("预计导出", if(count == null) "统计中…" else "$count 笔")
            SettingsDivider()
            SettingsValueRow("账单时间", range)
        }
        SettingsSection("文件内容", aside = "CONTENTS")
        SettingsPane {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("日期时间、收支、分类、金额（原金额）", "商户、备注、来源", "已退款金额、实付金额、退款状态", "AA 人数、是否计入预算").forEach {
                    Text(it, fontSize = 12.sp, color = c.ink)
                }
            }
        }
        SettingsNote("不同软件的列名和退款规则可能不同，导入时可按对方模板映射字段。退款在原账单列明，不重复导出为收入。")
        Spacer(Modifier.height(24.dp))
        EcoPrimaryButton(if(busy) "正在导出…" else "导出并保存 CSV", leading = EcoIcons.Download,
            enabled = valid && !busy && (count ?: 0) > 0, onClick = {
                busy = true
                scope.launch {
                    try {
                        val (start, end) = bounds()
                        val snapshot = vm.csvExportSnapshot(start, end)
                        val file = withContext(Dispatchers.IO) {
                            val dir = File(context.cacheDir, "ledger-exports").apply { mkdirs() }
                            dir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("AeriPocket-账单-") }
                                .sortedByDescending { it.lastModified() }.drop(1).forEach { it.delete() }
                            File(dir, "AeriPocket-账单-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}-${System.currentTimeMillis()}.csv")
                                .also { out -> out.outputStream().bufferedWriter(Charsets.UTF_8).use { LedgerCsvExporter.write(snapshot, it) } }
                        }
                        path = file.absolutePath
                        save.launch(file.name)
                    } catch (_: Exception) { busy = false; EcoToast.show("导出失败，请检查存储空间后重试") }
                }
            })
    }
}
