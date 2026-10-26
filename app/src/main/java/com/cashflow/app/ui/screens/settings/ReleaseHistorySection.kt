package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashflow.app.BuildConfig
import com.cashflow.app.data.update.ReleaseHistoryViewModel
import com.cashflow.app.ui.theme.duit
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReleaseHistorySection() {
    val manager: ReleaseHistoryViewModel = viewModel()
    val state by manager.status.collectAsState()
    val duit = MaterialTheme.duit
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID")) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pilih versi untuk membaca perubahannya.", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
                TextButton(onClick = manager::refresh, enabled = !state.loading) { Text("Segarkan", color = duit.textPrimary) }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = duit.textSecondary) }
        }
        items(state.entries, key = { it.tag }) { entry ->
            val open = expanded == entry.tag
            val installed = entry.versionCode == BuildConfig.VERSION_CODE.toLong() && entry.versionName == BuildConfig.VERSION_NAME
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = if (open) null else entry.tag }
                    .semantics { stateDescription = if (open) "Dibuka" else "Ditutup" }
                    .padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = duit.textPrimary)
                        val details = listOfNotNull(if (installed) "Terpasang" else null,
                            entry.publishedAt?.atZone(ZoneId.systemDefault())?.format(dateFormat), if (entry.preview) "Uji coba" else null)
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
                    }
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = duit.textSecondary)
                }
                if (open) Text(entry.notes, Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    style = MaterialTheme.typography.bodyMedium, color = duit.textSecondary)
                HorizontalDivider(color = duit.border)
            }
        }
        if (state.hasMore && state.entries.isNotEmpty()) item {
            TextButton(onClick = manager::loadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Muat rilis lainnya", color = duit.textPrimary)
            }
        }
    }
}
