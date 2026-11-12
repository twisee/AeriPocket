package com.controleco.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.db.MerchantRuleEntity
import com.controleco.app.data.db.TxType
import com.controleco.app.ui.common.EcoIcons
import com.controleco.app.ui.common.SettingsDivider
import com.controleco.app.ui.common.SettingsLazyPage
import com.controleco.app.ui.common.SettingsLead
import com.controleco.app.ui.common.SettingsNote
import com.controleco.app.ui.common.SettingsPane
import com.controleco.app.ui.common.formatTimestamp
import com.controleco.app.ui.eco.EcoConfirm
import com.controleco.app.ui.eco.EcoSheet
import com.controleco.app.ui.eco.EcoField
import com.controleco.app.ui.eco.EcoChip
import com.controleco.app.ui.eco.EcoPrimaryButton
import com.controleco.app.ui.eco.EcoTextAction
import com.controleco.app.ui.eco.EcoToast
import com.controleco.app.data.db.CategoryEntity
import com.controleco.app.data.db.RefundCategory
import com.controleco.app.ui.eco.EcoEmpty
import com.controleco.app.ui.eco.EcoPill
import com.controleco.app.ui.eco.EcoPillTone
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco

/**
 * 设置 → 商户归类：学习到的「商户 → 分类/账户」规则（确认或编辑捕获交易时自动学习）。
 *
 * 滚动结构：整页只有 [SettingsLazyPage] 里的一个 LazyColumn。旧实现把
 * `LazyColumn(Modifier.fillMaxSize())` 嵌在 `SettingsPage` 的 `verticalScroll` 里，
 * 规则非空时子列表拿到无限高度约束会直接崩；这里把 LazyColumn 提升为页面滚动容器，
 * 规则行放在同一个 item 的非滚动 Column 里，两者不会再嵌套滚动。
 */
@Composable
fun SettingsMerchantRulesScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val rules by vm.merchantRules.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var clearAsk by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MerchantRuleEntity?>(null) }

    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }
    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }
    // 规则本身已是「最近在前」，这里再按更新时间兜底，避免 DAO 排序变化打乱展示
    val sorted = remember(rules) { rules.sortedByDescending { it.updatedAt } }

    SettingsLazyPage(micro = "MERCHANT RULES", title = "商户归类", onBack = onBack) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${sorted.size} 条商户规则",
                    style = EcoType.SectionAside,
                    color = MaterialTheme.eco.muted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                EcoTextAction("新增规则", onClick = { editing = null; editorOpen = true })
                if (sorted.isNotEmpty()) {
                    Text(
                        "清空全部（${sorted.size}）",
                        style = EcoType.Pill.copy(fontWeight = FontWeight.W800),
                        color = MaterialTheme.eco.clay,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { clearAsk = true }
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                    )
                }
            }
        }
        item {
            if (sorted.isEmpty()) {
                SettingsPane {
                    EcoEmpty(
                        title = "还没有商户规则",
                        body = "点新增规则，为常用商户指定分类",
                        icon = EcoIcons.Tag,
                    )
                }
            } else {
                SettingsPane {
                    sorted.forEachIndexed { index, rule ->
                        if (index > 0) SettingsDivider()
                        MerchantRuleRow(
                            rule = rule,
                            categoryName = categoryNames[rule.categoryId] ?: "已删除分类",
                            accountName = rule.accountId?.let { accountNames[it] ?: "已删除账户" },
                            onEdit = { editing = rule; editorOpen = true },
                            onDelete = { vm.deleteMerchantRule(rule.id) },
                        )
                    }
                }
            }
        }
        item {
        }
    }

    if (editorOpen) {
        MerchantRuleEditor(editing, categories, onDismiss = { editorOpen = false }) { merchant, type, category, done ->
            vm.saveMerchantRule(editing?.id ?: 0, merchant, type, category) { error, changed ->
                done(error)
                if (error == null) {
                    editorOpen = false
                    EcoToast.show("已保存，更新 $changed 笔旧账")
                }
            }
        }
    }

    if (clearAsk) {
        EcoConfirm(
            title = "清空商户归类",
            body = "只清除学习到的规则，不影响已入账的账单。之后确认账单会重新开始学习。",
            confirmText = "清空",
            onConfirm = {
                vm.clearMerchantRules()
                clearAsk = false
            },
            onDismiss = { clearAsk = false },
            danger = true,
        )
    }
}

/** 单条规则：商户关键字 + 方向 pill / → 分类 · 账户 · 命中次数 / 更新时间 / 删除 */
@Composable
private fun MerchantRuleRow(
    rule: MerchantRuleEntity,
    categoryName: String,
    accountName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = MaterialTheme.eco
    val income = rule.type == TxType.INCOME
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.matchKey,
                    style = EcoType.RowTitle.copy(fontSize = 10.5.sp, fontWeight = FontWeight(740)),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                EcoPill(
                    text = if (income) "收入" else "支出",
                    tone = if (income) EcoPillTone.Good else EcoPillTone.Neutral,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildAnnotatedString {
                    append("→ ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.W700)) {
                        append(categoryName)
                    }
                    if (accountName != null) {
                        append(" / ")
                        append(accountName)
                    }
                    append(" / 命中 ${rule.hits} 次")
                },
                style = EcoType.RowSub.copy(fontSize = 8.5.sp),
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatTimestamp(rule.updatedAt),
                style = EcoType.PageNote.copy(fontSize = 8.sp),
                color = c.quiet,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        EcoTextAction("修改", onClick = onEdit)
        Text(
            "删除",
            style = EcoType.RowTrail.copy(fontSize = 9.5.sp, fontWeight = FontWeight.W700),
            color = c.clay,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantRuleEditor(
    old: MerchantRuleEntity?, categories: List<CategoryEntity>, onDismiss: () -> Unit,
    onSave: (String, TxType, Long, (String?) -> Unit) -> Unit,
) {
    val c=MaterialTheme.eco
    var merchant by remember { mutableStateOf(old?.matchKey.orEmpty()) }
    var type by remember { mutableStateOf(old?.type ?: TxType.EXPENSE) }
    var category by remember { mutableStateOf(old?.categoryId) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val choices=categories.filter { it.type==type && it.name!=RefundCategory.NAME }
    LaunchedEffect(type,choices) { if(choices.none { it.id==category }) category=choices.firstOrNull()?.id }
    EcoSheet(onDismiss=onDismiss,title=if(old==null) "新增商户规则" else "修改商户规则",modifier=Modifier.imePadding()) {
        EcoField(label="商户名称",value=merchant,onValueChange={ merchant=it;error=null },placeholder="例如：江西理工大学")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            EcoChip(text="支出",selected=type==TxType.EXPENSE,onClick={ type=TxType.EXPENSE })
            EcoChip(text="收入",selected=type==TxType.INCOME,onClick={ type=TxType.INCOME })
        }
        Spacer(Modifier.height(14.dp))
        Text("默认分类",style=EcoType.FieldLabel,color=c.muted)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            choices.forEach { item -> EcoChip(text=item.name,selected=category==item.id,onClick={ category=item.id }) }
        }
        Spacer(Modifier.height(14.dp))
        Text("保存后，同商户已有账单会一起更新；之后导入或自动识别也沿用这个分类。",style=EcoType.PageNote,color=c.muted)
        error?.let { Text(it,style=EcoType.PageNote,color=c.clay) }
        Spacer(Modifier.height(16.dp))
        EcoPrimaryButton(text=if(saving) "保存中…" else "保存规则",enabled=!saving && merchant.isNotBlank() && category!=null,onClick={
            saving=true
            onSave(merchant,type,requireNotNull(category)) { result -> saving=false;error=result }
        })
        Spacer(Modifier.height(18.dp))
    }
}
