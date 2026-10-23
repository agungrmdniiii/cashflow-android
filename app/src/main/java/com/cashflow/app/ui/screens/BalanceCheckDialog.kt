package com.cashflow.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cashflow.app.data.model.Asset
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.ui.components.ThousandSeparatorVisualTransformation

@Composable
fun BalanceCheckDialog(
    asset: Asset,
    onDismiss: () -> Unit,
    onAdjust: (Long, String, (Boolean) -> Unit) -> Unit
) {
    var input by remember(asset.id) { mutableStateOf("") }
    var note by remember(asset.id) { mutableStateOf("") }
    var confirming by remember(asset.currentBalance) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val target = input.toLongOrNull()
    val difference = target?.let { runCatching { Math.subtractExact(it, asset.currentBalance) }.getOrNull() }
        ?.takeUnless { it == Long.MIN_VALUE }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (confirming) "Konfirmasi penyesuaian" else "Cek saldo ${asset.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Saldo aplikasi: ${Formatters.formatRupiah(asset.currentBalance)}")
                if (!confirming) {
                    OutlinedTextField(value = input, onValueChange = { input = it },
                        label = { Text("Saldo di bank atau uang tunai") }, prefix = { Text("Rp ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = if (input.startsWith("-")) androidx.compose.ui.text.input.VisualTransformation.None else ThousandSeparatorVisualTransformation(), singleLine = true,
                        isError = input.isNotEmpty() && difference == null,
                        supportingText = { if (input.isNotEmpty() && difference == null) Text("Masukkan saldo dalam rupiah bulat yang valid.") })
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Alasan selisih (opsional)") })
                }
                if (difference != null) {
                    Text(if (difference == 0L) "Saldo sudah cocok. Tidak ada penyesuaian."
                        else "Saldo nyata ${if (difference > 0) "lebih tinggi" else "lebih rendah"} ${Formatters.formatRupiah(kotlin.math.abs(difference))}.")
                    if (difference != 0L) Text("Penyesuaian dicatat di mutasi, tanpa dihitung sebagai pemasukan atau pengeluaran. Periksa dahulu apakah ada transaksi yang belum dicatat.")
                }
                if (confirming) Text("Saldo akan menjadi ${Formatters.formatRupiah(target!!)}. Simpan penyesuaian ini?")
            }
        },
        confirmButton = {
            TextButton(enabled = difference != null && !saving, onClick = {
                when {
                    difference == 0L -> onDismiss()
                    !confirming -> confirming = true
                    else -> {
                        saving = true
                        onAdjust(target!!, note.ifBlank { "Pencocokan saldo ${asset.name}" }) { success ->
                            saving = false
                            if (success) onDismiss()
                        }
                    }
                }
            }) { Text(if (saving) "Menyimpan…" else if (difference == 0L) "Selesai" else if (confirming) "Simpan penyesuaian" else "Tinjau penyesuaian") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { if (confirming) confirming = false else onDismiss() }) { Text(if (confirming) "Kembali" else "Batal") } }
    )
}
