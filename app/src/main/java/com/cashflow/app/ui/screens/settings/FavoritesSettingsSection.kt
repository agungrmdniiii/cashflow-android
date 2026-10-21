package com.cashflow.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.FavoriteTemplate
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.components.DuitAingButton
import com.cashflow.app.ui.components.DuitAingButtonVariant
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.ui.theme.AppSpacing
import com.cashflow.app.ui.theme.duit

/**
 * Section Transaksi Favorit & Berulang:
 * Pengaturan simpan langsung 1-tap, daftar templat favorit, dan navigasi transaksi berulang.
 */
@Composable
fun FavoritesSettingsSection(
    favoriteTemplates: List<FavoriteTemplate>,
    isFavoriteDirectSave: Boolean,
    currentAssets: List<Asset>,
    categories: List<Category>,
    recurringCount: Int,
    onToggleFavoriteDirectSave: (Boolean) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onOpenRecurring: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit

    val assetMap = remember(currentAssets) { currentAssets.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "settings", contentType = "settings") {
            SettingsSectionCard(
                title = "Pintasan Transaksi",
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: 1-Tap Save Mode
                    SettingsItemRow(
                        title = "Simpan Langsung (1-Tap)",
                        description = if (isFavoriteDirectSave) "Tap favorit langsung simpan transaksi"
                        else "Tap favorit membuka form untuk dicek/diedit",
                        icon = Icons.Rounded.TouchApp,
                        iconTint = if (isFavoriteDirectSave) duit.primary else duit.textSecondary,
                        iconBackground = if (isFavoriteDirectSave) duit.primaryContainer else duit.surfaceVariant,
                        control = {
                            Switch(
                                checked = isFavoriteDirectSave,
                                onCheckedChange = onToggleFavoriteDirectSave,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = duit.onPrimary,
                                    checkedTrackColor = duit.primary,
                                    uncheckedThumbColor = duit.textSecondary,
                                    uncheckedTrackColor = duit.surfaceVariant
                                )
                            )
                        }
                    )

                    HorizontalDivider(color = duit.border, thickness = 1.dp)

                    // Row 2: Transaksi Berulang
                    SettingsItemRow(
                        title = "Transaksi Berulang",
                        description = "Jadwal tagihan otomatis & cicilan ($recurringCount aktif)",
                        icon = Icons.Rounded.EventRepeat,
                        iconTint = duit.textPrimary,
                        iconBackground = duit.surfaceVariant,
                        control = {
                            DuitAingButton(
                                text = "KELOLA",
                                onClick = onOpenRecurring,
                                variant = DuitAingButtonVariant.SECONDARY,
                                height = 36.dp
                            )
                        }
                    )

                }
            }
        }
        if (favoriteTemplates.isNotEmpty()) {
            item(key = "favorites_title", contentType = "heading") {
                Text("TEMPLAT FAVORIT (${favoriteTemplates.size})", style = MaterialTheme.typography.labelLarge,
                    color = duit.textSecondary)
            }
        }
        items(favoriteTemplates, key = { it.id }, contentType = { "favorite" }) { fav ->
            val asset = assetMap[fav.assetId]
            val cat = categoryMap[fav.categoryId]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(duit.surfaceVariant)
                    .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        val (badgeBg, badgeColor, badgeText) = when (fav.type) {
                            TransactionType.INCOME.value -> Triple(duit.incomeContainer, duit.income, "MASUK")
                            TransactionType.TRANSFER.value -> Triple(duit.transferContainer, duit.transfer, "TRANSFER")
                            else -> Triple(duit.expenseContainer, duit.expense, "KELUAR")
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = badgeColor
                            )
                        }
                        Text(
                            text = fav.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = duit.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${cat?.name ?: "Tanpa Kategori"} • ${asset?.name ?: "Aset"} • ${Formatters.formatRupiah(fav.amount)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = duit.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { onDeleteFavorite(fav.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Hapus Favorit",
                        tint = duit.expense,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
