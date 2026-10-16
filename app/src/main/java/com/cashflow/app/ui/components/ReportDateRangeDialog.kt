package com.cashflow.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.ReportDateRange
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDateRangeDialog(initialRange: ReportDateRange?, onDismiss: () -> Unit, onApply: (ReportDateRange) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange?.start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = initialRange?.end?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Button(enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null, onClick = {
                        val start = Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val end = Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        onApply(ReportDateRange(start, end))
                    }) { Text("Gunakan rentang") }
                }
                Text("Rentang laporan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                DateRangePicker(state = state, modifier = Modifier.weight(1f), title = null, showModeToggle = true)
            }
        }
    }
}
