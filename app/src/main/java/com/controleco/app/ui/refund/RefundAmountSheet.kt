package com.controleco.app.ui.refund

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money

@Composable
internal fun RefundAmountSheet(remaining: Long, initial: Long = remaining,
    onRefund: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(Money.formatCents(initial)) }
    var error by remember { mutableStateOf<String?>(null) }
    EcoSheet(title = "退款金额", onDismiss = onDismiss) {
        Text("剩余可退 ${Money.formatYuan(remaining)}，可填写部分或全部金额。",
            style = EcoType.PageNote, color = MaterialTheme.eco.muted)
        Spacer(Modifier.height(12.dp))
        EcoMoneyField(label = "本次退款", value = text, onValueChange = { text = it; error = null },
            supporting = error, isError = error != null)
        Spacer(Modifier.height(18.dp))
        EcoPrimaryButton(text = "确认退款", onClick = {
            val cents = Money.parseCents(text)
            when {
                cents == null || cents <= 0 -> error = "请输入大于零的退款金额"
                cents > remaining -> error = "退款金额超过剩余可退金额"
                else -> onRefund(cents)
            }
        })
    }
}
