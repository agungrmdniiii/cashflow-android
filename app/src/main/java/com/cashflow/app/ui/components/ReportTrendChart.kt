package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.CashflowBucket
import com.cashflow.app.ui.theme.duit

@Composable
fun ReportTrendChart(buckets: List<CashflowBucket>, isExpense: Boolean, hidden: Boolean, modifier: Modifier = Modifier) {
    val duit = MaterialTheme.duit
    val amounts = buckets.map { if (isExpense) it.expense else it.income }
    val peak = amounts.maxOrNull() ?: 0L
    var selected by remember(buckets, isExpense) { mutableIntStateOf(amounts.indexOfLast { it > 0 }.coerceAtLeast(0)) }
    Column(modifier) {
        if (hidden) {
            Text("Grafik disembunyikan saat nominal disamarkan", style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
        } else if (peak <= 0 || buckets.isEmpty()) {
            Text("Belum ada transaksi pada rentang ini.", color = duit.textSecondary)
        } else {
            Text("${buckets[selected].label} • ${Formatters.formatRupiah(amounts[selected])}", color = duit.textPrimary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                buckets.forEachIndexed { index, bucket ->
                    Column(Modifier.weight(1f).fillMaxHeight().semantics {
                        contentDescription = "${bucket.label}, ${Formatters.formatRupiah(amounts[index])}"
                    }.clickable(role = Role.Button) { selected = index }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.width(18.dp).height((96f * amounts[index].toFloat() / peak).dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (index == selected) if (isExpense) duit.expense else duit.income else duit.chartInactive))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(bucket.label, fontSize = 10.sp, color = duit.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
