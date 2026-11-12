package com.controleco.app.ui.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.controleco.app.data.budget.MonthPeriod
import com.controleco.app.data.db.TransactionRow
import com.controleco.app.ui.eco.EcoPane
import com.controleco.app.ui.theme.*
import com.controleco.app.util.Money
import java.time.*
import kotlin.math.sqrt

data class SpendingDay(val date: LocalDate, val cents: Long, val count: Int)

/** Adapt the existing ECO daily-spending calculation; no separate financial totals. */
internal fun calendarDays(rows: List<TransactionRow>, month: YearMonth, startDay: Int, zone: ZoneId = ZoneId.systemDefault()): List<SpendingDay> {
    val period=MonthPeriod.of(month,startDay)
    return ChartMath.monthBars(rows,month,startDay,zone).mapIndexed { i, bar -> SpendingDay(period.start.plusDays(i.toLong()),bar.cents,bar.count) }
}
internal fun calendarHeat(cents: Long, maximum: Long): Float =
    if(cents<=0 || maximum<=0) 0f else sqrt((cents.toDouble()/maximum).coerceIn(0.0,1.0)).toFloat()

@Composable
internal fun SpendingCalendarCard(month: YearMonth, startDay: Int, rows: List<TransactionRow>, onOpenDay: (LocalDate) -> Unit = {}) {
    val c=MaterialTheme.eco
    val days=remember(rows,month,startDay) { calendarDays(rows,month,startDay) }
    val today=LocalDate.now()
    var selected by remember(month,startDay) { mutableStateOf(days.firstOrNull { it.date==today }?.date ?: days.maxByOrNull { it.cents }?.date) }
    val chosen=days.firstOrNull { it.date==selected }
    val maximum=days.maxOfOrNull { it.cents } ?: 0L
    val leading=(days.firstOrNull()?.date?.dayOfWeek?.value ?: 1)-1
    val slots=List<SpendingDay?>(leading) { null }+days
    val weeks=slots.chunked(7)
    EcoPane(Modifier.fillMaxWidth().padding(horizontal=22.dp).testTag("spendingCalendar"),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("支出日历",style=EcoType.SectionTitle,color=c.ink)
                Text(chosen?.let { "${it.date.monthValue}月${it.date.dayOfMonth}日  ${Money.formatYuan(it.cents)}" } ?: "元",fontSize=11.sp,color=c.muted)
            }
            chosen?.let { day ->
                TextButton(onClick = { onOpenDay(day.date) }, modifier = Modifier.align(Alignment.End).testTag("confirmSpendingDay")) {
                    Text("查看 ${day.date.monthValue}月${day.date.dayOfMonth}日流水", color = c.blueDeep)
                }
            }
            if(startDay!=1 && days.isNotEmpty()) Text("${days.first().date} — ${days.last().date}",fontSize=10.sp,color=c.quiet)
            Row(Modifier.fillMaxWidth()) {
                listOf("一","二","三","四","五","六","日").forEach { Text(it,Modifier.weight(1f),fontSize=10.sp,color=c.quiet,textAlign=TextAlign.Center) }
            }
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    for(i in 0..6) {
                        val day=week.getOrNull(i)
                        if(day==null) { Spacer(Modifier.weight(1f).height(53.dp));continue }
                        val heat=calendarHeat(day.cents,maximum)
                        val bg=if(heat==0f) c.surface2 else lerp(c.surface2,c.blue,.14f+heat*.72f)
                        val fg=contentColorOn(bg)
                        Column(Modifier.weight(1f).height(53.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                            .border(if(day.date==selected) 1.5.dp else .5.dp,if(day.date==selected) c.blueDeep else c.rule.copy(alpha=.4f),RoundedCornerShape(8.dp))
                            .clickable { selected=day.date }.testTag("spendingDay-${day.date}")
                            .semantics { contentDescription="${day.date} 支出 ${Money.formatYuan(day.cents)}" },
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Text(if(day.date.dayOfMonth==1 && startDay!=1) "${day.date.monthValue}/1" else day.date.dayOfMonth.toString(),
                                color=fg,fontSize=12.sp,fontWeight=if(day.date==today) FontWeight.Bold else FontWeight.Medium)
                            Spacer(Modifier.height(3.dp))
                            val amount=if(day.cents>0) Money.formatCents(day.cents) else ""
                            Text(amount,color=fg,fontSize=if(amount.length>7) 7.sp else 9.sp,maxLines=1,textAlign=TextAlign.Center)
                        }
                    }
                }
            }
            Text("颜色越深，支出越多",fontSize=10.sp,color=c.quiet,modifier=Modifier.padding(top=2.dp))

        }
    }
}


/** Twelve accounting months, calculated with the same refund/AA rules as daily cells. */
@Composable
internal fun SpendingMonthGrid(year: Int, startDay: Int, rows: List<TransactionRow>, onOpenMonth: (YearMonth) -> Unit) {
    val c = MaterialTheme.eco
    val months = remember(year, startDay, rows) { ChartMath.yearBars(rows, YearMonth.of(year, 1), startDay) }
    val maximum = months.maxOfOrNull { it.cents } ?: 0L
    var selected by remember(year) { mutableStateOf<YearMonth?>(null) }
    EcoPane(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("spendingMonthGrid"), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("月度支出", style = EcoType.SectionTitle, color = c.ink)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selected?.let(onOpenMonth) }, enabled = selected != null,
                    modifier = Modifier.testTag("confirmSpendingMonth")) {
                    Text(selected?.let { "查看 ${it.monthValue}月流水" } ?: "先选择月份",
                        color = if (selected != null) c.blueDeep else c.quiet, fontSize = 11.sp)
                }
            }
            (0..1).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..5).forEach { column ->
                        val index = row * 6 + column
                        val month = YearMonth.of(year, index + 1)
                        val value = months[index]
                        val heat = calendarHeat(value.cents, maximum)
                        val bg = if (heat == 0f) c.surface2 else lerp(c.surface2, c.blue, .14f + heat * .72f)
                        val fg = contentColorOn(bg)
                        Column(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(6.dp)).background(bg)
                            .border(if (selected == month) 1.5.dp else .5.dp,
                                if (selected == month) c.blueDeep else c.rule.copy(alpha = .4f), RoundedCornerShape(6.dp))
                            .clickable { selected = month }.testTag("spendingMonth-$month")
                            .semantics { contentDescription = "$month 支出 ${Money.formatYuan(value.cents)}" },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("${index + 1}月", fontSize = 11.sp, color = fg, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(3.dp))
                            Text(Money.formatCents(value.cents), fontSize = 9.sp, color = fg, maxLines = 1)
                        }
                    }
                }
            }
            Text("颜色越深，支出越多", fontSize = 9.sp, color = c.quiet, modifier = Modifier.padding(top = 4.dp))

        }
    }
}
