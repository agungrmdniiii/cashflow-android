package com.cashflow.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.DebtStatus
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
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
    onDismiss: () -> Unit
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
    var selectedCategoryId by remember {
        mutableStateOf(existingTx?.categoryId ?: "")
    }
    var selectedAssetId by remember {
        mutableStateOf(existingTx?.assetId ?: defaultAsset?.id ?: activeAssets.firstOrNull()?.id ?: "")
    }
    var selectedDestAssetId by remember {
        mutableStateOf(existingTx?.destinationAssetId ?: "")
    }
    var dateText by remember {
        mutableStateOf(existingTx?.transactionDate ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var noteText by remember {
        mutableStateOf(existingTx?.note ?: "")
    }

    var isSubmitting by remember { mutableStateOf(false) }

    // Dynamic accent color depending on transaction type
    val accentColor = when (selectedType) {
        TransactionType.EXPENSE -> if (isDebtPayment) CoralPrimary else StatusNegative
        TransactionType.INCOME -> JadePrimary
        TransactionType.TRANSFER -> StatusTransfer
    }

    // Filter categories based on selected type
    val relevantCategories = remember(selectedType, categories) {
        val typeStr = if (selectedType == TransactionType.INCOME) "income" else "expense"
        categories.filter { it.type == typeStr && it.isActive }
    }

    // Set initial category if not set
    LaunchedEffect(relevantCategories) {
        if (selectedCategoryId.isBlank() && relevantCategories.isNotEmpty() && selectedType != TransactionType.TRANSFER) {
            selectedCategoryId = relevantCategories.first().id
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
            currentSourceAsset != null &&
            amount > currentSourceAsset.currentBalance

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.93f)
                .padding(vertical = 10.dp)
                .imePadding(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
            colors = CardDefaults.cardColors(containerColor = PebbleSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
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
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (selectedType) {
                                    TransactionType.EXPENSE -> if (isDebtPayment) Icons.Rounded.CreditCard else Icons.Rounded.NorthEast
                                    TransactionType.INCOME -> Icons.Rounded.SouthWest
                                    TransactionType.TRANSFER -> Icons.Rounded.SwapHoriz
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (existingTx != null) "EDIT TRANSAKSI" else "TAMBAH TRANSAKSI",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp,
                                    fontSize = 18.sp
                                ),
                                color = DarkSurface
                            )
                            Text(
                                text = when {
                                    isDebtPayment -> "BAYAR CICILAN / HUTANG"
                                    selectedType == TransactionType.EXPENSE -> "CATAT PENGELUARAN DANA"
                                    selectedType == TransactionType.INCOME -> "CATAT PEMASUKAN DANA"
                                    else -> "PINDAH SALDO ANTAR REKENING"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.5.sp
                                ),
                                color = accentColor
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                enabled = !isSubmitting,
                                role = Role.Button,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PebbleSurfaceVariant)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Tutup",
                                tint = DarkSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. HERO AMOUNT CARD (Polished, Tactile Money Input)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.20f)),
                        colors = CardDefaults.cardColors(containerColor = PebbleSurfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "NOMINAL TRANSAKSI",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp,
                                        fontSize = 11.sp
                                    ),
                                    color = TextSecondary
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accentColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = selectedType.displayName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.5.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = accentColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                                prefix = {
                                    Text(
                                        text = "Rp ",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = accentColor
                                    )
                                },
                                textStyle = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp,
                                    fontSize = 26.sp
                                ),
                                placeholder = { Text("0", color = TextSecondary.copy(alpha = 0.5f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = PebbleSurface,
                                    unfocusedContainerColor = PebbleSurface,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = DarkBorder.copy(alpha = 0.35f)
                                )
                            )

                            if (amount > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Terbaca: ${Formatters.formatRupiah(amount)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        ),
                                        color = accentColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Fast Preset Increment Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PebbleSurface)
                                            .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                val cur = amountText.toLongOrNull() ?: 0L
                                                amountText = (cur + presetVal).toString()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = chipText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = DarkSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. INTEGRATED DEBT REPAYMENT SECTION (Only in EXPENSE mode)
                    if (selectedType == TransactionType.EXPENSE) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isDebtPayment) CoralPrimary.copy(alpha = 0.5f) else DarkBorder.copy(alpha = 0.20f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDebtPayment) CoralLight.copy(alpha = 0.4f) else PebbleSurfaceVariant.copy(alpha = 0.35f)
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
                                        .clickable {
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
                                                .background(if (isDebtPayment) CoralPrimary else PebbleSurface)
                                                .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
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
                                                text = if (unpaidDebts.isNotEmpty()) "${unpaidDebts.size} pinjaman aktif tersedia" else "Tidak ada hutang belum lunas",
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
                                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.15f), thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (unpaidDebts.isEmpty()) {
                                        Text(
                                            text = "Tidak ada catatan hutang aktif. Anda dapat menambahkan hutang di tab Aset > Hutang Saya.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = TextSecondary
                                        )
                                    } else {
                                        Text(
                                            text = "PILIH CATATAN PINJAMAN:",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.8.sp,
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
                                                        .clickable {
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
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(CoralPrimary)
                                                                        .clickable {
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
                    }

                    // 3. DESCRIPTION FIELD with Contextual Suggestions
                    Column {
                        Text(
                            text = "DESKRIPSI TRANSAKSI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                fontSize = 11.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            placeholder = {
                                Text(
                                    when (selectedType) {
                                        TransactionType.EXPENSE -> "Contoh: Makan siang, Belanja mingguan"
                                        TransactionType.INCOME -> "Contoh: Gaji bulanan, Bonus project"
                                        TransactionType.TRANSFER -> "Contoh: Top up e-wallet, Pindah tabungan"
                                    },
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PebbleSurface,
                                unfocusedContainerColor = PebbleSurface,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = DarkBorder.copy(alpha = 0.35f)
                            )
                        )

                        val descSuggestions = remember(selectedType, isDebtPayment) {
                            when {
                                isDebtPayment -> listOf("Bayar Cicilan", "Pelunasan Hutang", "Cicilan Bulanan")
                                selectedType == TransactionType.EXPENSE -> listOf("Makan Siang", "Kopi", "Bensin", "Belanja Harian", "Tagihan Listrik", "Parkir")
                                selectedType == TransactionType.INCOME -> listOf("Gaji Bulanan", "Freelance", "Bonus", "Hadiah", "Cashback", "Penjualan")
                                selectedType == TransactionType.TRANSFER -> listOf("Top Up GoPay", "Top Up DANA", "Pindah Saldo", "Tabungan Bulanan")
                                else -> emptyList()
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            descSuggestions.forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PebbleSurfaceVariant)
                                        .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .clickable { descriptionText = suggestion }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.5.sp
                                        ),
                                        color = DarkSurface
                                    )
                                }
                            }
                        }
                    }

                    // 4. CATEGORY SELECTOR (Only for Income and Expense, if not Debt payment)
                    if (selectedType != TransactionType.TRANSFER && !isDebtPayment) {
                        Column {
                            Text(
                                text = "KATEGORI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 11.sp
                                ),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            CategoryDropdownField(
                                categories = relevantCategories,
                                selectedCategoryId = selectedCategoryId,
                                onCategorySelected = { selectedCategoryId = it.id },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 5. SOURCE ASSET SELECTOR (With Real-Time Balance Display)
                    Column {
                        Text(
                            text = if (selectedType == TransactionType.TRANSFER) "ASET SUMBER (DARI)" else "ASET PEMBAYARAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                fontSize = 11.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

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
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isInsufficientBalance) StatusNegative else TextSecondary
                                )
                                if (isInsufficientBalance) {
                                    Text(
                                        text = "• Saldo tidak mencukupi!",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = StatusNegative
                                    )
                                }
                            }
                        }
                    }

                    // 6. DESTINATION ASSET SELECTOR (For Transfer)
                    if (selectedType == TransactionType.TRANSFER) {
                        Column {
                            Text(
                                text = "ASET TUJUAN (KE)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 11.sp
                                ),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            AssetDropdownField(
                                assets = activeAssets.filter { it.id != selectedAssetId },
                                selectedAssetId = selectedDestAssetId,
                                onAssetSelected = { selectedDestAssetId = it.id },
                                placeholder = "PILIH ASET TUJUAN",
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

                    // 8. NOTES FIELD (Optional)
                    Column {
                        Text(
                            text = "CATATAN TAMBAHAN (OPSIONAL)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                fontSize = 11.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("Tambahkan memo, invoice, atau detail lain", fontSize = 13.sp) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PebbleSurface,
                                unfocusedContainerColor = PebbleSurface,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = DarkBorder.copy(alpha = 0.35f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // VALIDATION & SUBMIT BUTTON
                val isValid = amount > 0 && selectedAssetId.isNotBlank() &&
                        (selectedType != TransactionType.TRANSFER || (selectedDestAssetId.isNotBlank() && selectedAssetId != selectedDestAssetId)) &&
                        (!isDebtPayment || selectedDebtId != null)

                val buttonLabel = when {
                    isDebtPayment -> "BAYAR CICILAN SEKARANG"
                    existingTx != null -> "PERBARUI TRANSAKSI"
                    selectedType == TransactionType.INCOME -> "SIMPAN PEMASUKAN"
                    selectedType == TransactionType.TRANSFER -> "TRANSFER SALDO"
                    else -> "SIMPAN PENGELUARAN"
                }

                DuitAingButton(
                    text = buttonLabel,
                    onClick = {
                        if (isValid && !isSubmitting) {
                            isSubmitting = true

                            if (isDebtPayment && selectedDebtId != null && selectedType == TransactionType.EXPENSE) {
                                // Debt repayment flow: updates debt, deducts asset, inserts expense transaction
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
                                        }
                                    },
                                    categoryId = if (selectedType != TransactionType.TRANSFER) selectedCategoryId else null,
                                    assetId = selectedAssetId,
                                    destinationAssetId = if (selectedType == TransactionType.TRANSFER) selectedDestAssetId else null,
                                    transactionDate = dateText,
                                    transactionTime = existingTx?.transactionTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                    note = noteText,
                                    inputMethod = existingTx?.inputMethod ?: "manual"
                                )

                                if (existingTx != null) {
                                    viewModel.updateTransaction(finalTx) { success ->
                                        isSubmitting = false
                                        if (success) onDismiss()
                                    }
                                } else {
                                    viewModel.saveTransaction(finalTx) { success ->
                                        isSubmitting = false
                                        if (success) onDismiss()
                                    }
                                }
                            }
                        }
                    },
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    enabled = isValid && !isSubmitting,
                    isLoading = isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    variant = DuitAingButtonVariant.PRIMARY,
                    height = 50.dp,
                    shadowOffset = 3.dp
                )
            }
        }
    }
}
