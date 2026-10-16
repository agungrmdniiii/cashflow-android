package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.repository.BalanceDiscrepancy
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.DuitAingModalBottomSheet
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.ui.theme.duit

/**
 * Layar "Cek Integritas Saldo" di Pengaturan > Diagnostik.
 * Menampilkan audit konsistensi saldo per aset secara mendalam:
 * - Saldo tersimpan vs Saldo hasil hitung mutasi jurnal
 * - Status selisih per aset (Hijau/Lime jika Rp 0, Merah/Expense jika selisih)
 * - Tombol "Periksa Ulang" (read-only verification)
 * - Tombol "Perbaiki" dengan dialog konfirmasi & backup pengaman otomatis
 *
 * Sesuai pedoman:
 * - Font >= 12sp di semua elemen teks
 * - Tanpa warna hardcode (100% menggunakan token tema MaterialTheme.duit)
 */
@Composable
fun BalanceIntegritySheet(
    viewModel: CashflowViewModel,
    onDismiss: () -> Unit
) {
    val duit = MaterialTheme.duit
    val integrityItems by viewModel.integrityReportItems.collectAsState()
    val discrepancies by viewModel.integrityDiscrepancies.collectAsState()
    val isChecking by viewModel.isCheckingIntegrity.collectAsState()

    var showConfirmRepairDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (integrityItems.isEmpty()) {
            viewModel.refreshIntegrityCheck()
        }
    }

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Sheet
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(duit.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = duit.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Cek Integritas Saldo",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = duit.textPrimary
                        )
                        Text(
                            text = "Verifikasi saldo aset terhadap jurnal transaksi",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = duit.textSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup",
                        tint = duit.textSecondary
                    )
                }
            }

            // 2. Status Banner
            val hasDiscrepancy = discrepancies.isNotEmpty()
            val bannerBg = if (hasDiscrepancy) duit.expense.copy(alpha = 0.12f) else duit.income.copy(alpha = 0.12f)
            val bannerBorder = if (hasDiscrepancy) duit.expense else duit.income
            val bannerIcon = if (hasDiscrepancy) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle
            val bannerTint = if (hasDiscrepancy) duit.expense else duit.income

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bannerBg)
                    .border(1.dp, bannerBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = bannerIcon,
                    contentDescription = null,
                    tint = bannerTint,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasDiscrepancy) {
                            "Terdeteksi ${discrepancies.size} Aset Tidak Sinkron"
                        } else {
                            "Semua Saldo Aset Sinkron"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = duit.textPrimary
                    )
                    Text(
                        text = if (hasDiscrepancy) {
                            "Ditemukan perbedaan antara saldo tersimpan dan akumulasi mutasi transaksi. Klik 'Perbaiki Saldo' untuk menyinkronkan."
                        } else {
                            "Saldo tersimpan di semua aset identik 100% dengan total saldo awal + pemasukan - pengeluaran ± transfer/penyesuaian."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = duit.textSecondary
                    )
                }
            }

            // 3. Tabel Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(duit.surfaceVariant)
                    .border(1.dp, duit.border, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ASET",
                    modifier = Modifier.weight(1.1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = duit.textSecondary
                )
                Text(
                    text = "TERSIMPAN",
                    modifier = Modifier.weight(1.1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = duit.textSecondary
                )
                Text(
                    text = "DIHITUNG",
                    modifier = Modifier.weight(1.1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = duit.textSecondary
                )
                Text(
                    text = "SELISIH",
                    modifier = Modifier.weight(1.0f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = duit.textSecondary
                )
            }

            // 4. Tabel Daftar Aset
            if (integrityItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            color = duit.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text(
                            text = "Belum ada aset terdaftar dalam basis data.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = duit.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .border(1.dp, duit.border, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
                    verticalArrangement = Arrangement.Top
                ) {
                    items(integrityItems, key = { it.assetId }) { item ->
                        IntegrityAssetRow(item = item)
                        HorizontalDivider(color = duit.border, thickness = 0.5.dp)
                    }
                }
            }

            // 5. Tombol Aksi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DuitAingButton(
                    text = if (isChecking) "MEMERIKSA..." else "PERIKSA ULANG",
                    onClick = { viewModel.refreshIntegrityCheck() },
                    variant = DuitAingButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                    height = 46.dp
                )

                DuitAingButton(
                    text = "PERBAIKI SALDO",
                    onClick = { showConfirmRepairDialog = true },
                    variant = DuitAingButtonVariant.PRIMARY,
                    enabled = hasDiscrepancy && !isChecking,
                    modifier = Modifier.weight(1f),
                    height = 46.dp
                )
            }
        }
    }

    // Dialog Konfirmasi Perbaikan Saldo
    if (showConfirmRepairDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRepairDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BuildCircle,
                        contentDescription = null,
                        tint = duit.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Konfirmasi Perbaikan Saldo",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = duit.textPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sistem akan menyesuaikan saldo tersimpan agar sesuai dengan hasil hitung jurnal transaksi:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        color = duit.textPrimary
                    )

                    // Daftar perubahan per aset
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(duit.surfaceVariant)
                            .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        discrepancies.forEach { disc ->
                            Column {
                                Text(
                                    text = disc.assetName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = duit.textPrimary
                                )
                                Text(
                                    text = "${Formatters.formatRupiah(disc.recordedBalance)} → ${Formatters.formatRupiah(disc.calculatedBalance)} (Selisih: ${if (disc.difference > 0) "+" else ""}${Formatters.formatRupiah(disc.difference)})",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = duit.expense
                                )
                            }
                        }
                    }

                    // Pemberitahuan Backup Pengaman
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(duit.surfaceVariant)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = duit.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Backup pengaman otomatis (.json) akan dibuat sebelum perubahan diterapkan.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = duit.textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.repairBalanceDiscrepancies {
                            showConfirmRepairDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = duit.primary,
                        contentColor = duit.onPrimary
                    )
                ) {
                    Text(
                        text = "TERAPKAN PERBAIKAN",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRepairDialog = false }) {
                    Text(
                        text = "BATAL",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = duit.textSecondary
                    )
                }
            }
        )
    }
}

@Composable
private fun IntegrityAssetRow(
    item: BalanceDiscrepancy,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit
    val isSync = item.difference == 0L

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSync) duit.surface else duit.expense.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nama Aset
        Text(
            text = item.assetName,
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = duit.textPrimary,
            maxLines = 1
        )

        // Saldo Tersimpan
        Text(
            text = Formatters.formatRupiah(item.recordedBalance),
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp
            ),
            color = duit.textSecondary,
            maxLines = 1
        )

        // Saldo Dihitung
        Text(
            text = Formatters.formatRupiah(item.calculatedBalance),
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = duit.textPrimary,
            maxLines = 1
        )

        // Selisih
        Text(
            text = if (isSync) {
                "0 (Cocok)"
            } else {
                val sign = if (item.difference > 0) "+" else ""
                "$sign${Formatters.formatCompact(item.difference)}"
            },
            modifier = Modifier.weight(1.0f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = if (isSync) duit.income else duit.expense,
            maxLines = 1
        )
    }
}
