package com.controleco.app.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodDateDialog(
    current: LocalDate,
    latest: LocalDate,
    confirmLabel: String,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestMillis = latest.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val selectableDates = remember(latest) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestMillis
            override fun isSelectableYear(year: Int): Boolean = year <= latest.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = minOf(current, latest).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates,
    )
    val selected = state.selectedDateMillis?.takeIf { it <= latestMillis }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = selected != null,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}
