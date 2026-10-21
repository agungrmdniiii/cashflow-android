package com.cashflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.*
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun RecurringTransactionsScreen(
    viewModel: CashflowViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recurringList by viewModel.recurringTransactions.collectAsState()
    val upcoming by viewModel.upcomingRecurringBills.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshUpcomingBills() }
    val categories by viewModel.categories.collectAsState()
    val assets by viewModel.activeAssets.collectAsState()
    val debts by viewModel.debts.collectAsState()

    var showFormDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RecurringTransaction?>(null) }
    val duit = MaterialTheme.duit

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(duit.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(duit.surface)
                        .border(1.dp, duit.border, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali",
                        tint = duit.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jadwal Berulang",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = duit.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tagihan, gaji & langganan",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp
                        ),
                        color = duit.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DuitAingButton(
                text = "Tambah",
                onClick = {
                    editingItem = null
                    showFormDialog = true
                },
                leadingIcon = Icons.Rounded.Add,
                variant = DuitAingButtonVariant.PRIMARY,
                height = 38.dp,
                shadowOffset = 0.dp
            )
        }

        if (recurringList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedEmptyState(
                    icon = Icons.Rounded.Autorenew,
                    title = "BELUM ADA JADWAL BERULANG",
                    description = "Atur transaksi berkala seperti tagihan listrik, cicilan, langganan streaming, atau gaji bulanan agar tidak perlu repot mencatat manual.",
                    lottieRawRes = com.cashflow.app.R.raw.lottie_manage_money,
                    actionText = "Buat Jadwal Pertama",
                    actionIcon = Icons.Rounded.Add,
                    onActionClick = {
                        editingItem = null
                        showFormDialog = true
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                if (upcoming.isNotEmpty()) {
                    item { Text("Jatuh tempo & 7 hari mendatang", color = duit.textPrimary, fontWeight = FontWeight.Bold) }
                    items(upcoming, key = { "due_${it.recurring.id}_${it.dueDate}" }, contentType = { "upcoming" }) { bill ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("${bill.recurring.name} • ${bill.dueDate}", color = duit.textPrimary)
                            Text(if (bill.isOverdue) "Terlambat" else "Belum dicatat", color = duit.textSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.recordUpcomingBill(bill.recurring.id, bill.dueDate) }) { Text("Catat") }
                                OutlinedButton(onClick = { viewModel.skipUpcomingBill(bill.recurring.id, bill.dueDate) }) { Text("Lewati") }
                            }
                        }
                    }
                    item { HorizontalDivider(); Text("Semua jadwal", color = duit.textPrimary) }
                }
                items(recurringList, key = { it.id }, contentType = { "recurring" }) { item ->
                    val cat = remember(item.categoryId, categories) {
                        categories.firstOrNull { it.id == item.categoryId }
                    }
                    val asset = remember(item.assetId, assets) {
                        assets.firstOrNull { it.id == item.assetId }
                    }

                    DoubleBezelCard(
                        modifier = Modifier.fillMaxWidth(),
                        outerPadding = 3.dp,
                        outerRadius = 14.dp,
                        innerRadius = 11.dp,
                        shadowOffset = 0.dp,
                        innerColor = duit.surface,
                        onClick = {
                            editingItem = item
                            showFormDialog = true
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CategoryIcon(
                                        iconName = cat?.icon ?: "schedule",
                                        size = 36.dp,
                                        iconSize = 18.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            ),
                                            color = duit.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.frequencyDisplayName} • ${if (item.mode == "auto") "Otomatis" else "Ingatkan Dulu"}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = duit.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Switch(
                                        checked = item.isActive,
                                        onCheckedChange = { active ->
                                            viewModel.saveRecurringTransaction(item.copy(isActive = active))
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = duit.onPrimary,
                                            checkedTrackColor = duit.primary,
                                            uncheckedThumbColor = duit.textSecondary,
                                            uncheckedTrackColor = duit.surfaceVariant
                                        )
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteRecurringTransaction(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Hapus Jadwal",
                                            tint = duit.textSecondary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = duit.border, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = duit.textSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = asset?.name ?: "Aset",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = duit.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.linkedDebtId != null) {
                                        Text(
                                            text = "• Cicilan Hutang",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = duit.expense
                                        )
                                    }
                                }

                                Text(
                                    text = Formatters.displayRupiah(item.amount),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    ),
                                    color = if (item.type == TransactionType.INCOME.value) duit.income else duit.expense
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        RecurringFormDialog(
            existing = editingItem,
            categories = categories,
            assets = assets,
            debts = debts,
            onDismiss = { showFormDialog = false },
            onSave = { recurring ->
                viewModel.saveRecurringTransaction(recurring) { success ->
                    if (success) showFormDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringFormDialog(
    existing: RecurringTransaction?,
    categories: List<Category>,
    assets: List<Asset>,
    debts: List<Debt>,
    onDismiss: () -> Unit,
    onSave: (RecurringTransaction) -> Unit
) {
    val duit = MaterialTheme.duit
    var nameText by remember { mutableStateOf(existing?.name ?: "") }
    var selectedType by remember {
        mutableStateOf(if (existing != null) TransactionType.fromValue(existing.type) else TransactionType.EXPENSE)
    }
    var amountText by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var selectedCategoryId by remember {
        mutableStateOf(existing?.categoryId ?: categories.firstOrNull { it.type == "expense" }?.id ?: "")
    }
    var selectedAssetId by remember {
        mutableStateOf(existing?.assetId ?: assets.firstOrNull { it.isDefault }?.id ?: assets.firstOrNull()?.id ?: "")
    }
    var selectedDestAssetId by remember { mutableStateOf(existing?.destinationAssetId ?: "") }
    var frequency by remember {
        mutableStateOf(existing?.frequency ?: RecurringFrequency.MONTHLY.value)
    }
    var interval by remember { mutableStateOf(existing?.interval ?: 1) }
    var startDateText by remember {
        mutableStateOf(existing?.startDate ?: LocalDate.now().toString())
    }
    var mode by remember {
        mutableStateOf(existing?.mode ?: RecurringMode.AUTO.value)
    }
    var selectedDebtId by remember { mutableStateOf(existing?.linkedDebtId) }
    var noteText by remember { mutableStateOf(existing?.note ?: "") }
    var endDateText by remember { mutableStateOf(existing?.endDate.orEmpty()) }
    var countText by remember { mutableStateOf(existing?.totalOccurrences?.toString().orEmpty()) }

    val amount = amountText.toLongOrNull() ?: 0L
    val isValid = nameText.isNotBlank() && amount > 0 && selectedAssetId.isNotBlank() && interval in 1..365 &&
        runCatching { val start = LocalDate.parse(startDateText); endDateText.isBlank() || !LocalDate.parse(endDateText).isBefore(start) }.getOrDefault(false) &&
        (countText.isBlank() || (countText.toIntOrNull() ?: 0) > 0) &&
        (selectedType != TransactionType.TRANSFER || (selectedDestAssetId.isNotBlank() && selectedDestAssetId != selectedAssetId))

    val relevantCategories = remember(selectedType, categories) {
        val typeStr = if (selectedType == TransactionType.INCOME) "income" else "expense"
        categories.filter { it.type == typeStr && it.isActive }
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
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {},
                colors = CardDefaults.cardColors(containerColor = duit.surface),
                border = BorderStroke(1.dp, duit.border)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .heightIn(max = 650.dp)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    Text(
                        text = if (existing != null) "Edit Jadwal Berulang" else "Tambah Jadwal Berulang",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        ),
                        color = duit.textPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Name
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Nama Jadwal (mis: Wifi Indihome)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = duit.surface,
                            unfocusedContainerColor = duit.surface,
                            focusedBorderColor = duit.primary,
                            unfocusedBorderColor = duit.border
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Amount
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                        label = { Text("Nominal (Rp)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = duit.surface,
                            unfocusedContainerColor = duit.surface,
                            focusedBorderColor = duit.primary,
                            unfocusedBorderColor = duit.border
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER).forEach { type ->
                            FilterChip(selected = selectedType == type, onClick = { selectedType = type; selectedCategoryId = categories.firstOrNull { it.type == type.value }?.id.orEmpty() }, label = { Text(type.displayName) })
                        }
                    }
                    AssetDropdownField(assets = assets, selectedAssetId = selectedAssetId, onAssetSelected = { selectedAssetId = it.id }, placeholder = "Rekening sumber")
                    if (selectedType == TransactionType.TRANSFER) {
                        AssetDropdownField(assets = assets.filter { it.id != selectedAssetId }, selectedAssetId = selectedDestAssetId, onAssetSelected = { selectedDestAssetId = it.id }, placeholder = "Rekening tujuan")
                    } else {
                        CategoryDropdownField(categories = relevantCategories, selectedCategoryId = selectedCategoryId, onCategorySelected = { selectedCategoryId = it.id })
                    }
                    OutlinedTextField(value = startDateText, onValueChange = { startDateText = it }, label = { Text("Mulai (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = interval.toString(), onValueChange = { interval = it.toIntOrNull() ?: 0 }, label = { Text("Setiap berapa periode (1–365)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = endDateText, onValueChange = { endDateText = it }, label = { Text("Berakhir (YYYY-MM-DD, opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = countText, onValueChange = { countText = it }, label = { Text("Jumlah pengulangan (opsional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth())

                    // Frequency row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            RecurringFrequency.MONTHLY.value to "Bulanan",
                            RecurringFrequency.WEEKLY.value to "Mingguan",
                            RecurringFrequency.DAILY.value to "Harian"
                        ).forEach { (fVal, label) ->
                            val isSelected = frequency == fVal
                            OutlinedButton(
                                onClick = { frequency = fVal },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) duit.primary else duit.surfaceVariant
                                ),
                                border = BorderStroke(1.dp, if (isSelected) duit.primary else duit.border)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) duit.onPrimary else duit.textSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode: Otomatis vs Ingatkan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            RecurringMode.AUTO.value to "Otomatis Catat",
                            RecurringMode.REMIND.value to "Ingatkan Dulu"
                        ).forEach { (mVal, label) ->
                            val isSelected = mode == mVal
                            OutlinedButton(
                                onClick = { mode = mVal },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) duit.primary else duit.surfaceVariant
                                ),
                                border = BorderStroke(1.dp, if (isSelected) duit.primary else duit.border)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) duit.onPrimary else duit.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, duit.border)
                        ) {
                            Text("Batal", color = duit.textSecondary)
                        }

                        Button(
                            onClick = {
                                if (isValid) {
                                    val item = RecurringTransaction(
                                        id = existing?.id ?: "rec_${UUID.randomUUID()}",
                                        name = nameText.trim(),
                                        type = selectedType.value,
                                        amount = amount,
                                        categoryId = if (selectedType != TransactionType.TRANSFER) selectedCategoryId else null,
                                        assetId = selectedAssetId,
                                        destinationAssetId = if (selectedType == TransactionType.TRANSFER) selectedDestAssetId else null,
                                        frequency = frequency,
                                        interval = interval,
                                        startDate = startDateText,
                                        endDate = endDateText.ifBlank { null },
                                        totalOccurrences = countText.toIntOrNull(),
                                        mode = mode,
                                        linkedDebtId = selectedDebtId,
                                        isActive = existing?.isActive ?: true,
                                        note = noteText
                                    )
                                    onSave(item)
                                }
                            },
                            enabled = isValid,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = duit.primary,
                                contentColor = duit.onPrimary
                            )
                        ) {
                            Text("Simpan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
