package com.controleco.app.ui.refund

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.PendingStatus
import com.controleco.app.data.db.RefundMark
import com.controleco.app.data.db.TransactionRow
import com.controleco.app.ui.common.EcoIcons
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 捕获退款与历史待处理退款共用候选页；选择后持久保存原支出关联。 */
@Composable
fun RefundLinkScreen(
    vm: LedgerViewModel,
    pendingId: Long? = null,
    legacyRefundId: Long? = null,
    onBack: () -> Unit,
) {
    var rows by remember { mutableStateOf<List<TransactionRow>>(emptyList()) }
    var amount by remember { mutableStateOf<Long?>(null) }
    var refundText by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<TransactionRow?>(null) }
    var loading by remember { mutableStateOf(true) }
    var available by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var merchant by remember { mutableStateOf("") }
    var minAmount by remember { mutableStateOf("") }
    var maxAmount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    val c = MaterialTheme.eco

    LaunchedEffect(pendingId, legacyRefundId) {
        loading = true
        try {
            if (pendingId != null) {
                val pending = vm.pendingById(pendingId)
                available = pending?.refundMark == RefundMark.REFUND_INCOME &&
                    pending.status == PendingStatus.PENDING
                amount = pending?.amountCents?.takeIf { it > 0 }
            } else if (legacyRefundId != null) {
                val refund = vm.transactionById(legacyRefundId)
                available = refund?.refundPending == true && refund.refundMark == RefundMark.REFUND_INCOME
                amount = refund?.amountCents?.takeIf { it > 0 }
            }
            refundText = amount?.let { Money.formatCents(it) }.orEmpty()
            if (available) rows = vm.refundCandidates(null, allowSnapshot = legacyRefundId != null)
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            error = "加载失败"
        } finally {
            loading = false
        }
    }
    val chosenAmount = if (legacyRefundId != null) amount else Money.parseCents(refundText)?.takeIf { it > 0 }
    val visible = remember(rows, chosenAmount, merchant, minAmount, maxAmount, startDate, endDate) {
        filterRefundCandidates(rows, chosenAmount, merchant, minAmount, maxAmount, startDate, endDate, allowSnapshot = legacyRefundId != null)
    }
    fun submit(row: TransactionRow, refundCents: Long) {
        if (submitting || !available) return
        submitting = true
        error = null
        val done: (String?) -> Unit = { result ->
            submitting = false
            if (result == null) {
                EcoToast.show("已退款")
                onBack()
            } else {
                error = result
            }
        }
        if (pendingId != null) vm.refundCaptured(pendingId, row.id, done, refundCents)
        else if (legacyRefundId != null) vm.handlePendingRefund(legacyRefundId, row.id, done)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        EcoTopBar(micro = "REFUND", title = "选择被退款的账单", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (available) {
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (legacyRefundId != null) {
                            Text("退款 ${Money.formatYuan(amount ?: 0)}", style = EcoType.BillAmount, color = c.jade)
                        } else {
                            EcoMoneyField("退款金额", refundText, { refundText = it }, placeholder = "选择原账单后填写")
                        }
                        run {
                            EcoField(label = "商户", value = merchant, onValueChange = { merchant = it }, placeholder = "搜索商户")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EcoMoneyField("最低金额", minAmount, { minAmount = it }, Modifier.weight(1f), placeholder = "不限")
                                EcoMoneyField("最高金额", maxAmount, { maxAmount = it }, Modifier.weight(1f), placeholder = "不限")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RefundDateField("开始日期", startDate, { startDate = it }, Modifier.weight(1f))
                            RefundDateField("结束日期", endDate, { endDate = it }, Modifier.weight(1f))
                        }
                        if (startDate != null || endDate != null || merchant.isNotEmpty() || minAmount.isNotEmpty() || maxAmount.isNotEmpty()) {
                            EcoTextAction("重置", onClick = {
                                startDate = null; endDate = null; merchant = ""; minAmount = ""; maxAmount = ""
                            })
                        }
                    }
                }
            }
            error?.let { message -> item(key = "error") { Text(message, style = EcoType.PageNote, color = c.clay) } }
            when {
                loading -> item { EcoEmpty(title = "加载中…", icon = EcoIcons.Receipt) }
                !available -> item { EcoEmpty(title = if (error == null) "退款已处理" else "暂时无法加载", icon = EcoIcons.Check) }
                visible.isEmpty() -> item { EcoEmpty(title = "暂无可选账单", icon = EcoIcons.Receipt) }
                else -> items(visible, key = { it.id }) { row ->
                    EcoPane { RefundRow(row, onClick = { if (!submitting) picked = row }) }
                }
            }
        }
    }
    picked?.let { row ->
        if (legacyRefundId != null) {
            EcoConfirm(title = "关联退款？", body = "将退款关联到这笔支出。", confirmText = "确认", onConfirm = {
                picked = null; submit(row, amount ?: 0)
            }, onDismiss = { picked = null })
        } else RefundAmountSheet(remaining = row.amountCents - row.refundedCents,
            initial = chosenAmount ?: (row.amountCents - row.refundedCents),
            onRefund = { cents -> picked = null; submit(row, cents) }, onDismiss = { picked = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefundDateField(label: String, value: LocalDate?, onPick: (LocalDate) -> Unit, modifier: Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier) {
        EcoGhostButton(text = value?.toString() ?: label, onClick = { showPicker = true }, height = 42.dp)
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun RefundRow(
    row: TransactionRow,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.eco
    val merchant = row.counterparty.ifBlank { row.note }.ifBlank { "未命名支出" }
    val detail = listOf(row.categoryName, refundDate(row.timestamp))
        .filter { it.isNotBlank() }
        .joinToString(" ")

    Row(
        Modifier
            .fillMaxWidth()
            .ecoPressable(scaleTo = 0.99f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EcoCategoryTile(
            categoryName = row.categoryName,
            icon = row.categoryIcon,
            size = 38.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "-" + Money.formatYuan(row.amountCents),
                    style = EcoType.BillAmount,
                    color = c.ink,
                    maxLines = 1,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "$merchant",
                    style = EcoType.RowTitle,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (detail.isNotBlank()) {
                Spacer(Modifier.size(2.dp))
                Text(
                    detail,
                    style = EcoType.RowSub,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(EcoIcons.Chev, contentDescription = null, tint = c.quiet, modifier = Modifier.size(14.dp))
    }
}

private val refundDateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault())
private fun refundDate(millis: Long): String = refundDateFormatter.format(Instant.ofEpochMilli(millis))
