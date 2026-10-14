package com.cashflow.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.DebtStatus
import com.cashflow.app.data.model.InputMethod
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.data.service.ReceiptOcrService
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
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
    modifier: Modifier = Modifier.fillMaxHeight(0.90f)
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
    modifier: Modifier = Modifier
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
        mutableStateOf(existingTx?.amount?.toString() ?: "")
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

    val amountFocusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val favoriteTemplates by viewModel.favoriteTemplates.collectAsState()

    LaunchedEffect(Unit) {
        if (existingTx == null) {
            kotlinx.coroutines.delay(120)
            try {
                amountFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var isSuccessBurst by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Dynamic accent color with smooth animated transition
    val targetAccentColor = when (selectedType) {
        TransactionType.EXPENSE -> if (isDebtPayment) CoralPrimary else StatusNegative
        TransactionType.INCOME -> JadePrimary
        TransactionType.TRANSFER -> StatusTransfer
        else -> JadePrimary
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

    // Multi-tag state
    var selectedTags by remember {
        mutableStateOf(
            existingTx?.tags?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }
    var customTagInput by remember { mutableStateOf("") }
    val suggestedTags = listOf("#Makan", "#Bulanan", "#Keluarga", "#Kantor", "#Liburan", "#Hobi", "#Kesehatan", "#Transport", "#Belanja")

    val receiptPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
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
                        viewModel.showSnackbar("Struk terbaca! Periksa kembali nominal & detail.")
                    }
                } catch (e: Exception) {
                    viewModel.showSnackbar("Gagal memindai struk: ${e.message}")
                } finally {
                    isScanningReceipt = false
                }
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
                .padding(top = 4.dp, bottom = 10.dp),
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
                    color = DarkSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isDebtPayment -> "Bayar Cicilan / Hutang"
                        selectedType == TransactionType.EXPENSE -> "Catat pengeluaran dana"
                        selectedType == TransactionType.INCOME -> "Catat pemasukan dana"
                        else -> "Pindah saldo antar rekening"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = { onBack?.invoke() ?: onDismiss() },
                enabled = !isSubmitting,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PebbleSurfaceVariant.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup",
                    tint = DarkSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Type Tabs Segmented Control
        TransactionTypeSegmentedControl(
            selectedType = selectedType,
            onTypeSelected = {
                selectedType = it
                if (it != TransactionType.EXPENSE) {
                    isDebtPayment = false
                    selectedDebtId = null
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Scrollable Form Fields with Generous Breathing Room
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Favorite Templates Row (if any exist)
            if (favoriteTemplates.isNotEmpty() && existingTx == null) {
                Column {
                    Text(
                        text = "TEMPLAT FAVORIT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FadingHorizontalScrollRow(
                        fadeColor = PebbleBackground,
                        contentPadding = PaddingValues(horizontal = 2.dp),
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
                                colors = CardDefaults.outlinedCardColors(containerColor = PebbleSurface),
                                border = BorderStroke(1.dp, PebbleBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFE89A3C),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${fav.name} • ${Formatters.formatCompact(fav.amount)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        color = DarkSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. HERO AMOUNT SECTION (Clean, Spacious, No nested cards)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PebbleSurface)
                    .border(1.dp, PebbleBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Nominal Transaksi",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    prefix = {
                        Text(
                            text = "Rp ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = accentColor
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        fontSize = 30.sp
                    ),
                    placeholder = {
                        Text(
                            text = "0",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary.copy(alpha = 0.35f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    visualTransformation = ThousandSeparatorVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountFocusRequester),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PebbleBackground.copy(alpha = 0.5f),
                        unfocusedContainerColor = PebbleBackground.copy(alpha = 0.3f),
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Fast Preset Increment Chips with edge fade and safe trailing padding
                FadingHorizontalScrollRow(
                    fadeColor = PebbleSurface,
                    contentPadding = PaddingValues(horizontal = 2.dp),
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
                                .clip(RoundedCornerShape(999.dp))
                                .background(PebbleSurfaceVariant)
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
                                    fontSize = 11.5.sp
                                ),
                                color = DarkSurface
                            )
                        }
                    }
                }
            }

            // 2. INTEGRATED DEBT REPAYMENT SECTION (Only in EXPENSE mode, and ONLY IF active unpaid debts exist!)
            AnimatedVisibility(
                visible = selectedType == TransactionType.EXPENSE && unpaidDebts.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = 1.dp,
                            color = if (isDebtPayment) CoralPrimary.copy(alpha = 0.5f) else PebbleBorder,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDebtPayment) CoralLight.copy(alpha = 0.4f) else PebbleSurface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDebtPayment) CoralPrimary else PebbleSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CreditCard,
                                        contentDescription = null,
                                        tint = if (isDebtPayment) Color.White else DarkSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Bayar Cicilan / Hutang?",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        ),
                                        color = DarkSurface
                                    )
                                    Text(
                                        text = "${unpaidDebts.size} pinjaman aktif tersedia",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp
                                        ),
                                        color = TextSecondary
                                    )
                                }
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
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CoralPrimary,
                                    uncheckedThumbColor = PebbleSurface,
                                    uncheckedTrackColor = PebbleBorder
                                )
                            )
                        }

                        if (isDebtPayment) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.12f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "PILIH CATATAN PINJAMAN:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    fontSize = 10.5.sp
                                ),
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                unpaidDebts.forEach { debt ->
                                    val isSelected = debt.id == selectedDebtId
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) PebbleSurface else PebbleSurfaceVariant.copy(alpha = 0.5f))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) CoralPrimary.copy(alpha = 0.6f) else DarkBorder.copy(alpha = 0.20f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable(role = Role.RadioButton) {
                                                selectedDebtId = debt.id
                                                descriptionText = "Pembayaran Hutang: ${debt.title} (${debt.lenderName})"
                                            }
                                            .padding(10.dp)
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
                                                        fontSize = 13.sp
                                                    ),
                                                    color = DarkSurface
                                                )
                                                Text(
                                                    text = "Pemberi: ${debt.lenderName} • Jatuh Tempo: ${debt.dueDate}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.5.sp
                                                    ),
                                                    color = TextSecondary
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Sisa: ${Formatters.formatRupiah(debt.remainingAmount)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 11.5.sp
                                                    ),
                                                    color = StatusNegative
                                                )

                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .minimumInteractiveComponentSize()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(CoralPrimary)
                                                            .clickable(role = Role.Button) {
                                                                amountText = debt.remainingAmount.toString()
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = "Isi Lunas",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
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

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Mengurangi saldo aset pembayaran & mengurangi sisa hutang.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 3. CATEGORY SELECTOR (Clean, single entry point - no duplicate chip cloud)
            AnimatedVisibility(
                visible = selectedType != TransactionType.TRANSFER && !isDebtPayment,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Text(
                        text = "Kategori",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    CategoryDropdownField(
                        categories = relevantCategories,
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = { selectedCategoryId = it.id },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. DESCRIPTION FIELD (Clean & simple text field)
            Column {
                Text(
                    text = "Keterangan",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                TransactionType.EXPENSE -> "Contoh: Makan siang, Belanja mingguan"
                                TransactionType.INCOME -> "Contoh: Gaji bulanan, Bonus project"
                                TransactionType.TRANSFER -> "Contoh: Top up e-wallet, Pindah tabungan"
                                else -> "Contoh: Penyesuaian Saldo"
                            },
                            fontSize = 13.5.sp,
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PebbleSurface,
                        unfocusedContainerColor = PebbleSurface,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = PebbleBorder
                    )
                )

                // Quick Description Preset Chips (Dynamic based on frequent past entries per category)
                val frequentDescriptions = remember(selectedCategoryId, selectedType) {
                    val dynamic = viewModel.getFrequentDescriptions(selectedCategoryId)
                    if (dynamic.isNotEmpty()) dynamic
                    else when (selectedType) {
                        TransactionType.EXPENSE -> listOf("Makan Siang", "Kopi", "Bensin", "Belanja", "Tagihan", "Parkir", "Cemilan", "Kesehatan")
                        TransactionType.INCOME -> listOf("Gaji", "Bonus", "Freelance", "Investasi", "Hadiah", "Cashback", "Penjualan")
                        TransactionType.TRANSFER -> listOf("Top Up E-Wallet", "Transfer Bank", "Tarik Tunai", "Pindah Tabungan")
                        else -> listOf("Penyesuaian Saldo")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FadingHorizontalScrollRow(
                    fadeColor = PebbleBackground,
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    frequentDescriptions.forEach { desc ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(PebbleSurface)
                                .border(1.dp, PebbleBorder, RoundedCornerShape(999.dp))
                                .clickable(role = Role.Button) {
                                    descriptionText = desc
                                }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = DarkSurface
                            )
                        }
                    }
                }
            }

            // 5. SOURCE ASSET SELECTOR (With Real-Time Balance Display)
            Column {
                Text(
                    text = if (selectedType == TransactionType.TRANSFER) "Sumber Dana (Dari)" else "Sumber Dana",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                AssetDropdownField(
                    assets = activeAssets,
                    selectedAssetId = selectedAssetId,
                    onAssetSelected = { selectedAssetId = it.id },
                    modifier = Modifier.fillMaxWidth()
                )

                // Real-time balance info & alert
                if (currentSourceAsset != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.padding(start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (isInsufficientBalance) StatusNegative else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Saldo tersedia: ${Formatters.formatRupiah(currentSourceAsset.currentBalance)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (isInsufficientBalance) StatusNegative else TextSecondary
                        )
                        if (isInsufficientBalance) {
                            Text(
                                text = "• Saldo tidak mencukupi!",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = StatusNegative
                            )
                        }
                    }
                }
            }

            // 6. DESTINATION ASSET SELECTOR (For Transfer)
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
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    AssetDropdownField(
                        assets = activeAssets.filter { it.id != selectedAssetId },
                        selectedAssetId = selectedDestAssetId,
                        onAssetSelected = { selectedDestAssetId = it.id },
                        placeholder = "Pilih Aset Tujuan",
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (currentDestAsset != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.padding(start = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Saldo saat ini: ${Formatters.formatRupiah(currentDestAsset.currentBalance)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // 7. DATE FIELD
            DuitAingDatePickerField(
                dateIsoString = dateText,
                onDateSelected = { dateText = it },
                modifier = Modifier.fillMaxWidth()
            )

            // 8. NOTES FIELD (Collapsible)
            var showNotes by remember { mutableStateOf(noteText.isNotBlank()) }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { showNotes = !showNotes }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Catatan Tambahan",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = if (showNotes) "Sembunyikan" else "+ Tambah catatan",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        color = accentColor
                    )
                }

                AnimatedVisibility(
                    visible = showNotes,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("Tambahkan memo, no. referensi, atau detail lain", fontSize = 13.sp) },
                            maxLines = 3,
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PebbleSurface,
                                unfocusedContainerColor = PebbleSurface,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = PebbleBorder
                            )
                        )
                    }
                }
            }

            // 9. MULTI-TAG SELECTOR
            Column {
                Text(
                    text = "Label / Tag Transaksi",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FadingHorizontalScrollRow(
                    fadeColor = PebbleBackground,
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestedTags.forEach { tag ->
                        val isSelected = selectedTags.contains(tag)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.15f) else PebbleSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) accentColor else PebbleBorder,
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable(role = Role.Button) {
                                    selectedTags = if (isSelected) {
                                        selectedTags - tag
                                    } else {
                                        selectedTags + tag
                                    }
                                }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) accentColor else DarkSurface
                            )
                        }
                    }
                }

                // Custom Tag Input Field
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customTagInput,
                        onValueChange = { customTagInput = it },
                        placeholder = { Text("Tambah tag kustom, mis: #proyekA", fontSize = 12.sp) },
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
                            focusedContainerColor = PebbleSurface,
                            unfocusedContainerColor = PebbleSurface,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = PebbleBorder
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                        border = BorderStroke(1.dp, accentColor)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // 10. RECEIPT ATTACHMENT & OCR SCANNER
            Column {
                Text(
                    text = "Lampiran Foto Struk / OCR",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Button to launch OCR photo picker
                    OutlinedButton(
                        onClick = {
                            receiptPickerLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PebbleSurface,
                            contentColor = DarkSurface
                        ),
                        border = BorderStroke(1.dp, PebbleBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isScanningReceipt) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = accentColor
                                )
                                Text("Membaca...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.DocumentScanner,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (receiptPhotoPath != null) "Ganti Struk (OCR)" else "Pindai Struk (OCR)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Thumbnail preview if photo attached
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
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, PebbleBorder, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Foto Struk",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
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
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

                Spacer(modifier = Modifier.height(14.dp))

                // VALIDATION & SUBMIT BUTTON
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

                val executeSubmit: () -> Unit = {
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
                            tags = selectedTags.joinToString(" ")
                        )

                        if (existingTx != null) {
                            viewModel.updateTransaction(finalTx, previousTx = existingTx) { success ->
                                if (success) {
                                    isSuccessBurst = true
                                } else {
                                    isSubmitting = false
                                }
                            }
                        } else {
                            viewModel.saveTransaction(finalTx) { success ->
                                if (success) {
                                    isSuccessBurst = true
                                } else {
                                    isSubmitting = false
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
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    enabled = isValid && !isSubmitting && !isSuccessBurst,
                    isLoading = isSubmitting && !isSuccessBurst,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (isValid) accentColor else null,
                    height = 52.dp,
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

                if (isSuccessBurst) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.cashflow.app.ui.components.SuccessBurstMotion(
                            size = 110.dp,
                            onAnimationEnd = {
                                isSubmitting = false
                                isSuccessBurst = false
                                onDismiss()
                            }
                        )
                    }
                }
            }
}
