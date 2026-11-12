package com.controleco.app.ui.common

import android.graphics.BitmapFactory
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleco.app.data.PeriodMode
import com.controleco.app.ui.eco.EcoAutoMoney
import com.controleco.app.ui.eco.EcoCountUpMoney
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.WeakHashMap

private object FloralArtworkCache {
    private val images = WeakHashMap<Context, ImageBitmap>()
    fun peek(context: Context) = synchronized(images) { images[context.applicationContext] }
    fun load(context: Context): ImageBitmap {
        peek(context)?.let { return it }
        val image = context.assets.open("characters/aerith/card_75_flowers.png").use {
            BitmapFactory.decodeStream(it).asImageBitmap()
        }
        return synchronized(images) { images.getOrPut(context.applicationContext) { image } }
    }
}

/** The approved floral artwork is already blurred behind the clear character. No bottom scrim. */
@Composable
internal fun AliceFloralBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = MaterialTheme.eco.isDark
    val artwork by produceState(FloralArtworkCache.peek(context), context) {
        value = withContext(Dispatchers.IO) { FloralArtworkCache.load(context) }
    }
    Box(modifier.background(MaterialTheme.eco.surfaceInset)) {
        artwork?.let { Image(it, "爱丽丝花卉卡片", Modifier.fillMaxSize().testTag("aliceFloralArtwork"),
            alignment = Alignment.CenterEnd, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High) }
        val wash = if (dark) Color(0xFF231821) else Color(0xFFFFF8FA)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to wash.copy(alpha = if (dark) .88f else .78f),
            .38f to wash.copy(alpha = if (dark) .57f else .51f),
            .75f to Color.Transparent, 1f to Color.Transparent)))
    }
}

@Composable
private fun floralTextStyle(size: Int): TextStyle {
    val dark = MaterialTheme.eco.isDark
    val density = LocalDensity.current
    return TextStyle(fontSize = size.sp, fontWeight = FontWeight.Medium,
        color = if (dark) Color(0xFFFFF4F6) else Color(0xFF39283B),
        shadow = Shadow(if (dark) Color.Black.copy(alpha = .75f) else Color.White.copy(alpha = .8f),
            Offset(0f, with(density) { 1.dp.toPx() }), with(density) { 2.dp.toPx() }))
}

@Composable
private fun FloralAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable (() -> Unit)? = null) {
    val c = MaterialTheme.eco
    Row(modifier.height(30.dp).clip(CircleShape)
        .background(if (c.isDark) Color(0xB0231821) else Color(0xAAFFF8FA))
        .clickable(onClick = onClick).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.invoke()
        Text(label, style = floralTextStyle(11), maxLines = 1)
    }
}

@Composable
internal fun AliceBalanceCard(mode: PeriodMode, balanceCents: Long, incomeCents: Long, expenseCents: Long,
    onModeChange: (PeriodMode) -> Unit, onOpenImageSettings: () -> Unit, modifier: Modifier, showEdit: Boolean) {
    val text = floralTextStyle(11)
    Box(modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp)).testTag("aliceBalanceCard")) {
        AliceFloralBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.height(29.dp).clip(CircleShape)
                    .background(if (MaterialTheme.eco.isDark) Color(0xB0231821) else Color(0xAAFFF8FA))) {
                    listOf("月" to PeriodMode.MONTH, "年" to PeriodMode.YEAR).forEach { (label, period) ->
                        Box(Modifier.fillMaxHeight().clip(CircleShape)
                            .background(if (mode == period) text.color.copy(alpha = .1f) else Color.Transparent)
                            .clickable { onModeChange(period) }.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = text)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(if (mode == PeriodMode.MONTH) "本月结余" else "本年结余", style = text, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (showEdit) Box(Modifier.size(29.dp).clip(CircleShape)
                    .background(if (MaterialTheme.eco.isDark) Color(0xB0231821) else Color(0xAAFFF8FA))
                    .clickable(onClick = onOpenImageSettings), contentAlignment = Alignment.Center) {
                    Icon(EcoIcons.Edit, "更换结余卡背景", tint = text.color, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            EcoAutoMoney(Money.formatYuanGroupedFixed(balanceCents), floralTextStyle(30), text.color,
                Modifier.fillMaxWidth(.64f), minSize = 16.sp, textAlign = TextAlign.Start)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(.74f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EcoAutoMoney("收入 ${Money.formatYuanGrouped(incomeCents)}", text, text.color, Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.Start)
                EcoAutoMoney("支出 ${Money.formatYuanGrouped(expenseCents)}", text, text.color, Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.Start)
            }
        }
    }
}

@Composable
internal fun AliceBudgetCard(budgetCents: Long, spentCents: Long, ratio: Float, currency: String,
    onCurrency: () -> Unit, onOpenAccount: () -> Unit, onManageBudget: () -> Unit) {
    val c = MaterialTheme.eco
    val text = floralTextStyle(11)
    Box(Modifier.fillMaxWidth().height(197.dp).clip(RoundedCornerShape(28.dp)).testTag("aliceBudgetCard")) {
        AliceFloralBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(17.dp)) {
            Text("本月预算", style = text)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(.50f), verticalAlignment = Alignment.CenterVertically) {
                if (budgetCents > 0) EcoCountUpMoney(budgetCents, floralTextStyle(28), text.color,
                    Modifier.weight(1f).clickable(onClick = onManageBudget), minSize = 16.sp, textAlign = TextAlign.Start)
                else Text("未设置", style = floralTextStyle(28), modifier = Modifier.weight(1f).clickable(onClick = onManageBudget))
                Box(Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onManageBudget), contentAlignment = Alignment.Center) {
                    Icon(EcoIcons.Edit, "设置本月预算", tint = text.color, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloralAction(currency, onCurrency)
                FloralAction("账户", onOpenAccount) { Icon(EcoIcons.Wallet, null, Modifier.size(13.dp), tint = text.color) }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(text.color.copy(alpha = .16f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0f, 1f)).background(c.blue))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EcoAutoMoney("已花 ${Money.formatYuanGrouped(spentCents)}", text, text.color, Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.Start)
                Text("${(ratio * 100).roundToInt()}%", style = text, modifier = Modifier.padding(horizontal = 8.dp))
                EcoAutoMoney("剩余 ${Money.formatYuanGrouped(budgetCents - spentCents)}", text, text.color, Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.End)
            }
        }
    }
}
