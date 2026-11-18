package com.controleco.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controleco.app.data.LedgerViewModel
import com.controleco.app.data.PeriodMode
import com.controleco.app.data.budget.MonthPeriod
import com.controleco.app.ui.common.EcoIcons
import com.controleco.app.ui.common.PeriodDateDialog
import com.controleco.app.ui.common.PeriodPickerSheet
import com.controleco.app.ui.eco.EcoAutoMoney
import com.controleco.app.ui.eco.EcoFilterStrip
import com.controleco.app.ui.eco.EcoTopBar
import com.controleco.app.ui.stats.ChartMath
import com.controleco.app.ui.theme.EcoType
import com.controleco.app.ui.theme.eco
import com.controleco.app.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SpendingTrendScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val c = MaterialTheme.eco
    val today by vm.today.collectAsStateWithLifecycle()
    val startDay by vm.monthStartDay.collectAsStateWithLifecycle()
    var cumulative by rememberSaveable { mutableStateOf(false) }
    var pickedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val latestMonth = MonthPeriod.monthOf(today, startDay)
    val endDay = pickedDay?.let { minOf(LocalDate.parse(it), today) } ?: today
    val month = pickedMonth?.let { minOf(YearMonth.parse(it), latestMonth) } ?: latestMonth
    val periods = remember(today, startDay, cumulative, endDay, month) {
        if (cumulative) monthlyTrendPeriods(month, today, startDay) else weeklyTrendPeriods(endDay)
    }
    val trend = remember(vm, periods) {
        vm.observeTrendRows(periods.previous.start, periods.current.endExclusive)
            .map { spendingTrendData(it, periods) }
            .flowOn(Dispatchers.Default)
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        EcoTopBar(micro = "SPENDING", title = "支出趋势", onBack = onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrendDateButton(
                        date = if (cumulative) "${month.year}年${month.monthValue}月" else "${endDay.year}年${shortDate(endDay)}",
                        description = if (cumulative) "选择月份" else "选择结束日期",
                        onClick = { showPicker = true },
                    )
                    Text("${shortDate(periods.current.start)}–${shortDate(periods.current.endExclusive.minusDays(1))}",
                        style = EcoType.PageNote, color = c.muted)
                }
                EcoFilterStrip(listOf("7天", "累计"), if (cumulative) 1 else 0, { cumulative = it == 1 })
            }
            key(periods) {
                val data by trend.collectAsStateWithLifecycle(initialValue = null)
                data?.let {
                    SpendingTrendSummary(it, periods)
                    SpendingTrendPlot(it, periods)
                    SpendingCategoryChanges(it.categoryChanges)
                } ?: Text("正在读取支出…", style = EcoType.PageNote, color = c.muted)
            }
        }
    }
    if (showPicker) {
        if (cumulative) {
            PeriodPickerSheet(
                mode = PeriodMode.MONTH,
                current = month,
                currentMonth = latestMonth,
                onSelectMonth = { pickedMonth = it.toString() },
                onSelectYear = {},
                onDismiss = { showPicker = false },
            )
        } else {
            PeriodDateDialog(
                current = endDay,
                latest = today,
                confirmLabel = "确定",
                onPick = { pickedDay = it.toString(); showPicker = false },
                onDismiss = { showPicker = false },
            )
        }
    }
}

@Composable
private fun TrendDateButton(date: String, description: String, onClick: () -> Unit) {
    val c = MaterialTheme.eco
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(c.blueSoft)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("trendDatePicker")
            .padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EcoIcons.Calendar, contentDescription = null, tint = c.blueDeep, modifier = Modifier.size(14.dp))
        Text(date, style = EcoType.RowTitle, color = c.blueDeep, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(EcoIcons.ChevDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(11.dp))
    }
}

@Composable
private fun SpendingTrendSummary(data: SpendingTrendData, periods: SpendingTrendPeriods) {
    val c = MaterialTheme.eco
    val change = data.change
    val before = data.comparablePrevious
    val current = data.current.sum()
    val days = periods.current.days
    val changeText = when {
        change > 0 -> "+${Money.formatYuanGroupedFixed(change)}"
        change < 0 -> Money.formatYuanGroupedFixed(change)
        else -> "持平"
    }
    val rateText = if (before > 0 && change != 0L) {
        val symbol = if (change > 0) "↑" else "↓"
        "  $symbol${String.format(Locale.ROOT, "%.1f", abs(change.toDouble()) * 100 / before)}%"
    } else ""
    val priorLabel = when {
        !periods.cumulative -> "前7天"
        periods.comparisonDays != null -> "上月同期"
        else -> "上月"
    }
    Column(
        Modifier.fillMaxWidth().background(c.blueSoft, RoundedCornerShape(17.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("本期支出", style = EcoType.PageNote, color = c.muted)
                EcoAutoMoney(Money.formatYuanGroupedFixed(current), EcoType.SectionTitle.copy(fontSize = 23.sp), c.ink,
                    Modifier.fillMaxWidth(), minSize = 12.sp, textAlign = TextAlign.Start)
                Text("日均 ${Money.formatYuanGroupedFixed(current / days)}", style = EcoType.PageNote, color = c.muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                Text("较$priorLabel", style = EcoType.PageNote, color = c.muted)
                EcoAutoMoney(changeText + rateText, EcoType.RowTitle, when {
                    change > 0 -> c.clay
                    change < 0 -> c.jade
                    else -> c.ink
                }, Modifier.fillMaxWidth(), minSize = 9.sp, textAlign = TextAlign.End)
                Text(if (before > 0) "$priorLabel ${Money.formatYuanGroupedFixed(before)}" else "${priorLabel}无支出",
                    style = EcoType.PageNote, color = c.muted, textAlign = TextAlign.End)
            }
        }
        if (periods.comparisonDays != null && periods.comparisonDays < periods.current.days) {
            Text("上期较短，差额按两期共有天数计算", style = EcoType.PageNote, color = c.muted)
        }
    }
}

@Composable
private fun SpendingTrendPlot(data: SpendingTrendData, periods: SpendingTrendPeriods) {
    val c = MaterialTheme.eco
    var point by rememberSaveable { mutableIntStateOf(-1) }
    val cumulative = periods.cumulative
    val current = remember(data, cumulative) { if (cumulative) data.current.runningReduce(Long::plus) else data.current }
    val previous = remember(data, cumulative) { if (cumulative) data.previous.runningReduce(Long::plus) else data.previous }
    val selected = if (point < 0) current.lastIndex else point.coerceAtMost(periods.axisDays - 1)
    val date = periods.current.start.plusDays(selected.toLong())
    val calendarAligned = periods.current.start.dayOfMonth == 1 && periods.previous.start.dayOfMonth == 1
    val axisLabel: (Int) -> String = { index ->
        when {
            cumulative && calendarAligned -> "${index + 1}日"
            cumulative -> "${index + 1}"
            else -> periods.current.start.plusDays(index.toLong()).let { "${it.monthValue}/${it.dayOfMonth}" }
        }
    }
    val readoutDate = when {
        !cumulative -> shortDate(date)
        calendarAligned -> "${selected + 1}日累计"
        else -> "第${selected + 1}天累计"
    }
    val currentLabel = if (cumulative) "${periods.current.start.monthValue}月" else "本期"
    val previousLabel = if (cumulative) "${periods.previous.start.monthValue}月" else "上期"
    Column(
        Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(17.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (cumulative) "累计支出" else "每日支出", style = EcoType.RowTitle, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("━ $currentLabel", style = EcoType.PageNote, color = c.blueDeep)
                Text("━ $previousLabel", style = EcoType.PageNote, color = c.clay)
            }
        }
        ScrubbableSpendingChart(current, previous, selected, periods.axisDays, axisLabel, onSelect = { point = it })
        Row(
            Modifier.fillMaxWidth().background(c.blueSoft, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(readoutDate, Modifier.testTag("trendSelectionDate"), style = EcoType.PageNote, color = c.ink)
            EcoAutoMoney("$currentLabel ${current.getOrNull(selected)?.let(Money::formatYuanGroupedFixed) ?: "—"}", EcoType.PageNote, c.blueDeep,
                Modifier.weight(1f), minSize = 8.sp, textAlign = TextAlign.End)
            EcoAutoMoney("$previousLabel ${previous.getOrNull(selected)?.let(Money::formatYuanGroupedFixed) ?: "—"}",
                EcoType.PageNote, c.clay, Modifier.weight(1f), minSize = 8.sp, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun ScrubbableSpendingChart(
    current: List<Long>,
    previous: List<Long>,
    selected: Int,
    axisDays: Int,
    dateLabel: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    val c = MaterialTheme.eco
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = c.muted, fontSize = 9.sp)
    val peak = maxOf(current.maxOrNull() ?: 0L, previous.maxOrNull() ?: 0L)
    val maxY = ChartMath.niceCeil(peak / 100f)
    val slots = axisDays - 1
    val gutter = 48.dp
    val rightPad = 19.dp
    Canvas(
        Modifier.fillMaxWidth().height(202.dp).testTag("spendingTrendChart")
            .semantics { contentDescription = "支出曲线，左右滑动查看金额" }
            .pointerInput(slots) {
                awaitEachGesture {
                    fun pick(x: Float) {
                        val left = gutter.toPx().coerceAtMost(size.width / 3f)
                        val width = (size.width - left - rightPad.toPx()).coerceAtLeast(1f)
                        val fraction = ((x - left) / width).coerceIn(0f, 1f)
                        onSelect((fraction * slots).roundToInt())
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pick(down.position.x)
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                        pick(change.position.x)
                    }
                    if (start != null) horizontalDrag(start.id) { change ->
                        pick(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        val left = gutter.toPx().coerceAtMost(size.width / 3f)
        val right = (size.width - rightPad.toPx()).coerceAtLeast(left)
        val top = 8.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun position(index: Int, value: Long) =
            Offset(left + (right - left) * index / slots, bottom - (value / 100f / maxY) * (bottom - top))
        (0..4).forEach { tick ->
            val y = bottom - (bottom - top) * tick / 4f
            drawLine(c.rule, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(Money.symbol + ChartMath.axisAmount(maxY * tick / 4f), tickStyle)
            drawText(label, topLeft = Offset((left - label.size.width - 4.dp.toPx()).coerceAtLeast(0f), y - label.size.height / 2f))
        }
        val indices = if (axisDays == TREND_WEEK_DAYS) (0..slots).toList() else {
            listOf(0, 4, 9, 14, 19, 24, slots).filter { it <= slots }.distinct()
        }
        indices.forEach { index ->
            val label = measurer.measure(dateLabel(index), tickStyle)
            val x = position(index, 0).x - label.size.width / 2f
            drawText(label, topLeft = Offset(x.coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)), bottom + 6.dp.toPx()))
        }
        val x = position(selected, 0).x
        drawLine(c.ruleStrong, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
        plotSeries(previous, c.clay, ::position)
        plotSeries(current, c.blueDeep, ::position)
        previous.getOrNull(selected)?.let { drawCircle(c.clay, 3.dp.toPx(), position(selected, it)) }
        current.getOrNull(selected)?.let { drawCircle(c.blueDeep, 4.dp.toPx(), position(selected, it)) }
    }
}

private fun DrawScope.plotSeries(series: List<Long>, color: Color, position: (Int, Long) -> Offset) {
    val path = Path()
    series.forEachIndexed { index, value ->
        val p = position(index, value)
        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun SpendingCategoryChanges(changes: List<SpendingCategoryChange>) {
    val c = MaterialTheme.eco
    Column(
        Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(17.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("分类支出变化", style = EcoType.SectionTitle, color = c.ink)
        if (changes.isEmpty()) {
            Text("两期分类支出持平", style = EcoType.PageNote, color = c.muted)
        } else {
            val max = abs(changes.first().cents).toDouble()
            changes.take(5).forEach { entry ->
                val decrease = entry.cents < 0
                val color = if (decrease) c.jade else c.clay
                val amount = Money.formatYuanGroupedFixed(entry.cents)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (decrease) {
                            EcoAutoMoney(amount, EcoType.RowTrail, color, Modifier.weight(1f),
                                minSize = 8.sp, textAlign = TextAlign.Start)
                        } else Box(Modifier.weight(1f))
                        Text(entry.name, Modifier.weight(1f), style = EcoType.RowSub, color = c.ink,
                            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!decrease) {
                            EcoAutoMoney("+$amount", EcoType.RowTrail, color, Modifier.weight(1f),
                                minSize = 8.sp, textAlign = TextAlign.End)
                        } else Box(Modifier.weight(1f))
                    }
                    Canvas(Modifier.fillMaxWidth().height(7.dp)) {
                        val mid = size.width / 2f
                        val width = mid * (abs(entry.cents) / max).toFloat()
                        drawRoundRect(color, topLeft = Offset(if (decrease) mid - width else mid, 0f),
                            size = Size(width, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
    }
}

private fun shortDate(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"
