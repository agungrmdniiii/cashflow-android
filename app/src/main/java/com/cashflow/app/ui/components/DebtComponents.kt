package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Eyebrow tag & Overdue warning
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                text = if (isBalanceHidden) "Rp ••••••••" else Formatters.formatRupiah(totalRemaining),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    letterSpacing = if (isBalanceHidden) 2.sp else (-1).sp
                ),
                color = TextOnDark
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
                    tint = JadePrimaryLight,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBalanceHidden) "Kekayaan Bersih: ••••••" else "Kekayaan Bersih: ${Formatters.formatRupiah(netWorth)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = TextOnDarkSecondary
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
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextOnDarkSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isBalanceHidden) "••••••" else Formatters.formatRupiah(totalPaid),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        ),
                        color = JadePrimaryLight
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "HUTANG AKTIF",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextOnDarkSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$activeCount Pinjaman",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        ),
                        color = if (overdueCount > 0) Color(0xFFFF8E8E) else TextOnDark
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
    DoubleBezelCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shadowOffset = 2.dp,
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
                        color = DarkSurface
                    )
                    Text(
                        text = "Pemberi pinjaman: ${debt.lenderName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = TextSecondary
                    )
                }

                // Status Badge
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (debt.status == DebtStatus.PAID.key) {
                        EyebrowTag(text = "LUNAS", variant = EyebrowVariant.JADE)
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
                            fontSize = 9.5.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = if (isBalanceHidden) "Rp ••••••" else Formatters.formatRupiah(debt.remainingAmount),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (debt.status == DebtStatus.PAID.key) JadePrimary else DarkSurface
                    )
                }

                Text(
                    text = "Total: ${if (isBalanceHidden) "••••••" else Formatters.formatRupiah(debt.totalAmount)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = TextSecondary
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
                color = JadePrimary,
                trackColor = PebbleSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Progress text & Due Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(debt.progressPercentage * 100).toInt()}% Terbayar (${if (isBalanceHidden) "••••••" else Formatters.formatRupiah(debt.paidAmount)})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = JadePrimary
                )

                Text(
                    text = "Jatuh tempo: ${Formatters.formatDateReadable(debt.dueDate)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = if (debt.isOverdue) StatusNegative else TextSecondary
                )
            }

            if (debt.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Catatan: ${debt.note}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.5.sp
                    ),
                    color = TextSecondary
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
                        .background(PebbleSurfaceVariant)
                        .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onDeleteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Hapus Hutang",
                        tint = TextSecondary,
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
                        shadowOffset = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = JadePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Lunas Sepenuhnya",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            ),
                            color = JadePrimary
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
    onSave: (title: String, lender: String, amount: Long, dueDate: String, note: String, receiveAssetId: String?) -> Unit
) {
    if (!isOpen) return

    var title by remember { mutableStateOf("") }
    var lender by remember { mutableStateOf("") }
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

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "CATAT HUTANG BARU",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = DarkSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Rekam kewajiban pinjaman dan jadwalkan cicilan",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
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
                        prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = JadePrimary) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ThousandSeparatorVisualTransformation(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (amount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Terbaca: ${Formatters.formatRupiah(amount)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = JadePrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) {
                                        val current = amountText.toLongOrNull() ?: 0L
                                        amountText = (current + presetVal).toString()
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    ),
                                    color = DarkSurface
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

                // 5. Option: Receive Funds into an Asset
                if (assets.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PebbleSurfaceVariant)
                            .border(1.dp, DarkBorder.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { receiveToAsset = !receiveToAsset }
                        ) {
                            Checkbox(
                                checked = receiveToAsset,
                                onCheckedChange = { receiveToAsset = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Terima dana ke Aset / Rekening",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = DarkSurface
                                )
                                Text(
                                    text = "Tambah saldo & catat transaksi pemasukan",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = TextSecondary
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
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusNegative,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 44.dp,
                    shadowOffset = 2.dp
                )

                DuitAingButton(
                    text = "SIMPAN HUTANG",
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.3f),
                    height = 44.dp,
                    shadowOffset = 2.dp,
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
                        onSave(title, lender, amount, dueDate, note, targetAsset)
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
    val selectedAsset = remember(selectedAssetId, assets) {
        assets.firstOrNull { it.id == selectedAssetId }
    }

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "CATAT PEMBAYARAN CICILAN",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = DarkSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${debt.title} • ${debt.lenderName}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = TextSecondary
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
                        .background(PebbleSurfaceVariant)
                        .border(1.dp, DarkBorder.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "SISA KEWAJIBAN SAAT INI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Formatters.formatRupiah(debt.remainingAmount),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = DarkSurface
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
                        color = TextSecondary
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
                            text = "Saldo tersedia: ${Formatters.formatRupiah(selectedAsset.currentBalance)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (selectedAsset.currentBalance >= payAmount) TextSecondary else StatusNegative
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
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it.filter { c -> c.isDigit() } },
                        prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = JadePrimary) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ThousandSeparatorVisualTransformation(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (payAmount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Terbaca: ${Formatters.formatRupiah(payAmount)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = JadePrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Action Chips: Lunasi Semua, 50%, 25%, +100rb, +500rb
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(JadePrimaryLight)
                                .border(1.dp, JadePrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) { paymentAmountText = debt.remainingAmount.toString() }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LUNASI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.5.sp
                                ),
                                color = JadePrimary
                            )
                        }

                        if (debt.remainingAmount > 100_000L) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) { paymentAmountText = (debt.remainingAmount / 2).toString() }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "50%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    ),
                                    color = DarkSurface
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PebbleSurfaceVariant)
                                    .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button) {
                                        val cur = paymentAmountText.toLongOrNull() ?: 0L
                                        val next = (cur + presetAmount).coerceAtMost(debt.remainingAmount)
                                        paymentAmountText = next.toString()
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    ),
                                    color = DarkSurface
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
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = StatusNegative,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DuitAingButton(
                    text = "BATAL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 44.dp,
                    shadowOffset = 2.dp
                )

                DuitAingButton(
                    text = "CATAT PEMBAYARAN",
                    variant = DuitAingButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.4f),
                    height = 44.dp,
                    shadowOffset = 2.dp,
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
                            errorMessage = "Nominal melebihi sisa hutang (${Formatters.formatRupiah(debt.remainingAmount)})"
                            return@DuitAingButton
                        }
                        onConfirmPay(debt.id, payAmount, selectedAssetId, paymentDate, note)
                    }
                )
            }
        }
    }
}
