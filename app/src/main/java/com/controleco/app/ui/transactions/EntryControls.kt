package com.controleco.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleco.app.data.MerchantSuggestion
import com.controleco.app.data.db.CategoryEntity
import com.controleco.app.ui.common.EcoIcons
import com.controleco.app.ui.eco.*
import com.controleco.app.ui.theme.eco
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryDateTime(date: LocalDate, time: LocalTime, onDate: (LocalDate) -> Unit, onTime: (LocalTime) -> Unit) {
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EntryDateCell("日期", date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), EcoIcons.Calendar, { dateOpen = true }, Modifier.weight(1f))
        EntryDateCell("时间", time.format(DateTimeFormatter.ofPattern("HH:mm")), EcoIcons.Clock, { timeOpen = true }, Modifier.weight(1f))
    }
    if (dateOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateOpen = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                dateOpen = false
            }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("取消") } }) { DatePicker(state) }
    }
    if (timeOpen) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { timeOpen = false }, title = { Text("选择时间") },
            text = { TimeInput(state) }, confirmButton = {
                TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)); timeOpen = false }) { Text("确定") }
            }, dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("取消") } })
    }
}

@Composable private fun EntryDateCell(label: String, value: String, icon: ImageVector, click: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.eco
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.rule, RoundedCornerShape(12.dp))
        .clickable(onClick = click).semantics { contentDescription = "选择$label" }.padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 10.sp, color = c.quiet)
            Text(value, fontSize = 12.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(EcoIcons.ChevDown, null, tint = c.quiet, modifier = Modifier.size(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryCategories(categories: List<CategoryEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    val c = MaterialTheme.eco
    val expense = categories.firstOrNull()?.type == com.controleco.app.data.db.TxType.EXPENSE
    val pinned = if (expense) com.controleco.app.data.CategoryPresets.pinnedExpenseNames.mapNotNull { name -> categories.firstOrNull { it.name == name } } else categories.take(3)
    val more = categories.filterNot { cat -> pinned.any { it.id == cat.id } }.let { remaining ->
        if (expense) remaining.sortedBy { cat ->
            com.controleco.app.data.CategoryPresets.all.indexOfFirst { it.name == cat.name }.takeIf { it >= 0 } ?: (1000 + cat.sortOrder)
        } else remaining
    }
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("分类", fontSize = 12.sp, color = c.muted)
            Spacer(Modifier.weight(1f))
            Text(categories.firstOrNull { it.id == selectedId }?.name ?: "请选择分类", fontSize = 12.sp, color = c.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pinned.forEach { category ->
                EntryCategoryChip(category.name, category.id == selectedId, Modifier.weight(1f)) { onSelect(category.id) }
            }
            repeat((3 - pinned.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            EntryCategoryChip("更多", false, Modifier.weight(1f)) { query = ""; open = true }
        }
        if (categories.isEmpty()) Text("当前类型下还没有分类", fontSize = 11.sp, color = c.quiet)
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.paper) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("更多分类", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { open = false }) { Text("关闭") }
                }
                EcoField("", query, { query = it }, placeholder = "搜索分类", minHeight = 42.dp)
                Spacer(Modifier.height(12.dp))
                val filtered = more.filter { it.name.contains(query.trim(), ignoreCase = true) }
                if (filtered.isEmpty()) Text("没有匹配的分类", color = c.muted, modifier = Modifier.padding(vertical = 20.dp))
                LazyVerticalGrid(columns = GridCells.Adaptive(70.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filtered, key = { it.id }) { cat ->
                        Column(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (cat.id == selectedId) c.blueWash else c.surface)
                            .border(1.dp, if (cat.id == selectedId) c.blue else c.rule, RoundedCornerShape(12.dp))
                            .clickable { onSelect(cat.id); open = false },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            EcoCategoryTile(cat.name, cat.icon, size = 24.dp, bare = true)
                            Text(cat.name, fontSize = 12.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun EntryCategoryChip(name: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    val c = MaterialTheme.eco
    Box(modifier.height(43.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) c.blueWash else c.surface)
        .border(1.dp, if (selected) c.blue else c.rule, RoundedCornerShape(12.dp)).clickable(onClick = click), contentAlignment = Alignment.Center) {
        Text(name, fontSize = 13.sp, color = if (selected) c.blueDeep else c.muted, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
internal fun EntryMerchant(value: String, onValue: (String) -> Unit, suggestions: List<MerchantSuggestion>,
                           onPick: (MerchantSuggestion) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    var dismissedValue by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        EcoField("商户", value, { dismissedValue = null; onValue(it) }, placeholder = "输入或选择商户", minHeight = 36.dp,
            onFocused = { focused = it })
        if (focused && value.isNotBlank() && dismissedValue != value && suggestions.isNotEmpty()) {
            val c = MaterialTheme.eco
            EcoPane(Modifier.fillMaxWidth().padding(top = 4.dp), elevation = 3.dp) {
                Column {
                    suggestions.take(4).forEach { suggestion ->
                        Column(Modifier.fillMaxWidth().clickable {
                            onPick(suggestion); dismissedValue = suggestion.name
                        }.padding(horizontal = 10.dp, vertical = 9.dp)) {
                            Text(suggestion.name, fontSize = 12.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("默认分类：${suggestion.categoryName}", fontSize = 10.sp, color = c.muted)
                        }
                    }
                }
            }
        }
    }
}
