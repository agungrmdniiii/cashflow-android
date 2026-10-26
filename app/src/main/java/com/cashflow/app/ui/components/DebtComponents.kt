package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.DebtStatus
import com.cashflow.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Editorial Neo-Brutalist Summary Card for Debts
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DebtSummaryCard(
    totalRemaining: Long,
    totalPaid: Long,
    activeCount: Int,
    overdueCount: Int,
    netWorth: Long,
    modifier: Modifier = Modifier,
    isBalanceHidden: Boolean = false
) {
    DarkDoubleBezelCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Eyebrow tag & Overdue warning
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EyebrowTag(
                    text = "TOTAL KEWAJIBAN HUTANG",
                    variant = EyebrowVariant.DARK
                )

                if (overdueCount > 0) {
                    EyebrowTag(
                        text = "$overdueCount JATUH TEMPO",
                        variant = EyebrowVariant.NEGATIVE
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Big remaining amount (Masked or Live)
            Text(
                text = if (isBalanceHidden) "Rp ••••••••" else Formatters.displayRupiah(totalRemaining),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    letterSpacing = if (isBalanceHidden) 2.sp else (-1).sp
                ),
                color = MaterialTheme.duit.onHero
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Net Worth indication badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.duit.heroAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBalanceHidden) "Kekayaan Bersih: ••••••" else "Kekayaan Bersih: ${Formatters.displayRupiah(netWorth)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.duit.onHeroSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-metrics divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2-column secondary stats with AAA contrast
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SUDAH DICICIL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = MaterialTheme.duit.onHeroSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isBalanceHidden) "••••••" else Formatters.displayRupiah(totalPaid),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.duit.heroAccent
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "HUTANG AKTIF",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = MaterialTheme.duit.onHeroSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$activeCount Pinjaman",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        ),
                        color = if (overdueCount > 0) MaterialTheme.duit.expense else MaterialTheme.duit.onHero
                    )
                }
            }
        }
    }
}

/**
 * Editorial Neo-Brutalist Debt Item Card
 */
@Composable
fun DebtItemCard(
    debt: Debt,
    onPayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBalanceHidden: Boolean = false
) {
    val duit = MaterialTheme.duit
    DoubleBezelCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shadowOffset = 0.dp,
        outerPadding = 3.dp,
        outerRadius = 16.dp,
        innerRadius = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title & Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = debt.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.2).sp
                        ),
                        color = duit.textPrimary
                    )
                    Text(
                        text = "${if (debt.direction == "receivable") "Piutang • Peminjam" else "Hutang • Pemberi pinjaman"}: ${debt.lenderName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = duit.textSecondary
                    )
                }

                // Status Badge
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (debt.status == DebtStatus.PAID.key) {
                        EyebrowTag(text = "LUNAS", variant = EyebrowVariant.PRIMARY)
                    } else {
                        if (debt.isOverdue) {
                            EyebrowTag(text = "JATUH TEMPO", variant = EyebrowVariant.NEGATIVE)
                        } else if (debt.status == DebtStatus.PARTIAL.key) {
                            EyebrowTag(text = "SEBAGIAN", variant = EyebrowVariant.POSITIVE)
                        } else {
                            EyebrowTag(text = "BELUM LUNAS", variant = EyebrowVariant.DARK)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Remaining Amount (prominent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "SISA PEMBAYARAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = duit.textSecondary
                    )
                    Text(
                        text = if (isBalanceHidden) "Rp ••••••" else Formatters.displayRupiah(debt.remainingAmount),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (debt.status == DebtStatus.PAID.key) duit.primary else duit.textPrimary
                    )
                }

                Text(
                    text = "Total: ${if (isBalanceHidden) "••••••" else Formatters.displayRupiah(debt.totalAmount)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = duit.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { debt.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = duit.primary,
                trackColor = duit.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Progress text & Due Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(debt.progressPercentage * 100).toInt()}% Terbayar (${if (isBalanceHidden) "••••••" else Formatters.displayRupiah(debt.paidAmount)})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = duit.primary
                )

                Text(
                    text = "Jatuh tempo: ${Formatters.formatDateReadable(debt.dueDate)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = if (debt.isOverdue) duit.expense else duit.textSecondary
                )
            }

            if (debt.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Catatan: ${debt.note}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp
                    ),
                    color = duit.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Actions: Pay button and Delete action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button with accessible >= 48dp touch target
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(duit.surfaceVariant)
                        .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onDeleteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Hapus Hutang",
                        tint = duit.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Pay / Installment button (only active if not paid)
                if (debt.status != DebtStatus.PAID.key) {
                    DuitAingButton(
                        text = "CICIL / BAYAR",
                        onClick = onPayClick,
                        leadingIcon = Icons.Rounded.Payment,
                        variant = DuitAingButtonVariant.PRIMARY,
                        height = 44.dp,
                        shadowOffset = 0.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = duit.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Lunas Sepenuhnya",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            ),
                            color = duit.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog to add a new Debt record with optional asset receipt integration
 */
@Composable
fun AddDebtDialog(
    isOpen: Boolean,
    assets: List<Asset> = emptyList(),
    defaultAssetId: String? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, lender: String, amount: Long, dueDate: String, note: String, receiveAssetId: String?, direction: String) -> Unit
) {
    if (!isOpen) return

    var title by remember { mutableStateOf("") }
    var lender by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("debt") }
    var amountText by remember { mutableStateOf("") }
    var dueDate by remember {
        mutableStateOf(LocalDate.now().plusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var note by remember { mutableStateOf("") }
    var receiveToAsset by remember { mutableStateOf(false) }
    var selectedReceiveAssetId by remember {
        mutableStateOf(defaultAssetId ?: assets.firstOrNull()?.id ?: "")
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val amount = amountText.toLongOrNull() ?: 0L
    val focusManager = LocalFocusManager.current
    val duit = MaterialTheme.duit

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "CATAT HUTANG / PIUTANG",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = duit.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Rekam kewajiban pinjaman dan jadwalkan cicilan",
                style = MaterialTheme.typography.bodySmall,
                color = duit.textSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Title
                Column {
                    Text(
                        text = "TUJUAN / NAMA PINJAMAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Contoh: Renovasi Dapur, Modal Usaha") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. Lender
                Column {
                    Text(
                        text = "PEMBERI PINJAMAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = lender,
                        onValueChange = { lender = it },
                        placeholder = { Text("Contoh: Bank BCA, Budi, Koperasi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Amount
                Column {
                    Text(
                        text = "TOTAL NOMINAL PINJAMAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = duit.primary) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        visualTransformation = ThousandSeparatorVisualTransformation(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (amount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Terbaca: ${Formatters.displayRupiah(amount)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = duit.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        listOf(
                            500_000L to "+500rb",
                            1_000_000L to "+1jt",
                            5_000_000L to "+5jt",
                            10_000_000L to "+10jt"
                        ).forEach { (presetVal, chipLabel) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(duit.surfaceVariant)
                                    .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) {
                                        val current = amountText.toLongOrNull() ?: 0L
                                        amountText = (current + presetVal).toString()
                                    }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.textPrimary
                                )
                            }
                        }
                    }
                }

                // 4. Due Date
                DuitAingDatePickerField(
                    dateIsoString = dueDate,
                    onDateSelected = { dueDate = it },
                    label = "Jatuh Tempo Pinjaman",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == "debt", onClick = { direction = "debt" }, label = { Text("Saya berhutang") })
                    FilterChip(selected = direction == "receivable", onClick = { direction = "receivable" }, label = { Text("Saya meminjamkan") })
                }
                // 5. Option: Receive Funds into an Asset
                if (assets.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(duit.surfaceVariant)
                            .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable(role = Role.Checkbox) { receiveToAsset = !receiveToAsset }
                        ) {
                            Checkbox(
                                checked = receiveToAsset,
                                onCheckedChange = { receiveToAsset = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = duit.primary,
                                    checkmarkColor = duit.onPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (direction == "receivable") "Keluarkan dana dari rekening" else "Terima dana ke rekening",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = duit.textPrimary
                                )
                                Text(
                                    text = "Opsional untuk dana yang bergerak sekarang; tanpa ini hanya catat saldo pinjaman lama.",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                    color = duit.textSecondary
                                )
                            }
                        }

                        if (receiveToAsset) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AssetDropdownField(
                                assets = assets,
                                selectedAssetId = selectedReceiveAssetId,
                                onAssetSelected = { selectedReceiveAssetId = it.id },
                                placeholder = "PILIH ASET PENERIMA",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 6. Notes
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan Tambahan (Opsional)") },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = duit.expense,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 44.dp,
                    shadowOffset = 0.dp
                )

                DuitAingButton(
                    text = "SIMPAN HUTANG",
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.3f),
                    height = 44.dp,
                    shadowOffset = 0.dp,
                    onClick = {
                        if (title.isBlank() || lender.isBlank()) {
                            errorMessage = "Nama pinjaman dan pemberi pinjaman wajib diisi"
                            return@DuitAingButton
                        }
                        if (amount <= 0) {
                            errorMessage = "Nominal pinjaman harus lebih dari 0"
                            return@DuitAingButton
                        }
                        if (receiveToAsset && selectedReceiveAssetId.isBlank()) {
                            errorMessage = "Pilih aset penerima dana pinjaman"
                            return@DuitAingButton
                        }
                        val targetAsset = if (receiveToAsset) selectedReceiveAssetId else null
                        onSave(title, lender, amount, dueDate, note, targetAsset, direction)
                    }
                )
            }
        }
    }
}

/**
 * Dialog to record an installment/payment towards a Debt.
 * Integrates with source Asset to deduct balance and record an EXPENSE transaction.
 */
@Composable
fun PayDebtDialog(
    debt: Debt?,
    assets: List<Asset>,
    defaultAssetId: String? = null,
    onDismiss: () -> Unit,
    onConfirmPay: (debtId: String, amount: Long, sourceAssetId: String, paymentDate: String, note: String) -> Unit
) {
    if (debt == null) return

    var selectedAssetId by remember {
        mutableStateOf(defaultAssetId ?: assets.firstOrNull()?.id ?: "")
    }
    var paymentAmountText by remember { mutableStateOf("") }
    var paymentDate by remember {
        mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val payAmount = paymentAmountText.toLongOrNull() ?: 0L
    val focusManager = LocalFocusManager.current
    val duit = MaterialTheme.duit
    val selectedAsset = remember(selectedAssetId, assets) {
        assets.firstOrNull { it.id == selectedAssetId }
    }

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "CATAT PEMBAYARAN CICILAN",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = duit.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${debt.title} • ${debt.lenderName}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = duit.textSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info Box: Sisa Hutang
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(duit.surfaceVariant)
                        .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "SISA KEWAJIBAN SAAT INI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.8.sp
                            ),
                            color = duit.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Formatters.displayRupiah(debt.remainingAmount),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = duit.textPrimary
                        )
                    }
                }

                // 1. Source Asset Selector
                Column {
                    Text(
                        text = "BAYAR DARI ASET / REKENING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = duit.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    AssetDropdownField(
                        assets = assets,
                        selectedAssetId = selectedAssetId,
                        onAssetSelected = { selectedAssetId = it.id },
                        placeholder = "PILIH SUMBER DANA",
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedAsset != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Saldo tersedia: ${Formatters.displayRupiah(selectedAsset.currentBalance)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (selectedAsset.currentBalance >= payAmount) duit.textSecondary else duit.expense
                        )
                    }
                }

                // 2. Input Payment Amount
                Column {
                    Text(
                        text = "NOMINAL PEMBAYARAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = duit.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it.filter { c -> c.isDigit() } },
                        prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = duit.primary) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        visualTransformation = ThousandSeparatorVisualTransformation(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (payAmount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Terbaca: ${Formatters.displayRupiah(payAmount)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = duit.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Action Chips: Lunasi Semua, 50%, 25%, +100rb, +500rb
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(duit.primaryContainer)
                                .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) { paymentAmountText = debt.remainingAmount.toString() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LUNASI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                ),
                                color = duit.onPrimaryContainer
                            )
                        }

                        if (debt.remainingAmount > 100_000L) {
                            Box(
                                modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(duit.surfaceVariant)
                                .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) { paymentAmountText = (debt.remainingAmount / 2).toString() }
                                .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "50%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.textPrimary
                                )
                            }
                        }

                        listOf(
                            100_000L to "+100rb",
                            500_000L to "+500rb"
                        ).forEach { (presetAmount, chipLabel) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(duit.surfaceVariant)
                                    .border(1.dp, duit.border, RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) {
                                        val cur = paymentAmountText.toLongOrNull() ?: 0L
                                        val next = (cur + presetAmount).coerceAtMost(debt.remainingAmount)
                                        paymentAmountText = next.toString()
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = duit.textPrimary
                                )
                            }
                        }
                    }
                }

                // 3. Payment Date
                DuitAingDatePickerField(
                    dateIsoString = paymentDate,
                    onDateSelected = { paymentDate = it },
                    label = "Tanggal Pembayaran",
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. Notes (Optional)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan Pembayaran (Opsional)") },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = duit.expense,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 44.dp,
                    shadowOffset = 0.dp
                )

                DuitAingButton(
                    text = "CATAT PEMBAYARAN",
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.4f),
                    height = 44.dp,
                    shadowOffset = 0.dp,
                    onClick = {
                        if (selectedAssetId.isBlank()) {
                            errorMessage = "Pilih aset/rekening sumber pembayaran"
                            return@DuitAingButton
                        }
                        if (payAmount <= 0) {
                            errorMessage = "Nominal pembayaran harus lebih dari 0"
                            return@DuitAingButton
                        }
                        if (payAmount > debt.remainingAmount) {
                            errorMessage = "Nominal melebihi sisa hutang."
                            return@DuitAingButton
                        }
                        onConfirmPay(debt.id, payAmount, selectedAssetId, paymentDate, note)
                    }
                )
            }
        }
    }
}
