package com.cashflow.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.theme.*

enum class ExportFormat {
    PDF,
    CSV
}

/**
 * Standardized Export Dialog for "Duit Aing"
 * Offers flexible export formats (Clean PDF Document vs Spreadsheet CSV)
 * with customizable data scope (Filtered Period vs All-Time).
 */
@Composable
fun ExportDataDialog(
    isOpen: Boolean,
    viewModel: CashflowViewModel,
    hasActiveFilter: Boolean = false,
    filterName: String = "Periode Aktif",
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var exportFilteredOnly by remember(hasActiveFilter) { mutableStateOf(hasActiveFilter) }
    var isExporting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = transitionState,
            enter = scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(220)) +
                    slideInVertically(
                        initialOffsetY = { 60 },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    ),
            exit = scaleOut(targetScale = 0.92f, animationSpec = tween(180)) +
                    fadeOut(animationSpec = tween(180))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .border(1.5.dp, DarkBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PebbleSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "EKSPOR KAS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pilih format dokumen yang Anda butuhkan",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { if (!isExporting) onDismiss() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Format Option 1: PDF Document (Neat, formatted, ready-to-open)
                val isPdf = selectedFormat == ExportFormat.PDF
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPdf) JadePrimaryContainer.copy(alpha = 0.45f) else PebbleSurfaceVariant.copy(alpha = 0.35f))
                        .border(
                            width = if (isPdf) 1.5.dp else 1.dp,
                            color = if (isPdf) JadePrimary else DarkBorder.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedFormat = ExportFormat.PDF }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isPdf) JadePrimary else PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = if (isPdf) PebbleSurface else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dokumen PDF (.pdf)",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(JadePrimary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "RAPI & SIAP BUKA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 8.5.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = PebbleSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Laporan tertata resmi dengan tabel, warna status, dan ringkasan kas siap cetak/buka langsung.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                ),
                                color = TextSecondary
                            )
                        }

                        RadioButton(
                            selected = isPdf,
                            onClick = { selectedFormat = ExportFormat.PDF },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = JadePrimary,
                                unselectedColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Format Option 2: CSV Spreadsheet
                val isCsv = selectedFormat == ExportFormat.CSV
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCsv) JadePrimaryContainer.copy(alpha = 0.45f) else PebbleSurfaceVariant.copy(alpha = 0.35f))
                        .border(
                            width = if (isCsv) 1.5.dp else 1.dp,
                            color = if (isCsv) JadePrimary else DarkBorder.copy(alpha = 0.20f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedFormat = ExportFormat.CSV }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isCsv) JadePrimary else PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TableChart,
                                contentDescription = null,
                                tint = if (isCsv) PebbleSurface else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Spreadsheet CSV (.csv)",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Data baris & kolom mentah berstandar UTF-8 untuk diolah di Microsoft Excel / Google Sheets.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                ),
                                color = TextSecondary
                            )
                        }

                        RadioButton(
                            selected = isCsv,
                            onClick = { selectedFormat = ExportFormat.CSV },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = JadePrimary,
                                unselectedColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scope Selector
                Text(
                    text = "JANGKAUAN DATA:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.5.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Scope 1: Semua Data
                    val isAllSelected = !exportFilteredOnly
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAllSelected) JadePrimary else PebbleSurfaceVariant)
                            .border(1.dp, if (isAllSelected) DarkBorder else DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                            .clickable { exportFilteredOnly = false }
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SEMUA RIWAYAT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (isAllSelected) PebbleSurface else TextPrimary
                        )
                    }

                    // Scope 2: Filter Aktif
                    val isFilteredSelected = exportFilteredOnly
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFilteredSelected) JadePrimary else PebbleSurfaceVariant)
                            .border(1.dp, if (isFilteredSelected) DarkBorder else DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                            .clickable { exportFilteredOnly = true }
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filterName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (isFilteredSelected) PebbleSurface else TextPrimary,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: BATAL + EKSPOR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DuitAingButton(
                        text = "BATAL",
                        onClick = onDismiss,
                        variant = DuitAingButtonVariant.OUTLINE,
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                        shadowOffset = 2.dp
                    )

                    DuitAingButton(
                        text = if (selectedFormat == ExportFormat.PDF) "EKSPOR PDF" else "EKSPOR CSV",
                        onClick = {
                            isExporting = true
                            if (selectedFormat == ExportFormat.PDF) {
                                viewModel.exportPdfFile(
                                    context = context,
                                    filteredOnly = exportFilteredOnly,
                                    onSuccess = { file, sendIntent ->
                                        isExporting = false
                                        onDismiss()
                                        try {
                                            context.startActivity(Intent.createChooser(sendIntent, "Buka / Bagikan Dokumen PDF"))
                                        } catch (_: Exception) {}
                                        viewModel.showSnackbar("Dokumen PDF berhasil dibuat: ${file.name}")
                                    },
                                    onError = { err ->
                                        isExporting = false
                                        viewModel.showSnackbar("Gagal membuat PDF: $err")
                                    }
                                )
                            } else {
                                viewModel.exportCsvFile(
                                    context = context,
                                    filteredOnly = exportFilteredOnly,
                                    onSuccess = { file, sendIntent, content ->
                                        isExporting = false
                                        onDismiss()
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Cashflow Export CSV", content)
                                        clipboard.setPrimaryClip(clip)
                                        try {
                                            context.startActivity(Intent.createChooser(sendIntent, "Bagikan / Simpan File CSV"))
                                        } catch (_: Exception) {}
                                        viewModel.showSnackbar("File CSV berhasil dibuat: ${file.name}")
                                    },
                                    onError = { err ->
                                        isExporting = false
                                        viewModel.showSnackbar("Gagal membuat CSV: $err")
                                    }
                                )
                            }
                        },
                        variant = DuitAingButtonVariant.PRIMARY,
                        leadingIcon = Icons.Rounded.Download,
                        isLoading = isExporting,
                        modifier = Modifier.weight(1.4f),
                        height = 44.dp,
                        shadowOffset = 2.dp
                    )
                }
            }
        }
    }
}
}
