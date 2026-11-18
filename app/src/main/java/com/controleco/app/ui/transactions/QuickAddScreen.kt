package com.controleco.app.ui.transactions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.budget.BudgetShare
import com.controleco.app.data.db.CategoryEntity
import com.controleco.app.data.db.CategoryRules
import com.controleco.app.data.db.TxType
import com.controleco.app.ui.common.EcoIcons
import com.controleco.app.ui.eco.EcoCategoryTile
import com.controleco.app.ui.eco.EcoField
import com.controleco.app.ui.eco.EcoFilterStrip
import com.controleco.app.ui.eco.EcoHairline
import com.controleco.app.ui.eco.EcoPrimaryButton
import com.controleco.app.ui.eco.EcoToast
import com.controleco.app.ui.eco.EcoTopBar
import com.controleco.app.ui.eco.ecoPressable
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 快速记账：大字金额与算式 + 三步引导 + 收支分段 + 分类折叠条 + 计算键盘 + 记一笔。
 *
 * 版式照设计稿：内容区（金额/分段/分类/补充信息）可滚动，键盘与主按钮钉在底部；
 * 系统输入法弹出时（写商户/备注）只收起数字键盘、把高度让给表单，主按钮始终可达，
 * 点击金额清除表单焦点并收起输入法，恢复计算键盘；关闭输入法后也自动恢复。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val merchantRules by vm.merchantRules.collectAsStateWithLifecycle()
    val merchantHistory by vm.merchantHistory.collectAsStateWithLifecycle()
    val allCategories by vm.categories.collectAsStateWithLifecycle()
    val categories = allCategories.filterNot { CategoryRules.isRefundIncome(it.type, it.name) }

    var calculator by rememberSaveable(stateSaver = listSaver<AmountCalculator, String>(
        save = { listOf(it.input, it.completedExpression.orEmpty(), it.isResult.toString()) },
        restore = { AmountCalculator(it[0], it[1].ifEmpty { null }, it[2].toBoolean()) },
    )) { mutableStateOf(AmountCalculator()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var timeOfDay by remember { mutableStateOf(LocalTime.now()) }
    var manualCategory by remember { mutableStateOf(false) }
    var rememberMerchantCategory by remember { mutableStateOf(false) }
    var counterparty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // 预算口径（只对支出）：不计入预算 / AA 支出人数
    val vault by vm.experienceVault.collectAsStateWithLifecycle()
    var experienceBudget by remember { mutableStateOf(false) }
    var budgetExcluded by remember { mutableStateOf(false) }
    var aaEnabled by remember { mutableStateOf(false) }
    var aaSharesText by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // 默认选中：第一个账户 + 当前类型的第一个分类
    LaunchedEffect(accounts, categories, type) {
        if (accountId == null) accountId = accounts.firstOrNull()?.id
        if (categories.firstOrNull { it.id == categoryId }?.type != type) {
            categoryId = categories.firstOrNull { it.type == type }?.id
        }
    }

    fun pressKey(key: String) {
        val edit = calculator.press(key)
        calculator = edit.state
        error = edit.error
    }

    val calculation = calculator.result
    val cents = calculation.cents
    val hasAmount = cents != null && cents > 0L
    val valid = calculator.input.isNotEmpty() && categoryId != null && accountId != null && !saving
    val catsOfType = categories.filter { it.type == type }
    val suggestions = com.controleco.app.data.MerchantEntrySupport.suggestions(counterparty, type, merchantRules, categories, merchantHistory)
    LaunchedEffect(counterparty, type, merchantRules, categories) {
        val matched = com.controleco.app.capture.MerchantRules.categoryIdFrom(merchantRules, categories, type, counterparty, "")
        categoryId = com.controleco.app.data.MerchantEntrySupport.categoryForMerchant(manualCategory, matched, categoryId)
    }


    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val contentScroll = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun save() {
        if (saving) return
        val amount = calculation.cents
        // AA 人数只在「支出 + 勾选 AA」时才需要合法；其余情况一律按 0 落库
        val wantAa = type == TxType.EXPENSE && aaEnabled
        val shares = if (wantAa) BudgetShare.parseShares(aaSharesText) else 0
        when {
            amount == null -> error = calculation.error ?: "请输入有效金额"
            amount <= 0L -> error = "金额须大于零"
            categoryId == null -> error = "请选择分类"
            accountId == null -> error = "请选择账户"
            wantAa && shares == null -> error = "AA 人数需在 2 到 99 之间"
            else -> {
                val zone = ZoneId.systemDefault()
                val ts = date.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
                saving = true
                vm.saveTransaction(
                    -1L, type, amount, categoryId!!, accountId!!, ts, note.trim(), counterparty.trim(),
                    budgetExcluded = budgetExcluded,
                    experienceBudget = experienceBudget,
                    aaShares = shares ?: 0,
                    rememberMerchantCategory = rememberMerchantCategory,
                    onResult = { message ->
                        saving = false
                        if (message == null) {
                            EcoToast.show("已记一笔")
                            onBack()
                        } else error = message
                    },
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        EcoTopBar(micro = "QUICK ENTRY", title = "记一笔", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(contentScroll)
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                EcoFilterStrip(options = listOf("支出", "收入"), selectedIndex = if(type == TxType.EXPENSE) 0 else 1,
                    onSelect = { type = if(it == 0) TxType.EXPENSE else TxType.INCOME; manualCategory = false; rememberMerchantCategory = false })
            }
            Spacer(Modifier.height(14.dp))
            AmountHero(calculator.displayAmount, calculator.formula, hasAmount) { focusManager.clearFocus(force = true); keyboard?.hide() }
            if(error != null) Text(error!!, style = TxErrorStyle, color = MaterialTheme.eco.clay)
            Spacer(Modifier.height(16.dp))
            EntryDateTime(date, timeOfDay, { date = it }, { timeOfDay = it })
            Spacer(Modifier.height(16.dp))
            EntryCategories(catsOfType, categoryId) { categoryId = it; manualCategory = true; rememberMerchantCategory = false }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EntryMerchant(counterparty, { counterparty = it; rememberMerchantCategory = false }, suggestions, { suggestion ->
                    counterparty = suggestion.name
                    rememberMerchantCategory = false
                    categoryId = com.controleco.app.data.MerchantEntrySupport.categoryForMerchant(manualCategory, suggestion.categoryId, categoryId)
                }, Modifier.weight(1f))
                EcoField(label = "备注", value = note, onValueChange = { note = it }, modifier = Modifier.weight(1f), placeholder = "可不填", minHeight = 36.dp)
            }
            if (counterparty.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = rememberMerchantCategory, onCheckedChange = { rememberMerchantCategory = it }, modifier = Modifier.size(31.dp))
                    Text("设为该商户的默认分类", fontSize = 11.sp, color = MaterialTheme.eco.muted)
                }
            }
            DetailsToggle(expanded = showMore, onToggle = { showMore = !showMore })
            if (showMore && type == TxType.EXPENSE) {
                TxBudgetOptions(budgetExcluded = budgetExcluded, onBudgetExcludedChange = { budgetExcluded = it },
                    aaEnabled = aaEnabled, onAaEnabledChange = {
                        aaEnabled = it
                        if(it && BudgetShare.parseShares(aaSharesText) == null) aaSharesText = BudgetShare.DEFAULT_AA_SHARES.toString()
                    }, sharesText = aaSharesText, onSharesTextChange = { aaSharesText = it }, compact = true,
                    experienceAvailable = vault.enabled, experienceBudget = experienceBudget, onExperienceBudgetChange = { experienceBudget = it })
            }
            Spacer(Modifier.height(16.dp))
        }

        // 输入法弹出时只收起数字键盘（表单需要高度），主按钮始终可达；
        // 关掉输入法后键盘自动回来，保证数字键盘永远不会被输入法盖住。
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (imeVisible) Modifier else Modifier.navigationBarsPadding()),
        ) {
            if (!imeVisible) {
                EcoHairline(color = MaterialTheme.eco.ruleStrong)
                Spacer(Modifier.height(4.dp))
                Text("金额计算", style = TxSectionLabelStyle, color = MaterialTheme.eco.blue,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                CalculatorKeypad(
                    onKey = ::pressKey,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            EcoPrimaryButton(
                text = "记一笔",
                onClick = ::save,
                modifier = Modifier.padding(horizontal = 22.dp),
                enabled = valid,
                leading = EcoIcons.Check,
                height = 46.dp,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 金额主体：AMOUNT / 金额 + ¥ + 自适应大数字 + 闪烁光标（背后一层极淡的玉色光斑） */
@Composable
private fun AmountHero(amountText: String, formula: String?, hasAmount: Boolean, onEdit: () -> Unit) {
    val c = MaterialTheme.eco
    Box(Modifier.fillMaxWidth().semantics { contentDescription = "修改金额" }.clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (formula != null) Text(formula, style = EcoType.PageNote, color = c.muted, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(
                    Money.symbol,
                    style = TxCurrencyStyle,
                    color = c.jade,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
                Spacer(Modifier.width(5.dp))
                FittedAmountText(
                    text = amountText.ifEmpty { "0" },
                    color = if (hasAmount) c.ink else c.muted.copy(alpha = 0.58f),
                )
                BlinkingCaret()
            }

        }
    }
}

/** 53/780 大字：超出可用宽度按 0.88 递减（与 EcoAutoMoney 同一套缩放策略，光标才能紧跟数字） */
@Composable
private fun FittedAmountText(text: String, color: Color) {
    val base: TextStyle = EcoType.KeyAmount
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxPx = with(density) { 236.dp.toPx() }
    val fitted = remember(text, maxPx) {
        var size = base.fontSize
        var guard = 0
        while (guard++ < 12) {
            val result = measurer.measure(
                text = AnnotatedString(text),
                style = base.copy(fontSize = size),
                maxLines = 1,
                softWrap = false,
            )
            if (result.size.width <= maxPx || size.value <= 24f) break
            size = (size.value * 0.88f).coerceAtLeast(24f).sp
        }
        size
    }
    Text(
        text = text,
        style = base.copy(fontSize = fitted),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "qa-caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "qa-caret-alpha",
    )
    Box(
        Modifier
            .padding(start = 3.dp, bottom = 8.dp)
            .width(1.5.dp)
            .height(31.dp)
            .background(MaterialTheme.eco.blue.copy(alpha = alpha)),
    )
}

/** 补充信息开关：edit 图标 + 文案 + 右侧 chevron（展开转 90°） */
@Composable
private fun DetailsToggle(expanded: Boolean, onToggle: () -> Unit) {
    val c = MaterialTheme.eco
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .ecoPressable(scaleTo = 0.99f, onClick = onToggle)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EcoIcons.Edit, contentDescription = null, tint = c.blue, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text("补充信息", style = TxToggleStyle, color = c.muted)
        Spacer(Modifier.weight(1f))
        Icon(
            EcoIcons.Chev,
            contentDescription = if (expanded) "收起补充信息" else "展开补充信息",
            tint = c.blue,
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
        )
    }
}
