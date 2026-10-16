package com.cashflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

/**
 * Debug-Only Internal Design System & Typography Preview Screen
 * Demonstrates all tokens, Plus Jakarta Sans typography, tabular currency figures,
 * and standard components in Light & Dark mode and 100% / 130% font scale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSystemPreviewDialog(
    onDismiss: () -> Unit
) {
    var isDarkPreview by remember { mutableStateOf(false) }
    var fontScale130 by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val currentDensity = LocalDensity.current
        val effectiveDensity = remember(currentDensity, fontScale130) {
            Density(
                density = currentDensity.density,
                fontScale = if (fontScale130) 1.30f else 1.0f
            )
        }

        CashflowTheme(darkTheme = isDarkPreview) {
            val duitColors = MaterialTheme.duit
            CompositionLocalProvider(
                LocalDensity provides effectiveDensity
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = duitColors.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        // Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Kembali",
                                    tint = duitColors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.xs))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Design System Preview",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = duitColors.textPrimary
                                )
                                Text(
                                    text = "Plus Jakarta Sans • Mono & Lime",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = duitColors.textSecondary
                                )
                            }
                        }

                        // Mode Controls Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dark Mode Toggle Pill
                            FilterChip(
                                selected = isDarkPreview,
                                onClick = { isDarkPreview = !isDarkPreview },
                                label = {
                                    Text(
                                        text = if (isDarkPreview) "Mode Gelap" else "Mode Terang",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDarkPreview) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            // 130% Font Scale Toggle Pill
                            FilterChip(
                                selected = fontScale130,
                                onClick = { fontScale130 = !fontScale130 },
                                label = {
                                    Text(
                                        text = if (fontScale130) "Skala Font: 130%" else "Skala Font: 100%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }

                        HorizontalDivider(color = duitColors.border)

                    // Scrollable Showcase Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xl)
                    ) {
                        // Section 1: Typography Scale
                        PreviewSection(title = "1. SKALA TIPOGRAFI (PLUS JAKARTA SANS)") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = duitColors.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, duitColors.border),
                                shape = RoundedCornerShape(AppRadius.card)
                            ) {
                                Column(
                                    modifier = Modifier.padding(AppSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                                ) {
                                    Text(
                                        text = "Display Large 36sp Bold",
                                        style = MaterialTheme.typography.displayLarge,
                                        color = duitColors.textPrimary
                                    )
                                    Text(
                                        text = "Display Medium 28sp Bold",
                                        style = MaterialTheme.typography.displayMedium,
                                        color = duitColors.textPrimary
                                    )
                                    Text(
                                        text = "Headline Large 22sp Bold",
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = duitColors.textPrimary
                                    )
                                    Text(
                                        text = "Title Large 16sp SemiBold",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = duitColors.textPrimary
                                    )
                                    Text(
                                        text = "Body Large 15sp Normal: Mengelola keuangan pribadi dengan presisi tinggi dan tenang.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = duitColors.textPrimary
                                    )
                                    Text(
                                        text = "Body Medium 14sp Normal: Batas minimum teks konten adalah 14sp untuk kenyamanan baca.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = duitColors.textSecondary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                        Text(
                                            text = "Label Large 14sp",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = duitColors.textPrimary
                                        )
                                        Text(
                                            text = "Label Medium 13sp",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = duitColors.textSecondary
                                        )
                                        Text(
                                            text = "Label Small 12sp (Min)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = duitColors.textTertiary
                                        )
                                    }
                                }
                            }
                        }

                        // Section 2: Tabular Numbers (tnum) for Money
                        PreviewSection(title = "2. TABULAR NUMBERS (tnum) ANGKA UANG") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = duitColors.hero),
                                shape = RoundedCornerShape(AppRadius.card)
                            ) {
                                Column(
                                    modifier = Modifier.padding(AppSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                                ) {
                                    Text(
                                        text = "Saldo Bersih (currencyDisplay 32sp tnum):",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = duitColors.onHeroSecondary
                                    )
                                    Text(
                                        text = "Rp 128.450.000",
                                        style = MaterialTheme.typography.currencyDisplay,
                                        color = duitColors.onHero
                                    )
                                    Text(
                                        text = "Rp 001.234.567 (Angka lurus rata sempurna)",
                                        style = MaterialTheme.typography.currencyHeadline,
                                        color = duitColors.heroAccent
                                    )
                                }
                            }
                        }

                        // Section 3: Color Tokens (Mono dan Lime)
                        PreviewSection(title = "3. PALET WARNA (MONO DAN LIME)") {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                                ) {
                                    ColorChip(name = "Primary", bg = duitColors.primary, fg = duitColors.onPrimary, modifier = Modifier.weight(1f))
                                    ColorChip(name = "Accent Lime", bg = duitColors.accentLime, fg = duitColors.onAccentLime, modifier = Modifier.weight(1f))
                                    ColorChip(name = "Hero", bg = duitColors.hero, fg = duitColors.onHero, modifier = Modifier.weight(1f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                                ) {
                                    ColorChip(name = "Income", bg = duitColors.incomeContainer, fg = duitColors.income, modifier = Modifier.weight(1f))
                                    ColorChip(name = "Expense", bg = duitColors.expenseContainer, fg = duitColors.expense, modifier = Modifier.weight(1f))
                                    ColorChip(name = "Transfer", bg = duitColors.transferContainer, fg = duitColors.transfer, modifier = Modifier.weight(1f))
                                    ColorChip(name = "Warning", bg = duitColors.warningContainer, fg = duitColors.warning, modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // Section 4: Basic Components
                        PreviewSection(title = "4. STANDARISASI KOMPONEN DASAR") {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                                // Buttons
                                Text(
                                    text = "DuitAingButton (Tinggi 48dp terstandarisasi):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = duitColors.textPrimary
                                )
                                DuitAingButton(
                                    text = "Primary Button (Mono)",
                                    onClick = {},
                                    leadingIcon = Icons.Rounded.Add,
                                    trailingIcon = Icons.Rounded.ArrowForward,
                                    modifier = Modifier.fillMaxWidth(),
                                    variant = DuitAingButtonVariant.PRIMARY
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                                ) {
                                    DuitAingButton(
                                        text = "Secondary",
                                        onClick = {},
                                        modifier = Modifier.weight(1f),
                                        variant = DuitAingButtonVariant.SECONDARY
                                    )
                                    DuitAingButton(
                                        text = "Danger Action",
                                        onClick = {},
                                        modifier = Modifier.weight(1f),
                                        variant = DuitAingButtonVariant.DESTRUCTIVE
                                    )
                                }

                                // Badges & Tags
                                Text(
                                    text = "AssetBadge (Normal case, 12sp, padding 8x4dp):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = duitColors.textPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    AssetBadge(text = "BCA Tabungan")
                                    AssetBadge(text = "Mandiri Giro")
                                    AssetBadge(text = "Dompet Tunai")
                                }

                                // Eyebrows
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    EyebrowTag(text = "Default", variant = EyebrowVariant.DEFAULT)
                                    EyebrowTag(text = "Pemasukan", variant = EyebrowVariant.POSITIVE)
                                    EyebrowTag(text = "Pengeluaran", variant = EyebrowVariant.NEGATIVE)
                                    EyebrowTag(text = "Aksen", variant = EyebrowVariant.PRIMARY)
                                }

                                // Double-Bezel Card
                                Text(
                                    text = "DoubleBezelCard (Concentric enclosure machined hardware):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = duitColors.textPrimary
                                )
                                DoubleBezelCard(
                                    innerColor = duitColors.surface,
                                    innerBorderColor = duitColors.border
                                ) {
                                    Column(modifier = Modifier.padding(AppSpacing.md)) {
                                        Text(
                                            text = "Kartu Saldo Double-Bezel",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = duitColors.textPrimary
                                        )
                                        Text(
                                            text = "Radius 24dp (outer) / 20dp (inner) presisi skala.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = duitColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.xxl))
                    }
                }
            }
        }
    }
}
}

@Composable
private fun PreviewSection(
    title: String,
    content: @Composable () -> Unit
) {
    val duit = MaterialTheme.duit
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = duit.textSecondary
        )
        content()
    }
}

@Composable
private fun ColorChip(
    name: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(bg)
            .border(1.dp, MaterialTheme.duit.borderStrong.copy(alpha = 0.3f), RoundedCornerShape(AppRadius.sm))
            .padding(vertical = AppSpacing.sm, horizontal = AppSpacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1
        )
    }
}
