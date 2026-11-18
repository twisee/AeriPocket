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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontFamily
import com.controleco.app.R
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
import java.util.WeakHashMap

internal val BalanceCoverHeight = 150.dp
internal val BalanceCoverShape = RoundedCornerShape(22.dp)

internal enum class AliceCoverArt(val filename: String, val lightWash: Color) {
    BUDGET("card_75_flowers.png", Color(0xFFFFF8FA)),
    LEDGER("card_ledger_market.webp", Color(0xFFFFF6EE)),
    OVERVIEW("card_overview_desk.webp", Color(0xFFF4F2FF)),
}

private object FloralArtworkCache {
    private val images = WeakHashMap<Context, MutableMap<AliceCoverArt, ImageBitmap>>()
    fun peek(context: Context, art: AliceCoverArt) = synchronized(images) { images[context.applicationContext]?.get(art) }
    fun load(context: Context, art: AliceCoverArt): ImageBitmap {
        peek(context, art)?.let { return it }
        val image = context.assets.open("characters/aerith/${art.filename}").use {
            BitmapFactory.decodeStream(it).asImageBitmap()
        }
        return synchronized(images) {
            images.getOrPut(context.applicationContext) { mutableMapOf() }.getOrPut(art) { image }
        }
    }
}

/** The approved floral artwork is already blurred behind the clear character. No bottom scrim. */
@Composable
internal fun AliceFloralBackground(modifier: Modifier = Modifier, art: AliceCoverArt = AliceCoverArt.BUDGET) {
    val context = LocalContext.current
    val dark = MaterialTheme.eco.isDark
    var artwork by remember(context, art) { mutableStateOf(FloralArtworkCache.peek(context, art)) }
    LaunchedEffect(context, art) {
        artwork = withContext(Dispatchers.IO) { FloralArtworkCache.load(context, art) }
    }
    Box(modifier.background(MaterialTheme.eco.surfaceInset)) {
        artwork?.let { Image(it, when (art) {
            AliceCoverArt.BUDGET -> "爱丽丝花卉卡片"
            AliceCoverArt.LEDGER -> "爱丽丝在集市接小票"
            AliceCoverArt.OVERVIEW -> "爱丽丝在书桌翻阅账本"
        }, Modifier.fillMaxSize().testTag("aliceFloralArtwork"),
            alignment = Alignment.CenterEnd, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High) }
        val wash = if (dark) Color(0xFF231821) else art.lightWash
        val lightAlpha = if (art == AliceCoverArt.BUDGET) .78f else .28f
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to wash.copy(alpha = if (dark) .88f else lightAlpha),
            .38f to wash.copy(alpha = if (dark) .57f else lightAlpha * .66f),
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
    onModeChange: (PeriodMode) -> Unit, onOpenImageSettings: () -> Unit, modifier: Modifier, showEdit: Boolean, showSpending: Boolean = false) {
    val text = floralTextStyle(11)
    Box(modifier.fillMaxWidth().height(BalanceCoverHeight).clip(BalanceCoverShape).testTag("aliceBalanceCard")) {
        AliceFloralBackground(Modifier.matchParentSize(), if (showSpending) AliceCoverArt.LEDGER else AliceCoverArt.OVERVIEW)
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 13.dp)) {
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
                Text(if (showSpending) { if (mode == PeriodMode.MONTH) "本月花费" else "本年花费" } else { if (mode == PeriodMode.MONTH) "本月结余" else "本年结余" }, style = text, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (showEdit) Box(Modifier.size(29.dp).clip(CircleShape)
                    .background(if (MaterialTheme.eco.isDark) Color(0xB0231821) else Color(0xAAFFF8FA))
                    .clickable(onClick = onOpenImageSettings), contentAlignment = Alignment.Center) {
                    Icon(EcoIcons.Edit, "更换结余卡背景", tint = text.color, modifier = Modifier.size(13.dp))
                }
            }
            EcoAutoMoney(Money.formatYuanGroupedFixed(if (showSpending) expenseCents else balanceCents),
                floralTextStyle(30), text.color, Modifier.align(Alignment.CenterStart).fillMaxWidth(.64f),
                minSize = 16.sp, textAlign = TextAlign.Start)
            if (!showSpending) {
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(.74f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EcoAutoMoney("收入 ${Money.formatYuanGrouped(incomeCents)}", text, text.color, Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.Start)
                    EcoAutoMoney("支出 ${Money.formatYuanGrouped(expenseCents)}", text, text.color, Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.Start)
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
internal fun AliceBudgetCard(budgetCents: Long, spentCents: Long, currency: String,
    onCurrency: () -> Unit, onOpenAccount: () -> Unit, onManageBudget: () -> Unit, title: String = "本月预算",
    budgetEditLabel: String = "设置本月预算") {
    val text = floralTextStyle(11)
    Box(Modifier.fillMaxWidth().height(197.dp).clip(RoundedCornerShape(28.dp)).testTag("aliceBudgetCard")) {
        AliceFloralBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(17.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = text)
                Box(Modifier.size(26.dp).clip(CircleShape)
                    .background(if (MaterialTheme.eco.isDark) Color(0xB0231821) else Color.White.copy(alpha = .65f))
                    .clickable(onClick = onManageBudget), contentAlignment = Alignment.Center) {
                    Icon(EcoIcons.Edit, budgetEditLabel, tint = text.color, modifier = Modifier.size(13.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(.54f), contentAlignment = Alignment.CenterStart) {
                val amountInk = if (MaterialTheme.eco.isDark) Color(0xFFFFF7F9) else Color(0xFF271824)
                val moneyStyle = floralTextStyle(31).copy(color = amountInk, fontFamily = FontFamily(Font(R.font.manrope, weight = FontWeight.Medium,
                    variationSettings = FontVariation.Settings(FontVariation.weight(500)))))
                if (budgetCents > 0) EcoCountUpMoney(budgetCents, moneyStyle, amountInk,
                    Modifier.fillMaxWidth(), minSize = 19.sp, textAlign = TextAlign.Start,
                    format = { Money.formatYuanGrouped(it / 100 * 100) })
                else Text("未设置", style = moneyStyle)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EcoAutoMoney("已花 ${Money.formatYuanGrouped(spentCents)}", text, text.color, Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.Start)
                EcoAutoMoney("剩余 ${Money.formatYuanGrouped(budgetCents - spentCents)}", text, text.color, Modifier.weight(1f), minSize = 9.sp, textAlign = TextAlign.End)
            }
        }
    }
}
