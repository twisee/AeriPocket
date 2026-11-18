package com.controleco.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.ui.common.*
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money

@Composable
internal fun ExperienceVaultSettings(vm: LedgerViewModel) {
    val vault by vm.experienceVault.collectAsStateWithLifecycle()
    val balance by vm.experienceAvailableCents.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    SettingsSection(title = "体验小金库", aside = "EXPERIENCE")
    SettingsPane {
        SettingsSwitchRow(title = "开启体验小金库", checked = vault.enabled,
            subtitle = "从月总预算划入固定额度，未用完自动累积",
            onCheckedChange = { enabled ->
                if (enabled) edit = true
                else vm.configureExperienceVault(false, vault.nextMonthlyCents ?: vault.monthlyCents)?.let { EcoToast.show(it) }
            })
        if (vault.enabled || vault.grants.isNotEmpty()) {
            SettingsDivider()
            SettingsRow(title = "每月体验额度", trail = Money.formatYuan(vault.nextMonthlyCents ?: vault.monthlyCents),
                subtitle = if (vault.nextMonthlyCents != null) "新额度从下一预算周期生效" else "修改额度从下一预算周期生效",
                onClick = { edit = true }, showChevron = true)
            SettingsDivider()
            SettingsRow(title = "清除累计额度", subtitle = "当前可用 ${Money.formatYuan(balance)} · 保留历史流水",
                trail = if (clearing) "处理中" else "清零", onClick = { if (!clearing) clear = true })
        }
    }
    if (edit) {
        var amount by remember { mutableStateOf(Money.formatCents(vault.nextMonthlyCents ?: vault.monthlyCents)) }
        var error by remember { mutableStateOf<String?>(null) }
        EcoSheet(title = "每月体验额度", onDismiss = { edit = false }) {
            EcoMoneyField(label = "从月总预算中划入", value = amount, onValueChange = { amount = it; error = null },
                supporting = error, isError = error != null)
            Spacer(Modifier.height(12.dp))
            Text("沿用每月首日。开启时本期划入一次；修改从下期生效；关闭期间暂停划入。退款返还体验额度，额度不足仍可记账。",
                style = EcoType.PageNote, color = MaterialTheme.eco.muted)
            Spacer(Modifier.height(18.dp))
            EcoPrimaryButton(text = "保存", onClick = {
                val cents = Money.parseCents(amount)
                if (cents == null || cents <= 0) error = "请输入大于零的金额"
                else {
                    error = vm.configureExperienceVault(true, cents)
                    if (error == null) edit = false
                }
            })
        }
    }
    if (clear) EcoConfirm(title = "清零体验额度？", body = "将当前可用额度归零，保留历史流水与本月新增、已用记录。之后每期继续划入。",
        confirmText = "清零", onConfirm = {
            clear = false; clearing = true
            vm.clearExperienceVault { error -> clearing = false; EcoToast.show(error ?: "体验额度已清零") }
        }, onDismiss = { clear = false })
}
