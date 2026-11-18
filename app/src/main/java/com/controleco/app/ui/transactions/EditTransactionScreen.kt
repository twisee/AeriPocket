package com.controleco.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Checkbox
import com.controleco.app.ui.eco.EcoFilterStrip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.budget.BudgetShare
import com.controleco.app.data.db.TransactionEntity
import com.controleco.app.data.db.CategoryRules
import com.controleco.app.data.db.RefundMark
import com.controleco.app.data.db.TxType
import com.controleco.app.ui.eco.EcoConfirm
import com.controleco.app.ui.eco.EcoField
import com.controleco.app.ui.eco.EcoGhostButton
import com.controleco.app.ui.eco.EcoHairline
import com.controleco.app.ui.eco.EcoPane
import com.controleco.app.ui.eco.EcoPillTone
import com.controleco.app.ui.eco.EcoPrimaryButton
import com.controleco.app.ui.eco.EcoTextAction
import com.controleco.app.ui.eco.EcoToast
import com.controleco.app.ui.eco.EcoTopBar
import com.controleco.app.ui.eco.ecoPressable
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 编辑记录：金额卡（EcoPane + EditAmount）→ 类型 → 分类 → 账户与日期 → 商户与备注 →
 * 保存修改 → 删除这笔记录（与顶栏「删除」冗余但等价）。
 *
 * 编辑已有记录时保留 id / createdAt / 原时分（只改日期不会把时间抹成 00:00）。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(vm: LedgerViewModel, txId: Long, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val merchantRules by vm.merchantRules.collectAsStateWithLifecycle()
    val merchantHistory by vm.merchantHistory.collectAsStateWithLifecycle()
    var manualCategory by remember { mutableStateOf(false) }
    val allCategories by vm.categories.collectAsStateWithLifecycle()

    var existing by remember { mutableStateOf<TransactionEntity?>(null) }
    val categories = allCategories.filter {
        if (existing?.refundMark == RefundMark.REFUND_INCOME) CategoryRules.isRefundIncome(it.type, it.name)
        else !CategoryRules.isRefundIncome(it.type, it.name)
    }
    LaunchedEffect(txId) {
        if (txId > 0) existing = vm.transactionById(txId)
    }

    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    // 编辑时保留原记录的时分（改日期不该把时间抹成 00:00）
    var timeOfDay by remember { mutableStateOf(LocalTime.now()) }
    var counterparty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // 预算口径（只对支出）：不计入预算 / AA 支出人数
    val vault by vm.experienceVault.collectAsStateWithLifecycle()
    var experienceBudget by remember { mutableStateOf(false) }
    var budgetExcluded by remember { mutableStateOf(false) }
    var aaEnabled by remember { mutableStateOf(false) }
    var aaSharesText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showRefundAmount by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var rememberMerchantCategory by remember { mutableStateOf(false) }

    // 编辑模式：加载完成后回填表单（仅一次）
    LaunchedEffect(existing) {
        existing?.let { t ->
            type = t.type
            amountText = Money.formatCents(t.amountCents)
            categoryId = t.categoryId
            manualCategory = false
            accountId = t.accountId
            val local = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault())
            date = local.toLocalDate()
            timeOfDay = local.toLocalTime()
            counterparty = t.counterparty
            note = t.note
            experienceBudget = t.experienceBudget
            budgetExcluded = t.budgetExcluded
            aaEnabled = t.aaShares >= BudgetShare.MIN_AA_SHARES
            aaSharesText = if (aaEnabled) t.aaShares.toString() else ""
        }
    }

    // 默认选中：第一个账户 + 当前类型的第一个分类
    LaunchedEffect(accounts, categories, type) {
        if (accountId == null) accountId = accounts.firstOrNull()?.id
        if (categories.firstOrNull { it.id == categoryId }?.type != type) {
            categoryId = categories.firstOrNull { it.type == type }?.id
        }
    }

    val catsOfType = categories.filter { it.type == type }
    val suggestions = com.controleco.app.data.MerchantEntrySupport.suggestions(counterparty, type, merchantRules, categories, merchantHistory)
    LaunchedEffect(counterparty, type, merchantRules, categories) {
        val matched = com.controleco.app.capture.MerchantRules.categoryIdFrom(merchantRules, categories, type, counterparty, "")
        // 打开旧账不把已有例外改回默认；用户输入另一个商户时才尝试沿用其规则。
        if(existing == null || counterparty != existing?.counterparty || type != existing?.type) {
            categoryId = com.controleco.app.data.MerchantEntrySupport.categoryForMerchant(manualCategory, matched, categoryId)
        }
    }
    val cents = Money.parseCents(amountText)
    val valid = cents != null && cents > 0L && categoryId != null && accountId != null && !saving
    val unchanged = existing?.let { record ->
        val local = Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault())
        type == record.type && cents == record.amountCents && categoryId == record.categoryId &&
            accountId == record.accountId && date == local.toLocalDate() && timeOfDay == local.toLocalTime() &&
            counterparty.trim() == record.counterparty.trim() && note.trim() == record.note.trim() &&
            budgetExcluded == record.budgetExcluded && experienceBudget == record.experienceBudget &&
            (if (aaEnabled) BudgetShare.parseShares(aaSharesText) else 0) == record.aaShares
    } == true
    val c = MaterialTheme.eco
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    fun save() {
        if (saving) return
        val value = Money.parseCents(amountText)
        // AA 人数只在「支出 + 勾选 AA」时才需要合法；其余情况一律按 0 落库
        val wantAa = type == TxType.EXPENSE && aaEnabled
        val shares = if (wantAa) BudgetShare.parseShares(aaSharesText) else 0
        when {
            value == null || value <= 0L -> error = "请输入有效金额"
            categoryId == null -> error = "请选择分类"
            accountId == null -> error = "请选择账户"
            wantAa && shares == null -> error = "AA 人数需在 2 到 99 之间"
            else -> {
                val ts = LocalDateTime.of(date, timeOfDay)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                saving = true
                vm.saveTransaction(
                    txId, type, value, categoryId!!, accountId!!, ts, note.trim(), counterparty.trim(),
                    budgetExcluded = budgetExcluded,
                    experienceBudget = experienceBudget,
                    aaShares = shares ?: 0,
                    rememberMerchantCategory = rememberMerchantCategory,
                    onResult = { message ->
                        saving = false
                        if (message == null) {
                            EcoToast.show(if (txId > 0) "已保存修改" else "已记一笔")
                            onBack()
                        } else error = message
                    },
                )
            }
        }
    }

    fun refund() {
        if(saving) return
        if(!unchanged) { EcoToast.show("请先保存修改，再操作退款"); return }
        showRefundAmount = true
    }

    fun undoRefund() {
        if (saving) return
        if (!unchanged) { EcoToast.show("请先保存修改，再操作退款"); return }
        val refunded = true
        saving = true
        val done: (String?) -> Unit = { message ->
            saving = false
            if(message == null) { EcoToast.show(if(refunded) "已撤销退款" else "已退款"); onBack() } else error = message
        }
        vm.undoRefund(txId, done)
    }

    if(txId > 0 && existing == null) {
        Column(Modifier.fillMaxSize()) {
            EcoTopBar(micro = "EDIT ENTRY", title = "编辑记录", onBack = onBack)
            Text("正在读取账单…", color = c.muted, modifier = Modifier.padding(22.dp))
        }
        return
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        EcoTopBar(micro = "EDIT ENTRY", title = "编辑记录", onBack = onBack, action = {
            if(txId > 0 && existing != null) {
                if(existing?.type == TxType.EXPENSE && type == TxType.EXPENSE) {
                    if ((existing?.refundedCents ?: 0L) < (existing?.amountCents ?: 0L)) {
                        EcoTextAction(if ((existing?.refundedCents ?: 0L) > 0L) "继续退款" else "退款", onClick = ::refund, enabled = !saving)
                        Spacer(Modifier.width(8.dp))
                    }
                    if ((existing?.refundedCents ?: 0L) > 0L) {
                        EcoTextAction("撤销退款", onClick = ::undoRefund, enabled = !saving)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                EcoTextAction("删除", onClick = { showDeleteConfirm = true }, color = c.clay, enabled = !saving)
            }
        })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if(existing?.refundMark != null && existing?.refundMark != RefundMark.NONE) {
                    Text(if(type == TxType.EXPENSE) "支出" else "收入", color = c.ink)
                } else EcoFilterStrip(options = listOf("支出", "收入"), selectedIndex = if(type == TxType.EXPENSE) 0 else 1,
                    onSelect = { type = if(it == 0) TxType.EXPENSE else TxType.INCOME; manualCategory = false; rememberMerchantCategory = false
                        if(type == TxType.INCOME) { budgetExcluded = false; experienceBudget = false; aaEnabled = false } })
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(Money.symbol, fontSize = 23.sp, color = c.muted, modifier = Modifier.padding(bottom = 7.dp, end = 5.dp))
                BasicTextField(value = amountText, onValueChange = { amountText = it; error = null }, singleLine = true,
                    textStyle = EcoType.EditAmount.copy(fontSize = 48.sp, color = c.ink, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(c.blue),
                    modifier = Modifier.width(240.dp))
            }
            Spacer(Modifier.height(18.dp))
            EntryDateTime(date, timeOfDay, { date = it }, { timeOfDay = it })
            Spacer(Modifier.height(18.dp))
            EntryCategories(catsOfType, categoryId) { categoryId = it; manualCategory = true; rememberMerchantCategory = false }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EntryMerchant(counterparty, { counterparty = it; rememberMerchantCategory = false }, suggestions, { suggestion ->
                    counterparty = suggestion.name
                    rememberMerchantCategory = false
                    categoryId = com.controleco.app.data.MerchantEntrySupport.categoryForMerchant(manualCategory, suggestion.categoryId, categoryId)
                }, Modifier.weight(1f))
                EcoField("备注", note, { note = it }, Modifier.weight(1f), placeholder = "可不填", minHeight = 36.dp)
            }
            if(counterparty.isNotBlank() && existing?.refundMark != RefundMark.REFUND_INCOME) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberMerchantCategory, { rememberMerchantCategory = it }, modifier = Modifier.size(31.dp))
                    Text("设为该商户的默认分类", fontSize = 11.sp, color = c.muted)
                }
            }
            if(type == TxType.EXPENSE) {
                Spacer(Modifier.height(14.dp))
                Text("预算选项", fontSize = 12.sp, color = c.muted)
                Spacer(Modifier.height(8.dp))
                EcoPane(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = 3.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)) {
                    TxBudgetOptions(budgetExcluded, { budgetExcluded = it }, aaEnabled, {
                        aaEnabled = it
                        if(it && BudgetShare.parseShares(aaSharesText) == null) aaSharesText = BudgetShare.DEFAULT_AA_SHARES.toString()
                    }, aaSharesText, { aaSharesText = it }, compact = true,
                        experienceAvailable = vault.enabled, experienceBudget = experienceBudget, onExperienceBudgetChange = { experienceBudget = it })
                }
            }
            existing?.let { record ->
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("来源", fontSize = 10.sp, color = c.quiet)
                    Spacer(Modifier.weight(1f))
                    Text(when(record.source) { com.controleco.app.data.db.TxSource.MANUAL -> "手动记账"
                        com.controleco.app.data.db.TxSource.WECHAT -> "微信"
                        com.controleco.app.data.db.TxSource.ALIPAY -> "支付宝"
                        com.controleco.app.data.db.TxSource.BANK -> "银行"
                        else -> "自动记账" }, fontSize = 10.sp, color = c.muted)
                }
            }
            error?.let { Text(it, color = c.clay, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(12.dp))
        }
        EcoPrimaryButton(if(txId > 0) "保存修改" else "保存", onClick = ::save, enabled = valid,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    if (showRefundAmount && existing != null) {
        com.controleco.app.ui.refund.RefundAmountSheet(
            remaining = existing!!.amountCents - existing!!.refundedCents,
            onDismiss = { showRefundAmount = false }, onRefund = { amount ->
                showRefundAmount = false; saving = true
                vm.refundExpense(txId, { message ->
                    saving = false
                    if (message == null) { EcoToast.show("已登记退款"); onBack() } else error = message
                }, amountCents = amount)
            })
    }

    if (showDeleteConfirm && existing != null) {
        EcoConfirm(
            title = "删除记录",
            body = "确定删除这笔交易吗？删除后不可恢复。",
            confirmText = "删除",
            danger = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                existing?.let { record ->
                    vm.deleteTransaction(record.id) { err ->
                        if (err == null) {
                            EcoToast.show("已删除", EcoPillTone.Bad)
                            onBack()
                        } else {
                            error = err
                        }
                    }
                }
            },
        )
    }
}
