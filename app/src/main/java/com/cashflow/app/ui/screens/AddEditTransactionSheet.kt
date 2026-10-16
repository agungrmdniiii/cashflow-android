package com.cashflow.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.DebtStatus
import com.cashflow.app.data.model.InputMethod
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.service.ReceiptOcrService
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    viewModel: CashflowViewModel,
    existingTx: Transaction? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier.fillMaxHeight(0.92f)
) {
    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        AddEditTransactionContent(
            viewModel = viewModel,
            existingTx = existingTx,
            initialType = initialType,
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionContent(
    viewModel: CashflowViewModel,
    existingTx: Transaction? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    tourTarget: Int? = null,
    onTourTargetPositioned: (Int, Rect) -> Unit = { _, _ -> }
) {
    val activeAssets by viewModel.activeAssets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val defaultAsset by viewModel.defaultAsset.collectAsState()
    val debts by viewModel.debts.collectAsState()

    val unpaidDebts = remember(debts) {
        debts.filter { it.status != DebtStatus.PAID.key && it.remainingAmount > 0 }
    }

    var selectedType by remember {
        mutableStateOf(
            if (existingTx != null) TransactionType.fromValue(existingTx.type) else initialType
        )
    }

    // Debt repayment state (accessible on EXPENSE transactions)
    var isDebtPayment by remember { mutableStateOf(false) }
    var selectedDebtId by remember { mutableStateOf<String?>(null) }

    var amountText by remember {
        mutableStateOf(existingTx?.amount?.toString() ?: if (tourTarget != null) "20000" else "")
    }
    var descriptionText by remember {
        mutableStateOf(existingTx?.description ?: "")
    }
    val lastUsedCat = remember(selectedType) { viewModel.getLastUsedCategory(selectedType.value) }
    val lastUsedAst = remember(selectedType) { viewModel.getLastUsedAsset(selectedType.value) }
    val lastUsedDestAst = remember(selectedType) { viewModel.getLastUsedDestinationAsset() }

    var selectedCategoryId by remember(selectedType) {
        mutableStateOf(
            existingTx?.categoryId
                ?: lastUsedCat
                ?: ""
        )
    }
    var selectedAssetId by remember(selectedType) {
        mutableStateOf(
            existingTx?.assetId
                ?: (if (lastUsedAst != null && activeAssets.any { it.id == lastUsedAst }) lastUsedAst else null)
                ?: defaultAsset?.id
                ?: activeAssets.firstOrNull()?.id
                ?: ""
        )
    }
    var selectedDestAssetId by remember(selectedType) {
        mutableStateOf(
            existingTx?.destinationAssetId
                ?: (if (lastUsedDestAst != null && activeAssets.any { it.id == lastUsedDestAst }) lastUsedDestAst else null)
                ?: ""
        )
    }
    var dateText by remember {
        mutableStateOf(existingTx?.transactionDate ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var noteText by remember {
        mutableStateOf(existingTx?.note ?: "")
    }

    val formScrollState = rememberScrollState()
    LaunchedEffect(tourTarget) {
        if (tourTarget == 10 || tourTarget == 11) formScrollState.scrollTo(0)
    }

    // Collapsible "Tambah Detail" - open by default only if existing transaction has extra details
    var showMoreDetails by remember {
        mutableStateOf(
            existingTx != null && (
                existingTx.note.isNotBlank() ||
                existingTx.tags.isNotBlank() ||
                existingTx.attachment != null ||
                (existingTx.description.isNotBlank() && existingTx.description != "Pengeluaran" && existingTx.description != "Pemasukan" && existingTx.description != "Transfer")
            )
        )
    }

    var showAllCategoriesDialog by remember { mutableStateOf(false) }

    val amountFocusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val favoriteTemplates by viewModel.favoriteTemplates.collectAsState()

    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val duit = MaterialTheme.duit

    // Dynamic accent color with smooth animated transition
    val targetAccentColor = when (selectedType) {
        TransactionType.EXPENSE -> duit.expense
        TransactionType.INCOME -> duit.income
        TransactionType.TRANSFER -> duit.transfer
        else -> duit.primary
    }
    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(220),
        label = "accentColor"
    )

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Receipt Photo & ML Kit OCR State
    var receiptPhotoPath by remember { mutableStateOf(existingTx?.attachment) }
    var isOcrActive by remember { mutableStateOf(existingTx?.inputMethod == InputMethod.OCR.value) }
    var isScanningReceipt by remember { mutableStateOf(false) }
    var showReceiptSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Multi-tag state
    var selectedTags by remember {
        mutableStateOf(
            existingTx?.tags?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }
    var customTagInput by remember { mutableStateOf("") }
    val suggestedTags = listOf("#Makan", "#Bulanan", "#Keluarga", "#Kantor", "#Liburan", "#Hobi", "#Kesehatan", "#Transport", "#Belanja")

    val processReceiptImage: (Uri) -> Unit = { uri ->
        scope.launch {
            isScanningReceipt = true
            try {
                val savedPath = ReceiptOcrService.saveAndCompressReceipt(context, uri)
                if (savedPath != null) {
                    receiptPhotoPath = savedPath
                    val extracted = ReceiptOcrService.scanReceipt(context, savedPath)
                    val total = extracted.totalAmount ?: 0L
                    if (total > 0L) {
                        amountText = total.toString()
                    }
                    if (!extracted.merchantName.isNullOrBlank()) {
                        descriptionText = extracted.merchantName
                    }
                    if (!extracted.date.isNullOrBlank()) {
                        dateText = extracted.date
                    }
                    isOcrActive = true
                    showMoreDetails = true
                    viewModel.showSnackbar("Struk terbaca! Periksa kembali nominal & detail.")
                }
            } catch (e: Exception) {
                viewModel.showSnackbar("Gagal memindai struk: ${e.message}")
            } finally {
                isScanningReceipt = false
            }
        }
    }

    val receiptPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            processReceiptImage(uri)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            tempCameraUri?.let { uri ->
                processReceiptImage(uri)
            }
        }
    }

    // Filter categories based on selected type
    val relevantCategories = remember(selectedType, categories) {
        val typeStr = if (selectedType == TransactionType.INCOME) "income" else "expense"
        categories.filter { it.type == typeStr && it.isActive }
    }

    // Set initial category if not set
    LaunchedEffect(relevantCategories, selectedType) {
        if (relevantCategories.isNotEmpty() && selectedType != TransactionType.TRANSFER) {
            val exists = relevantCategories.any { it.id == selectedCategoryId }
            if (!exists || selectedCategoryId.isBlank()) {
                val lastUsed = viewModel.getLastUsedCategory(selectedType.value)
                selectedCategoryId = if (lastUsed != null && relevantCategories.any { it.id == lastUsed }) {
                    lastUsed
                } else {
                    relevantCategories.first().id
                }
            }
        }
    }

    // Selected source asset for balance checking
    val currentSourceAsset = remember(selectedAssetId, activeAssets) {
        activeAssets.find { it.id == selectedAssetId }
    }
    val currentDestAsset = remember(selectedDestAssetId, activeAssets) {
        activeAssets.find { it.id == selectedDestAssetId }
    }

    val amount = amountText.toLongOrNull() ?: 0L
    val isInsufficientBalance = selectedType != TransactionType.INCOME &&
            selectedType != TransactionType.ADJUSTMENT &&
            currentSourceAsset != null &&
            amount > currentSourceAsset.currentBalance

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        // Header Row: Clean, breathable Title Case + subtle indicator + sleek close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (existingTx != null) "Edit Transaksi" else "Tambah Transaksi",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = duit.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isDebtPayment -> "Bayar cicilan / hutang"
                        selectedType == TransactionType.EXPENSE -> "Catat pengeluaran dana"
                        selectedType == TransactionType.INCOME -> "Catat pemasukan dana"
                        else -> "Pindah saldo antar rekening"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = duit.textSecondary
                )
            }

            IconButton(
                onClick = { onBack?.invoke() ?: onDismiss() },
                enabled = !isSubmitting,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(duit.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup",
                    tint = duit.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 1. TYPE TABS SEGMENTED CONTROL (Soft Red / Green / Blue-Teal)
        if (existingTx?.debtId == null && existingTx?.goalId == null) TransactionTypeSegmentedControl(
            selectedType = selectedType,
            onTypeSelected = {
                selectedType = it
                if (it != TransactionType.EXPENSE) {
                    isDebtPayment = false
                    selectedDebtId = null
                }
            },
            modifier = Modifier.fillMaxWidth().transactionTourAnchor(10, tourTarget, onTourTargetPositioned)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Scrollable Form Fields with Generous Breathing Room
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(formScrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quick Favorite Templates Row (if any exist)
            if (favoriteTemplates.isNotEmpty() && existingTx == null) {
                FadingHorizontalScrollRow(
                    fadeColor = duit.background,
                    contentPadding = PaddingValues(start = 2.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    favoriteTemplates.take(5).forEach { fav ->
                        OutlinedCard(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                amountText = fav.amount.toString()
                                descriptionText = fav.name
                                selectedType = TransactionType.fromValue(fav.type)
                                selectedCategoryId = fav.categoryId ?: ""
                                selectedAssetId = fav.assetId
                                if (fav.destinationAssetId != null) {
                                    selectedDestAssetId = fav.destinationAssetId
                                }
                                if (fav.note.isNotBlank()) {
                                    noteText = fav.note
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = duit.surface),
                            border = BorderStroke(1.dp, duit.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = duit.warning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${fav.name} • ${Formatters.formatCompact(fav.amount)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 2. HERO NOMINAL SECTION (Centered, Single Display, Formatted Rp, Fast Preset Chips)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(duit.surface)
                    .border(1.dp, duit.border, RoundedCornerShape(16.dp))
                    .transactionTourAnchor(11, tourTarget, onTourTargetPositioned)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Centered large input box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(duit.surfaceVariant.copy(alpha = 0.45f))
                        .border(
                            width = 1.dp,
                            color = if (amountText.isNotEmpty()) accentColor.copy(alpha = 0.6f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            try { amountFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rp ",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = accentColor
                    )
                    BasicTextField(
                        value = amountText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }
                            if (digits.length <= 13) {
                                amountText = digits
                            }
                        },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = duit.textPrimary,
                            letterSpacing = (-0.5).sp,
                            textAlign = TextAlign.Start
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        visualTransformation = ThousandSeparatorVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .defaultMinSize(minWidth = 36.dp)
                            .focusRequester(amountFocusRequester),
                        decorationBox = { innerTextField ->
                            if (amountText.isEmpty()) {
                                Text(
                                    text = "0",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp,
                                        color = duit.textTertiary.copy(alpha = 0.45f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fast Preset Increment Chips with proper edge fade and safe trailing padding
                FadingHorizontalScrollRow(
                    fadeColor = duit.surface,
                    contentPadding = PaddingValues(start = 2.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        10_000L to "+10rb",
                        20_000L to "+20rb",
                        50_000L to "+50rb",
                        100_000L to "+100rb",
                        200_000L to "+200rb",
                        500_000L to "+500rb",
                        1_000_000L to "+1jt"
                    ).forEach { (presetVal, chipText) ->
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(duit.surfaceVariant)
                                .clickable(role = Role.Button) {
                                    val cur = amountText.toLongOrNull() ?: 0L
                                    amountText = (cur + presetVal).toString()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chipText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = duit.textPrimary
                            )
                        }
                    }
                }
            }

            // 3. CATEGORY SELECTOR: ICON GRID (When not transfer)
            AnimatedVisibility(
                visible = selectedType != TransactionType.TRANSFER && !isDebtPayment,
                modifier = Modifier.transactionTourAnchor(12, tourTarget, onTourTargetPositioned),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                CategoryGridSection(
                    categories = relevantCategories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = { category ->
                        selectedCategoryId = category.id
                        if (descriptionText.isBlank() || descriptionText == "Pengeluaran" || descriptionText == "Pemasukan") {
                            descriptionText = category.name
                        }
                    },
                    onOpenAllCategories = { showAllCategoriesDialog = true },
                    accentColor = accentColor
                )
            }

            // 4. SOURCE ASSET SELECTOR: CHIP ROW WITH LIVE BALANCE
            Column(Modifier.transactionTourAnchor(13, tourTarget, onTourTargetPositioned)) {
                Text(
                    text = if (selectedType == TransactionType.TRANSFER) "Sumber Dana (Dari)" else "Sumber Rekening / Dompet",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = duit.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                AssetChipRow(
                    assets = activeAssets,
                    selectedAssetId = selectedAssetId,
                    onAssetSelected = { selectedAssetId = it.id }
                )

                if (isInsufficientBalance && currentSourceAsset != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.padding(start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = duit.expense,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Saldo ${currentSourceAsset.name} (${Formatters.formatRupiah(currentSourceAsset.currentBalance)}) tidak mencukupi nominal transaksi.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = duit.expense
                        )
                    }
                }
            }

            // 5. DESTINATION ASSET SELECTOR (For Transfer)
            AnimatedVisibility(
                visible = selectedType == TransactionType.TRANSFER,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Text(
                        text = "Tujuan Transfer (Ke)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        color = duit.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    AssetChipRow(
                        assets = activeAssets.filter { it.id != selectedAssetId },
                        selectedAssetId = selectedDestAssetId,
                        onAssetSelected = { selectedDestAssetId = it.id }
                    )
                }
            }

            // 6. COLLAPSIBLE "TAMBAH DETAIL" SECTION (Keterangan, Tanggal, Catatan, Tag, Struk OCR, Hutang)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(duit.surface)
                    .border(1.dp, duit.border, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth().transactionTourAnchor(14, tourTarget, onTourTargetPositioned)
                        .clickable(role = Role.Button) {
                            showMoreDetails = !showMoreDetails
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (showMoreDetails) Icons.Rounded.Tune else Icons.Rounded.AddCircleOutline,
                            contentDescription = null,
                            tint = duit.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (showMoreDetails) "Sembunyikan Opsi Lanjutan" else "Tambah Detail (Keterangan, Tanggal, Tag, Struk)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            color = duit.textPrimary
                        )
                    }

                    Icon(
                        imageVector = if (showMoreDetails) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = duit.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showMoreDetails,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(color = duit.border, thickness = 1.dp)

                        // Secondary: Keterangan
                        Column {
                            Text(
                                text = "Keterangan",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = duit.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = descriptionText,
                                onValueChange = { descriptionText = it },
                                placeholder = {
                                    Text(
                                        when (selectedType) {
                                            TransactionType.EXPENSE -> "Contoh: Makan siang, Belanja bulanan"
                                            TransactionType.INCOME -> "Contoh: Gaji bulanan, Bonus project"
                                            TransactionType.TRANSFER -> "Contoh: Top up e-wallet, Pindah tabungan"
                                            else -> "Penyesuaian Saldo"
                                        },
                                        fontSize = 13.sp,
                                        color = duit.textTertiary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = duit.surfaceVariant.copy(alpha = 0.3f),
                                    unfocusedContainerColor = duit.surfaceVariant.copy(alpha = 0.3f),
                                    focusedBorderColor = duit.primary,
                                    unfocusedBorderColor = duit.border
                                )
                            )

                            // Quick Description Suggestions
                            val frequentDescriptions = remember(selectedCategoryId, selectedType) {
                                val dynamic = viewModel.getFrequentDescriptions(selectedCategoryId)
                                if (dynamic.isNotEmpty()) dynamic
                                else when (selectedType) {
                                    TransactionType.EXPENSE -> listOf("Makan Siang", "Kopi", "Bensin", "Belanja", "Tagihan", "Parkir", "Cemilan")
                                    TransactionType.INCOME -> listOf("Gaji", "Bonus", "Freelance", "Investasi", "Cashback", "Hadiah")
                                    TransactionType.TRANSFER -> listOf("Top Up E-Wallet", "Transfer Bank", "Tarik Tunai", "Pindah Tabungan")
                                    else -> listOf("Penyesuaian Saldo")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            FadingHorizontalScrollRow(
                                fadeColor = duit.surface,
                                contentPadding = PaddingValues(start = 2.dp, end = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                frequentDescriptions.forEach { desc ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(duit.surfaceVariant)
                                            .border(1.dp, duit.border, RoundedCornerShape(999.dp))
                                            .clickable(role = Role.Button) {
                                                descriptionText = desc
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = duit.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Secondary: Tanggal
                        DuitAingDatePickerField(
                            dateIsoString = dateText,
                            onDateSelected = { dateText = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Secondary: Catatan Tambahan
                        Column {
                            Text(
                                text = "Catatan Memo",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = duit.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                placeholder = { Text("Tambahkan memo, no. referensi, atau detail lain", fontSize = 12.5.sp) },
                                maxLines = 2,
                                singleLine = false,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = duit.surfaceVariant.copy(alpha = 0.3f),
                                    unfocusedContainerColor = duit.surfaceVariant.copy(alpha = 0.3f),
                                    focusedBorderColor = duit.primary,
                                    unfocusedBorderColor = duit.border
                                )
                            )
                        }

                        // Secondary: Label / Tag Transaksi
                        Column {
                            Text(
                                text = "Label / Tag",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = duit.textSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FadingHorizontalScrollRow(
                                fadeColor = duit.surface,
                                contentPadding = PaddingValues(start = 2.dp, end = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                suggestedTags.forEach { tag ->
                                    val isSelected = selectedTags.contains(tag)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isSelected) duit.primaryContainer else duit.surfaceVariant)
                                            .border(
                                                1.dp,
                                                if (isSelected) duit.primary else duit.border,
                                                RoundedCornerShape(999.dp)
                                            )
                                            .clickable(role = Role.Button) {
                                                selectedTags = if (isSelected) {
                                                    selectedTags - tag
                                                } else {
                                                    selectedTags + tag
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) duit.onPrimaryContainer else duit.textPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customTagInput,
                                    onValueChange = { customTagInput = it },
                                    placeholder = { Text("Tambah tag kustom, mis: #proyek", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            val clean = customTagInput.trim()
                                            if (clean.isNotBlank()) {
                                                val formatted = if (clean.startsWith("#")) clean else "#$clean"
                                                if (!selectedTags.contains(formatted)) {
                                                    selectedTags = selectedTags + formatted
                                                }
                                                customTagInput = ""
                                            }
                                            focusManager.clearFocus()
                                        }
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = duit.surfaceVariant.copy(alpha = 0.3f),
                                        unfocusedContainerColor = duit.surfaceVariant.copy(alpha = 0.3f),
                                        focusedBorderColor = duit.primary,
                                        unfocusedBorderColor = duit.border
                                    )
                                )
                                OutlinedButton(
                                    onClick = {
                                        val clean = customTagInput.trim()
                                        if (clean.isNotBlank()) {
                                            val formatted = if (clean.startsWith("#")) clean else "#$clean"
                                            if (!selectedTags.contains(formatted)) {
                                                selectedTags = selectedTags + formatted
                                            }
                                            customTagInput = ""
                                        }
                                        focusManager.clearFocus()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = duit.primary),
                                    border = BorderStroke(1.dp, duit.border)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }

                        // Secondary: Foto Struk / OCR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            OutlinedButton(
                                onClick = { showReceiptSourceDialog = true },
                                enabled = !isScanningReceipt,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = duit.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = duit.textPrimary
                                ),
                                border = BorderStroke(1.dp, duit.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                                ) {
                                    if (isScanningReceipt) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = duit.primary
                                        )
                                        Text("Membaca...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.DocumentScanner,
                                            contentDescription = null,
                                            tint = duit.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (receiptPhotoPath != null) "Ganti Foto Struk" else "Pindai Struk (OCR)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (receiptPhotoPath != null) {
                                val bitmap = remember(receiptPhotoPath) {
                                    try {
                                        BitmapFactory.decodeFile(receiptPhotoPath)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (bitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                                    ) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Foto Struk",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.6f))
                                                .clickable {
                                                    receiptPhotoPath = null
                                                    isOcrActive = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Hapus Struk",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Secondary: Hutang / Cicilan Repayment Option
                        if (selectedType == TransactionType.EXPENSE && unpaidDebts.isNotEmpty()) {
                            HorizontalDivider(color = duit.border, thickness = 1.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Checkbox) {
                                        isDebtPayment = !isDebtPayment
                                        if (isDebtPayment && selectedDebtId == null && unpaidDebts.isNotEmpty()) {
                                            selectedDebtId = unpaidDebts.first().id
                                            val d = unpaidDebts.first()
                                            if (descriptionText.isBlank()) {
                                                descriptionText = "Pembayaran Hutang: ${d.title}"
                                            }
                                        }
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CreditCard,
                                        contentDescription = null,
                                        tint = if (isDebtPayment) duit.expense else duit.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Bayar Cicilan / Pinjaman?",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = duit.textPrimary
                                    )
                                }
                                Switch(
                                    checked = isDebtPayment,
                                    onCheckedChange = { checked ->
                                        isDebtPayment = checked
                                        if (checked && selectedDebtId == null && unpaidDebts.isNotEmpty()) {
                                            selectedDebtId = unpaidDebts.first().id
                                            val d = unpaidDebts.first()
                                            if (descriptionText.isBlank()) {
                                                descriptionText = "Pembayaran Hutang: ${d.title}"
                                            }
                                        }
                                    }
                                )
                            }

                            if (isDebtPayment) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    unpaidDebts.forEach { debt ->
                                        val isSelected = debt.id == selectedDebtId
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) duit.surface else duit.surfaceVariant)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) duit.expense else duit.border,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable(role = Role.RadioButton) {
                                                    selectedDebtId = debt.id
                                                    descriptionText = "Pembayaran Hutang: ${debt.title} (${debt.lenderName})"
                                                }
                                                .padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = debt.title,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.5.sp
                                                        ),
                                                        color = duit.textPrimary
                                                    )
                                                    Text(
                                                        text = "Sisa: ${Formatters.formatRupiah(debt.remainingAmount)}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = duit.expense
                                                    )
                                                }

                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(duit.expense)
                                                            .clickable(role = Role.Button) {
                                                                amountText = debt.remainingAmount.toString()
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = "Isi Lunas",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp
                                                            ),
                                                            color = Color.White
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
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 7. STICKY BOTTOM SAVE BUTTON (Dynamic Color matching Type, Disabled until Nominal & Asset filled)
        val isValid = amount > 0 && selectedAssetId.isNotBlank() &&
                (selectedType != TransactionType.TRANSFER || (selectedDestAssetId.isNotBlank() && selectedAssetId != selectedDestAssetId)) &&
                (!isDebtPayment || selectedDebtId != null)

        val buttonLabel = when {
            isDebtPayment -> "Bayar Cicilan Sekarang"
            existingTx != null -> "Perbarui Transaksi"
            selectedType == TransactionType.INCOME -> "Simpan Pemasukan"
            selectedType == TransactionType.TRANSFER -> "Transfer Saldo"
            else -> "Simpan Pengeluaran"
        }

        var showInsufficientBalanceConfirmDialog by remember { mutableStateOf(false) }
        var pendingSubmitAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        val executeSubmit: () -> Unit = submit@{
            if (tourTarget != null) return@submit
            if (isDebtPayment && selectedDebtId != null && selectedType == TransactionType.EXPENSE) {
                viewModel.recordDebtPayment(
                    debtId = selectedDebtId!!,
                    paymentAmount = amount,
                    sourceAssetId = selectedAssetId,
                    paymentDate = dateText,
                    note = noteText.ifBlank { descriptionText }
                )
                isSubmitting = false
                onDismiss()
            } else {
                val finalTx = Transaction(
                    id = existingTx?.id ?: "tx_${UUID.randomUUID()}",
                    type = selectedType.value,
                    amount = amount,
                    description = descriptionText.ifBlank {
                        when (selectedType) {
                            TransactionType.INCOME -> "Pemasukan"
                            TransactionType.EXPENSE -> "Pengeluaran"
                            TransactionType.TRANSFER -> "Transfer"
                            else -> "Penyesuaian Saldo"
                        }
                    },
                    categoryId = if (selectedType != TransactionType.TRANSFER) selectedCategoryId else null,
                    assetId = selectedAssetId,
                    destinationAssetId = if (selectedType == TransactionType.TRANSFER) selectedDestAssetId else null,
                    transactionDate = dateText,
                    transactionTime = existingTx?.transactionTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    note = noteText,
                    attachment = receiptPhotoPath,
                    inputMethod = if (isOcrActive) InputMethod.OCR.value else existingTx?.inputMethod ?: "manual",
                    debtId = existingTx?.debtId,
                    goalId = existingTx?.goalId,
                    tags = selectedTags.joinToString(" "),
                    isAdjustment = (selectedType == TransactionType.ADJUSTMENT),
                    adjustmentDirection = if (selectedType == TransactionType.ADJUSTMENT) (existingTx?.adjustmentDirection ?: 1) else 0
                )

                if (existingTx != null) {
                    viewModel.updateTransaction(finalTx, previousTx = existingTx) { success ->
                        isSubmitting = false
                        if (success) {
                            onDismiss()
                        }
                    }
                } else {
                    viewModel.saveTransaction(finalTx) { success ->
                        isSubmitting = false
                        if (success) {
                            onDismiss()
                        }
                    }
                }
            }
        }

        DuitAingButton(
            text = buttonLabel,
            onClick = {
                if (isValid && !isSubmitting) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isInsufficientBalance) {
                        pendingSubmitAction = executeSubmit
                        showInsufficientBalanceConfirmDialog = true
                    } else {
                        isSubmitting = true
                        executeSubmit()
                    }
                }
            },
            containerColor = accentColor,
            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
            enabled = isValid && !isSubmitting && tourTarget == null,
            isLoading = isSubmitting,
            modifier = Modifier.fillMaxWidth().transactionTourAnchor(15, tourTarget, onTourTargetPositioned),
            height = 50.dp,
            shadowOffset = 0.dp
        )

        if (showInsufficientBalanceConfirmDialog) {
            DuitAingConfirmDialog(
                isOpen = true,
                title = "SALDO TIDAK MENCUKUPI",
                message = "Saldo aset saat ini (${Formatters.formatRupiah(currentSourceAsset?.currentBalance ?: 0L)}) lebih kecil dari nominal transaksi (${Formatters.formatRupiah(amount)}). Tetap simpan transaksi ini?",
                confirmText = "TETAP SIMPAN",
                confirmVariant = DuitAingButtonVariant.PRIMARY,
                onConfirm = {
                    showInsufficientBalanceConfirmDialog = false
                    isSubmitting = true
                    pendingSubmitAction?.invoke()
                    pendingSubmitAction = null
                },
                onDismiss = {
                    showInsufficientBalanceConfirmDialog = false
                    pendingSubmitAction = null
                }
            )
        }
    }

    // Modal Sheet: All Categories Search & Pick Dialog
    if (showAllCategoriesDialog) {
        AllCategoriesDialog(
            categories = relevantCategories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { cat ->
                selectedCategoryId = cat.id
                if (descriptionText.isBlank() || descriptionText == "Pengeluaran" || descriptionText == "Pemasukan") {
                    descriptionText = cat.name
                }
                showAllCategoriesDialog = false
            },
            onDismiss = { showAllCategoriesDialog = false }
        )
    }

    // Modal Sheet: Receipt Photo Source (Camera vs Gallery)
    if (showReceiptSourceDialog) {
        ReceiptSourceDialog(
            onSelectCamera = {
                showReceiptSourceDialog = false
                try {
                    val cameraDir = File(context.cacheDir, "camera").apply {
                        if (!exists()) mkdirs()
                    }
                    val tempFile = File(cameraDir, "camera_receipt_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                    tempCameraUri = uri
                    takePictureLauncher.launch(uri)
                } catch (e: Exception) {
                    viewModel.showSnackbar("Gagal membuka kamera: ${e.message}")
                }
            },
            onSelectGallery = {
                showReceiptSourceDialog = false
                receiptPickerLauncher.launch("image/*")
            },
            onDismiss = { showReceiptSourceDialog = false }
        )
    }
}

/**
 * Compact Category Icon Grid (2 rows of 4 items max = 8 slots)
 * Keep quick choices stable; an item picked from search replaces only the last slot.
 */
@Composable
private fun CategoryGridSection(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (Category) -> Unit,
    onOpenAllCategories: () -> Unit,
    accentColor: Color
) {
    val duit = MaterialTheme.duit
    val haptic = LocalHapticFeedback.current

    val gridItems = remember(categories, selectedCategoryId) {
        com.cashflow.app.data.model.quickCategories(categories, selectedCategoryId)
    }
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kategori",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                ),
                color = duit.textSecondary
            )

            if (categories.size > 8) {
                Text(
                    text = "Lihat semua (${categories.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = duit.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClick = onOpenAllCategories)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid 4 columns
        val rows = gridItems.chunked(4)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { cat ->
                        key(cat.id) {
                        val isSelected = cat.id == selectedCategoryId
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (pressed) 0.96f else 1f, if (reduceMotion) snap() else tween(100), label = "CategoryPress")
                        val tileColor by animateColorAsState(if (isSelected) duit.primaryContainer else duit.surface, if (reduceMotion) snap() else tween(160), label = "CategorySelection")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .background(tileColor)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) duit.primary else duit.border,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .selectable(selected = isSelected, interactionSource = interaction, indication = ripple(), role = Role.RadioButton) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCategorySelected(cat)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CategoryIcon(
                                    iconName = cat.icon,
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                    backgroundColor = if (isSelected) duit.primary else duit.surfaceVariant,
                                    tintColor = if (isSelected) duit.onPrimary else duit.textPrimary
                                )
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = duit.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    }

                    // If last row has space and there are more categories, show "Lainnya" tile
                    if (rowIndex == rows.lastIndex && rowItems.size < 4 && categories.size > 8) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(duit.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, duit.border, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = onOpenAllCategories)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(duit.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GridView,
                                        contentDescription = null,
                                        tint = duit.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Lainnya",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = duit.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        // Fill any remaining spaces
                        for (i in 0 until (3 - rowItems.size)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    } else if (rowIndex == rows.lastIndex && rowItems.size < 4) {
                        for (i in 0 until (4 - rowItems.size)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal Asset Selection Row with Balance under Asset Name
 */
@Composable
private fun AssetChipRow(
    assets: List<Asset>,
    selectedAssetId: String,
    onAssetSelected: (Asset) -> Unit
) {
    val duit = MaterialTheme.duit
    val haptic = LocalHapticFeedback.current

    FadingHorizontalScrollRow(
        fadeColor = duit.background,
        contentPadding = PaddingValues(start = 2.dp, end = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        assets.forEach { asset ->
            val isSelected = asset.id == selectedAssetId
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) duit.primaryContainer else duit.surface)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) duit.primary else duit.border,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(role = Role.RadioButton) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAssetSelected(asset)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (asset.type.lowercase()) {
                                "bank" -> Icons.Rounded.AccountBalance
                                "e-wallet" -> Icons.Rounded.PhoneAndroid
                                "kartu kredit" -> Icons.Rounded.CreditCard
                                else -> Icons.Rounded.AccountBalanceWallet
                            },
                            contentDescription = null,
                            tint = if (isSelected) duit.primary else duit.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = asset.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            color = duit.textPrimary,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Formatters.formatRupiah(asset.currentBalance),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isSelected) duit.onPrimaryContainer.copy(alpha = 0.85f) else duit.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * All Categories Picker Dialog with Search Bar
 */
@Composable
private fun AllCategoriesDialog(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    val duit = MaterialTheme.duit
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(duit.surface)
                    .clickable(enabled = false) {}
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Semua Kategori",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = duit.textPrimary
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(duit.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup",
                            tint = duit.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari kategori...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = duit.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = duit.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = duit.surfaceVariant.copy(alpha = 0.4f),
                        focusedBorderColor = duit.primary,
                        unfocusedBorderColor = duit.border
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Category Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.id }) { cat ->
                        val isSelected = cat.id == selectedCategoryId
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(100), label = "FullCategoryPress")
                        Box(
                            modifier = Modifier
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) duit.primaryContainer else duit.surfaceVariant.copy(alpha = 0.5f))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) duit.primary else duit.border,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .selectable(selected = isSelected, interactionSource = interaction, indication = ripple(), role = Role.RadioButton) {
                                    onCategorySelected(cat)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CategoryIcon(
                                    iconName = cat.icon,
                                    size = 38.dp,
                                    iconSize = 20.dp,
                                    backgroundColor = if (isSelected) duit.primary else duit.surfaceVariant,
                                    tintColor = if (isSelected) duit.onPrimary else duit.textPrimary
                                )
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = duit.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
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
 * Receipt Photo Source Selection Dialog (Camera vs Gallery)
 */
@Composable
private fun ReceiptSourceDialog(
    onSelectCamera: () -> Unit,
    onSelectGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    val duit = MaterialTheme.duit

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(duit.surface)
                    .clickable(enabled = false) {}
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pindai Struk (OCR)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = duit.textPrimary
                        )
                        Text(
                            text = "Pilih sumber foto struk untuk membaca otomatis",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = duit.textSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(duit.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup",
                            tint = duit.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Option 1: Kamera (Ambil Foto Langsung)
                Surface(
                    onClick = onSelectCamera,
                    shape = RoundedCornerShape(14.dp),
                    color = duit.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, duit.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(duit.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = "Kamera",
                                tint = duit.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ambil Foto (Kamera)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                color = duit.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Foto struk fisik secara langsung",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = duit.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Galeri
                Surface(
                    onClick = onSelectGallery,
                    shape = RoundedCornerShape(14.dp),
                    color = duit.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, duit.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(duit.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = "Galeri",
                                tint = duit.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pilih dari Galeri",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                color = duit.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Unggah file foto struk atau screenshot",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = duit.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Modifier.transactionTourAnchor(
    id: Int, activeTarget: Int?, onPositioned: (Int, Rect) -> Unit
): Modifier {
    if (activeTarget == null) return this
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(activeTarget) {
        if (activeTarget == id) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return this.bringIntoViewRequester(requester)
        .onGloballyPositioned { onPositioned(id, it.boundsInRoot()) }
}
