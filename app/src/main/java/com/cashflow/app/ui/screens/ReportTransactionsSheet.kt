package com.cashflow.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cashflow.app.data.model.*
import com.cashflow.app.ui.components.*

@Composable
fun ReportTransactionsSheet(
    title: String, periodLabel: String, transactions: List<Transaction>,
    categories: Map<String, Category>, assets: Map<String, Asset>, isBalanceHidden: Boolean,
    onDismiss: () -> Unit, onOpen: (Transaction) -> Unit,
    summaryText: String? = null
) {
    DuitAingModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
            Text(periodLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
            Text("${transactions.size} transaksi · " + if (isBalanceHidden) "••••••" else summaryText ?: Formatters.formatRupiah(transactions.sumOf { it.amount }),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                if (transactions.isEmpty()) item { Text("Tidak ada transaksi pada rincian ini.") }
                items(transactions, key = { it.id }) { tx ->
                    Text(Formatters.formatDateReadable(tx.transactionDate), style = MaterialTheme.typography.labelSmall)
                    TransactionItemCard(tx, categories[tx.categoryId], assets[tx.assetId], assets[tx.destinationAssetId],
                        onClick = { onOpen(tx) }, isBalanceHidden = isBalanceHidden)
                }
            }
        }
    }
}
