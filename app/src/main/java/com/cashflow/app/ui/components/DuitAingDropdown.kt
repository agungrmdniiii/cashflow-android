package com.cashflow.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.AssetType
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.PeriodFilter
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Clean & tactile Asset Type Icon Box
 */
@Composable
fun AssetTypeIcon(
    type: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    backgroundColor: Color = JadePrimaryLight,
    tintColor: Color = JadePrimary
) {
    val vector: ImageVector = when (type.lowercase()) {
        "tunai" -> Icons.Rounded.LocalAtm
        "bank" -> Icons.Rounded.AccountBalance
        "e-wallet", "e-wallets", "ewallet" -> Icons.Rounded.PhoneAndroid
        "tabungan" -> Icons.Rounded.Savings
        "kartu kredit" -> Icons.Rounded.CreditCard
        else -> Icons.Rounded.AccountBalanceWallet
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Global Bottom Sheet Modal Container for "Duit Aing" Selection System
 *
 * Implements:
 * - Scrim dimming backdrop with touch dismiss
 * - Neo-brutalist top card with 1.5dp DarkBorder and 16dp rounded corners
 * - Tactile drag handle
 * - Bold title + subtitle + close button
 * - Optional realtime search field
 * - Adaptive compact height (doesn't waste 80% whitespace)
 */
@Composable
fun DuitAingSelectionSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    searchPlaceholder: String = "Cari pilihan...",
    content: @Composable () -> Unit
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurface.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent click-through */ }
                    .border(
                        BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = PebbleSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    // 1. Drag Handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(DarkBorder.copy(alpha = 0.20f))
                        )
                    }

                    // 2. Title & Subtitle + Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = title.uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                ),
                                color = DarkSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = TextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClick = onDismissRequest
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Tutup",
                                    tint = DarkSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 1.dp, color = PebbleBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Optional Search Field
                    if (onSearchQueryChange != null && searchQuery != null) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = searchPlaceholder,
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = DarkSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Hapus",
                                            tint = DarkSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PebbleSurfaceVariant,
                                unfocusedContainerColor = PebbleSurfaceVariant,
                                focusedBorderColor = JadePrimary,
                                unfocusedBorderColor = DarkBorder.copy(alpha = 0.20f)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 4. Content Area with Max Height
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Global Bottom Sheet Category Selector
 */
@Composable
fun CategoryDropdownField(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "PILIH KATEGORI"
) {
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val currentCat = categories.firstOrNull { it.id == selectedCategoryId }
    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = modifier) {
        // Trigger Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PebbleSurface)
                .border(1.dp, DarkBorder.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) {
                    searchQuery = ""
                    showSheet = true
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (currentCat != null) {
                        CategoryIcon(
                            iconName = currentCat.icon,
                            size = 32.dp,
                            iconSize = 16.dp,
                            backgroundColor = JadePrimaryLight
                        )
                        Text(
                            text = currentCat.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = DarkSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Category,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PebbleSurfaceVariant)
                            .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PILIH",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = DarkSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = DarkSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Bottom Sheet
    DuitAingSelectionSheet(
        visible = showSheet,
        onDismissRequest = { showSheet = false },
        title = "PILIH KATEGORI",
        subtitle = "Pilih kategori untuk transaksi ini",
        searchQuery = if (categories.size > 5) searchQuery else null,
        onSearchQueryChange = if (categories.size > 5) { { searchQuery = it } } else null,
        searchPlaceholder = "Cari kategori..."
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filteredCategories, key = { it.id }) { cat ->
                val isSelected = cat.id == selectedCategoryId
                val bg = if (isSelected) JadePrimaryLight.copy(alpha = 0.45f) else PebbleSurface
                val borderCol = if (isSelected) JadePrimary else DarkBorder.copy(alpha = 0.25f)
                val textCol = if (isSelected) JadePrimary else DarkSurface

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            onCategorySelected(cat)
                            showSheet = false
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            CategoryIcon(
                                iconName = cat.icon,
                                size = 36.dp,
                                iconSize = 18.dp,
                                backgroundColor = if (isSelected) JadePrimaryContainer else PebbleSurfaceVariant
                            )
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = textCol
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(JadePrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Terpilih",
                                    tint = TextOnDark,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Global Bottom Sheet Asset Selector with Balance Pills
 */
@Composable
fun AssetDropdownField(
    assets: List<Asset>,
    selectedAssetId: String,
    onAssetSelected: (Asset) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "PILIH ASET / REKENING"
) {
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val currentAsset = assets.firstOrNull { it.id == selectedAssetId }
    val filteredAssets = remember(assets, searchQuery) {
        if (searchQuery.isBlank()) assets
        else assets.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.type.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = modifier) {
        // Trigger Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PebbleSurface)
                .border(1.dp, DarkBorder.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) {
                    searchQuery = ""
                    showSheet = true
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (currentAsset != null) {
                        AssetTypeIcon(
                            type = currentAsset.type,
                            size = 32.dp,
                            iconSize = 16.dp,
                            backgroundColor = JadePrimaryLight
                        )
                        Column {
                            Text(
                                text = currentAsset.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = DarkSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = currentAsset.type.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = TextSecondary
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentAsset != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = Formatters.formatRupiah(currentAsset.currentBalance),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = DarkSurface
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = DarkSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Bottom Sheet
    DuitAingSelectionSheet(
        visible = showSheet,
        onDismissRequest = { showSheet = false },
        title = "PILIH ASET / REKENING",
        subtitle = "Pilih sumber dana atau rekening yang digunakan",
        searchQuery = if (assets.size > 3) searchQuery else null,
        onSearchQueryChange = if (assets.size > 3) { { searchQuery = it } } else null,
        searchPlaceholder = "Cari nama aset..."
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filteredAssets, key = { it.id }) { ast ->
                val isSelected = ast.id == selectedAssetId
                val bg = if (isSelected) JadePrimaryLight.copy(alpha = 0.45f) else PebbleSurface
                val borderCol = if (isSelected) JadePrimary else DarkBorder.copy(alpha = 0.25f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            onAssetSelected(ast)
                            showSheet = false
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            AssetTypeIcon(
                                type = ast.type,
                                size = 38.dp,
                                iconSize = 20.dp,
                                backgroundColor = if (isSelected) JadePrimaryContainer else PebbleSurfaceVariant,
                                tintColor = if (isSelected) JadePrimary else DarkSurface
                            )
                            Column {
                                Text(
                                    text = ast.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = DarkSurface
                                )
                                Text(
                                    text = ast.type.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = Formatters.formatRupiah(ast.currentBalance),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    ),
                                    color = DarkSurface
                                )
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(JadePrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Terpilih",
                                        tint = TextOnDark,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Global Bottom Sheet Asset Type Selector (Tunai, Bank, E-Wallet, etc.)
 */
@Composable
fun AssetTypeDropdownField(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    val options = listOf(
        AssetType.BANK.displayName to "Rekening bank tabungan / giro",
        AssetType.EWALLET.displayName to "GoPay, OVO, Dana, ShopeePay, dll.",
        AssetType.TUNAI.displayName to "Uang tunai / fisik di dompet",
        AssetType.TABUNGAN.displayName to "Simpanan atau tabungan khusus",
        AssetType.KARTU_KREDIT.displayName to "Kartu kredit dengan limit belanja",
        AssetType.LAINNYA.displayName to "Instrumen keuangan atau aset lain"
    )

    Box(modifier = modifier) {
        // Trigger Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PebbleSurface)
                .border(1.dp, DarkBorder.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { showSheet = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    AssetTypeIcon(
                        type = selectedType,
                        size = 32.dp,
                        iconSize = 16.dp,
                        backgroundColor = JadePrimaryLight
                    )
                    Text(
                        text = selectedType,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = DarkSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PebbleSurfaceVariant)
                            .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "GANTI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = DarkSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = DarkSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Bottom Sheet
    DuitAingSelectionSheet(
        visible = showSheet,
        onDismissRequest = { showSheet = false },
        title = "PILIH JENIS ASET",
        subtitle = "Tentukan jenis rekening atau instrumen penyimpanan"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { (type, description) ->
                val isSelected = type.equals(selectedType, ignoreCase = true)
                val bg = if (isSelected) JadePrimaryLight.copy(alpha = 0.45f) else PebbleSurface
                val borderCol = if (isSelected) JadePrimary else DarkBorder.copy(alpha = 0.25f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            onTypeSelected(type)
                            showSheet = false
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            AssetTypeIcon(
                                type = type,
                                size = 36.dp,
                                iconSize = 18.dp,
                                backgroundColor = if (isSelected) JadePrimaryContainer else PebbleSurfaceVariant,
                                tintColor = if (isSelected) JadePrimary else DarkSurface
                            )
                            Column {
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = DarkSurface
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp
                                    ),
                                    color = TextSecondary
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(JadePrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Terpilih",
                                    tint = TextOnDark,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Global Polished Neo-Brutalist Transaction Type Segmented Control
 */
@Composable
fun TransactionTypeSegmentedControl(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val types: List<Pair<TransactionType, Pair<String, ImageVector>>> = listOf(
        TransactionType.EXPENSE to ("PENGELUARAN" to Icons.Rounded.NorthEast),
        TransactionType.INCOME to ("PEMASUKAN" to Icons.Rounded.SouthWest),
        TransactionType.TRANSFER to ("TRANSFER" to Icons.Rounded.SwapHoriz)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PebbleSurfaceVariant)
            .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            types.forEach { (type, pair) ->
                val (label, icon) = pair
                val isSelected = selectedType == type

                val targetBg = when {
                    !isSelected -> Color.Transparent
                    type == TransactionType.INCOME -> JadePrimary
                    type == TransactionType.TRANSFER -> StatusTransfer
                    else -> StatusNegative
                }
                val bgColor by animateColorAsState(
                    targetValue = targetBg,
                    animationSpec = tween(durationMillis = 150),
                    label = "TxTypeBg"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .border(
                            if (isSelected) 1.dp else 0.dp,
                            if (isSelected) DarkBorder.copy(alpha = 0.25f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(role = Role.Tab) { onTypeSelected(type) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) TextOnDark else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontSize = 11.5.sp
                            ),
                            color = if (isSelected) TextOnDark else TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Global Polished Date Picker Trigger Field + Calendar Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuitAingDatePickerField(
    dateIsoString: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "TANGGAL TRANSAKSI"
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val formattedDateDisplay = remember(dateIsoString) {
        try {
            val parsed = LocalDate.parse(dateIsoString)
            val dayOfWeek = when (parsed.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "Senin"
                java.time.DayOfWeek.TUESDAY -> "Selasa"
                java.time.DayOfWeek.WEDNESDAY -> "Rabu"
                java.time.DayOfWeek.THURSDAY -> "Kamis"
                java.time.DayOfWeek.FRIDAY -> "Jumat"
                java.time.DayOfWeek.SATURDAY -> "Sabtu"
                java.time.DayOfWeek.SUNDAY -> "Minggu"
            }
            val month = when (parsed.monthValue) {
                1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "Mei"; 6 -> "Jun"
                7 -> "Jul"; 8 -> "Agu"; 9 -> "Sep"; 10 -> "Okt"; 11 -> "Nov"; 12 -> "Des"
                else -> ""
            }
            "$dayOfWeek, ${parsed.dayOfMonth} $month ${parsed.year}"
        } catch (_: Exception) {
            dateIsoString
        }
    }

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PebbleSurface)
                .border(1.dp, DarkBorder.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { showDatePicker = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(JadePrimaryLight)
                            .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = JadePrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Text(
                        text = formattedDateDisplay,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = DarkSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PebbleSurfaceVariant)
                        .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "UBAH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = DarkSurface
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val initialEpochMillis = remember(dateIsoString) {
            try {
                val parsed = LocalDate.parse(dateIsoString)
                parsed.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEpochMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val localDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.of("UTC"))
                                .toLocalDate()
                            onDateSelected(localDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("PILIH", fontWeight = FontWeight.ExtraBold, color = JadePrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("BATAL", fontWeight = FontWeight.Bold, color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = PebbleSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = PebbleSurface,
                    titleContentColor = DarkSurface,
                    headlineContentColor = DarkSurface,
                    selectedDayContainerColor = JadePrimary,
                    selectedDayContentColor = TextOnDark,
                    todayDateBorderColor = JadePrimary,
                    todayContentColor = JadePrimary,
                    dayContentColor = DarkSurface
                )
            )
        }
    }
}

/**
 * Global Bottom Sheet for Period Selection
 */
@Composable
fun PeriodSelectionSheet(
    visible: Boolean,
    selectedPeriod: PeriodFilter,
    onPeriodSelected: (PeriodFilter) -> Unit,
    onDismissRequest: () -> Unit
) {
    DuitAingSelectionSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = "PILIH PERIODE",
        subtitle = "Tampilkan ringkasan keuangan berdasarkan rentang waktu"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodFilter.entries.forEach { period ->
                val isSelected = period == selectedPeriod
                val bg = if (isSelected) JadePrimaryLight.copy(alpha = 0.45f) else PebbleSurface
                val borderCol = if (isSelected) JadePrimary else DarkBorder.copy(alpha = 0.25f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            onPeriodSelected(period)
                            onDismissRequest()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) JadePrimaryContainer else PebbleSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DateRange,
                                    contentDescription = null,
                                    tint = if (isSelected) JadePrimary else DarkSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = period.displayName.uppercase(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = if (isSelected) JadePrimary else DarkSurface
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(JadePrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Terpilih",
                                    tint = TextOnDark,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
