package com.controleco.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.ui.common.EcoIcons
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Composable
internal fun ProfileSpendingTrend(vm: LedgerViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.eco
    val today by vm.today.collectAsStateWithLifecycle()
    val period = remember(today) { weeklyTrendPeriods(today).current }
    val flow = remember(vm, period) {
        vm.observeTrendRows(period.start, period.endExclusive).map { spendSeries(it, period) }.flowOn(Dispatchers.Default)
    }
    val values by key(period) { flow.collectAsStateWithLifecycle(initialValue = emptyList()) }
    val ticks = remember(period) {
        List(TREND_WEEK_DAYS) { index ->
            val date = period.start.plusDays(index.toLong())
            "${date.monthValue}/${date.dayOfMonth}"
        }
    }
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = c.muted, fontSize = 9.sp)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.blueSoft)
            .clickable(onClickLabel = "打开支出趋势", onClick = onOpen)
            .semantics { contentDescription = "最近7天支出趋势" }
            .padding(horizontal = 11.dp, vertical = 9.dp)
            .testTag("profileSpendingTrend"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("支出趋势", style = EcoType.RowTitle, fontSize = 12.sp, color = c.ink, maxLines = 1)
            Icon(EcoIcons.Chev, contentDescription = null, tint = c.muted, modifier = Modifier.size(13.dp))
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val columnWidth = size.width / TREND_WEEK_DAYS
            fun xAt(index: Int) = columnWidth * (index + .5f)
            val normalLabels = ticks.map { measurer.measure(it, tickStyle) }
            val widest = normalLabels.maxOf { it.size.width }.coerceAtLeast(1)
            val scale = ((columnWidth - 1.dp.toPx()) / widest).coerceIn(.45f, 1f)
            val labels = if (scale == 1f) normalLabels else {
                ticks.map { measurer.measure(it, tickStyle.copy(fontSize = (9 * scale).sp)) }
            }
            val labelHeight = labels.maxOf { it.size.height }
            val baseline = (size.height - labelHeight - 7.dp.toPx()).coerceAtLeast(0f)
            val plotHeight = (baseline - 6.dp.toPx()).coerceAtLeast(0f)
            val peak = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toDouble()
            var end = Offset.Zero
            val curve = Path()
            values.forEachIndexed { index, cents ->
                end = Offset(xAt(index), baseline - (cents / peak).toFloat() * plotHeight)
                if (index == 0) curve.moveTo(end.x, end.y) else curve.lineTo(end.x, end.y)
            }
            if (values.isNotEmpty()) {
                drawPath(curve, c.blueDeep, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(c.blueDeep, 2.4.dp.toPx(), end)
            }
            labels.forEachIndexed { index, label ->
                drawText(label, topLeft = Offset(xAt(index) - label.size.width / 2f, size.height - label.size.height))
            }
        }
    }
}
