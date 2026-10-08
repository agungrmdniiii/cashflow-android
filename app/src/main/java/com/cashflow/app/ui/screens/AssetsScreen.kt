package com.cashflow.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.DebtStatus
import com.cashflow.app.data.model.FinancialGoal
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
import java.time.LocalDate

/**
 * Editorial Asset & Savings Management for "Duit Aing"
 * Open layout emphasizing balances and clean lists rather than bulky containers.
 */
@Composable
fun AssetsScreen(
    viewModel: CashflowViewModel,
    modifier: Modifier = Modifier
) {
    val totalWealth by viewModel.totalWealth.collectAsState()
    val activeAssets by viewModel.activeAssets.collectAsState()
    val defaultAsset by viewModel.defaultAsset.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()

    val debts by viewModel.debts.collectAsState()
    val totalRemainingDebt by viewModel.totalRemainingDebt.collectAsState()
    val totalPaidDebt by viewModel.totalPaidDebt.collectAsState()
    val activeDebtsCount by viewModel.activeDebtsCount.collectAsState()
    val overdueDebtsCount by viewModel.overdueDebtsCount.collectAsState()
    val netWorth by viewModel.netWorth.collectAsState()
    val subTab by viewModel.assetScreenSubTab.collectAsState()
    val selectedDebtFilter by viewModel.selectedDebtFilter.collectAsState()
    val isAddDebtOpen by viewModel.isAddDebtOpen.collectAsState()
    val payingDebt by viewModel.payingDebt.collectAsState()
    var debtToDelete by remember { mutableStateOf<Debt?>(null) }
    var goalToDelete by remember { mutableStateOf<FinancialGoal?>(null) }

    val filteredDebts = remember(debts, selectedDebtFilter) {
        when (selectedDebtFilter) {
            "ACTIVE" -> debts.filter { it.status != DebtStatus.PAID.key }
            "PAID" -> debts.filter { it.status == DebtStatus.PAID.key }
            else -> debts
        }
    }

    var showAddGoalDialog by remember { mutableStateOf(false) }
    var goalNameInput by remember { mutableStateOf("") }
    var goalTargetInput by remember { mutableStateOf("") }
    var goalDeadlineInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PebbleBackground),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // 1. Top Bar Header with spacious layout
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "ASET & HUTANG",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp,
                            fontSize = 22.sp
                        ),
                        color = DarkSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    EyebrowTag(
                        text = if (subTab == 0) "${activeAssets.size} ASET AKTIF" else "$activeDebtsCount KEWAJIBAN AKTIF",
                        variant = EyebrowVariant.DEFAULT
                    )
                }

                DuitAingButton(
                    text = if (subTab == 0) "TAMBAH ASET" else "TAMBAH HUTANG",
                    onClick = {
                        if (subTab == 0) {
                            viewModel.editingAsset.value = null
                            viewModel.isAddAssetOpen.value = true
                        } else {
                            viewModel.isAddDebtOpen.value = true
                        }
                    },
                    leadingIcon = Icons.Rounded.Add,
                    variant = DuitAingButtonVariant.PRIMARY,
                    height = 44.dp,
                    shadowOffset = 2.dp
                )
            }
        }

        // 2. Segmented Switcher (ASET SAYA | HUTANG SAYA) with accessible touch targets
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PebbleSurface)
                    .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                    .padding(3.dp)
            ) {
                // Tab 0: ASET SAYA
                val tab0Bg by animateColorAsState(
                    targetValue = if (subTab == 0) JadePrimary else Color.Transparent,
                    animationSpec = tween(durationMillis = 180, easing = DuitAingMotion.SubtleEasing),
                    label = "Tab0Bg"
                )
                val tab0TextCol by animateColorAsState(
                    targetValue = if (subTab == 0) TextOnDark else DarkSurface,
                    animationSpec = tween(durationMillis = 180, easing = DuitAingMotion.SubtleEasing),
                    label = "Tab0Text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tab0Bg)
                        .clickable(role = Role.Tab) { viewModel.setAssetScreenSubTab(0) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ASET SAYA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = tab0TextCol
                    )
                }

                // Tab 1: HUTANG SAYA
                val tab1Bg by animateColorAsState(
                    targetValue = if (subTab == 1) JadePrimary else Color.Transparent,
                    animationSpec = tween(durationMillis = 180, easing = DuitAingMotion.SubtleEasing),
                    label = "Tab1Bg"
                )
                val tab1TextCol by animateColorAsState(
                    targetValue = if (subTab == 1) TextOnDark else DarkSurface,
                    animationSpec = tween(durationMillis = 180, easing = DuitAingMotion.SubtleEasing),
                    label = "Tab1Text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tab1Bg)
                        .clickable(role = Role.Tab) { viewModel.setAssetScreenSubTab(1) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "HUTANG SAYA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = tab1TextCol
                        )
                        if (activeDebtsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (subTab == 1) TextOnDark else DarkBorder)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$activeDebtsCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp
                                    ),
                                    color = if (subTab == 1) JadePrimary else PebbleSurface
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        if (subTab == 0) {
            // 2. Total Wealth Focal Point with Concentric DarkDoubleBezelCard Architecture
            item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                DarkDoubleBezelCard(
                    modifier = Modifier.fillMaxWidth(),
                    outerPadding = 5.dp,
                    outerRadius = 20.dp,
                    innerRadius = 15.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EyebrowTag(
                                text = "TOTAL KEKAYAAN BERSIH",
                                variant = EyebrowVariant.JADE
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Balance Privacy Toggle (Accessible >= 48dp touch target)
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable(role = Role.Button) { viewModel.toggleBalanceVisibility() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (isBalanceHidden) "Tampilkan nominal saldo" else "Sembunyikan nominal saldo",
                                        tint = JadePrimaryLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                EyebrowTag(
                                    text = "${activeAssets.size} ASET AKTIF",
                                    variant = EyebrowVariant.DARK
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isBalanceHidden) {
                            Text(
                                text = "Rp ••••••••",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp
                                ),
                                color = TextOnDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        } else {
                            AnimatedNumberText(
                                value = totalWealth,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1.2).sp
                                ),
                                color = TextOnDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Konsolidasi saldo dari seluruh rekening, e-wallet, dan instrumen simpanan",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                            color = TextOnDarkSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(26.dp))
        }

        // 3. Assets Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DAFTAR ASET",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = DarkSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EyebrowTag(
                        text = "${activeAssets.size} TERDAFTAR",
                        variant = EyebrowVariant.DEFAULT
                    )
                }

                DuitAingButton(
                    text = "TRANSFER",
                    onClick = {
                        viewModel.addTransactionInitialType.value = TransactionType.TRANSFER
                        viewModel.editingTransaction.value = null
                        viewModel.isAddTransactionOpen.value = true
                    },
                    leadingIcon = Icons.Rounded.SwapHoriz,
                    variant = DuitAingButtonVariant.SECONDARY,
                    height = 40.dp,
                    shadowOffset = 2.dp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Render Active Assets as Open List Items
        items(activeAssets, key = { it.id }) { asset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { viewModel.selectedAssetForDetail.value = asset }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(JadePrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (asset.type.lowercase()) {
                                "tunai" -> Icons.Rounded.LocalAtm
                                "bank" -> Icons.Rounded.AccountBalance
                                "e-wallet" -> Icons.Rounded.PhoneAndroid
                                "tabungan" -> Icons.Rounded.Savings
                                "kartu kredit" -> Icons.Rounded.CreditCard
                                else -> Icons.Rounded.Wallet
                            },
                            contentDescription = null,
                            tint = JadePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = asset.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = DarkSurface
                            )
                            if (asset.isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                AssetBadge(text = "UTAMA")
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = asset.type.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp
                            ),
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isBalanceHidden) "••••••" else Formatters.formatRupiah(asset.currentBalance),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        color = DarkSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(
                color = PebbleBorder,
                thickness = 0.8.dp,
                modifier = Modifier.padding(start = 72.dp, end = 20.dp)
            )
        }

        // 4. Section: Target Keuangan (Financial Goals)
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TARGET KEUANGAN",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = DarkSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EyebrowTag(
                            text = "${goals.size} TARGET",
                            variant = EyebrowVariant.DEFAULT
                        )
                    }
                    Text(
                        text = "RENCANA & IMPIAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = TextSecondary
                    )
                }

                DuitAingButton(
                    text = "BUAT TARGET",
                    onClick = {
                        goalNameInput = ""
                        goalTargetInput = ""
                        goalDeadlineInput = "2026-12-31"
                        showAddGoalDialog = true
                    },
                    leadingIcon = Icons.Rounded.Add,
                    variant = DuitAingButtonVariant.SECONDARY,
                    height = 40.dp,
                    shadowOffset = 2.dp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (goals.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "BELUM ADA TARGET",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = DarkSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tetapkan target dana darurat, liburan, atau pembelian penting.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    DuitAingButton(
                        text = "BUAT TARGET PERTAMA",
                        onClick = {
                            goalNameInput = ""
                            goalTargetInput = ""
                            goalDeadlineInput = "2026-12-31"
                            showAddGoalDialog = true
                        },
                        leadingIcon = Icons.Rounded.Add,
                        variant = DuitAingButtonVariant.SECONDARY,
                        height = 44.dp,
                        shadowOffset = 2.dp
                    )
                }
            }
        } else {
            items(goals, key = { it.id }) { goal ->
                DoubleBezelCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shadowOffset = 2.dp,
                    outerPadding = 3.dp,
                    outerRadius = 14.dp,
                    innerRadius = 11.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = goal.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    ),
                                    color = DarkSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "TARGET: ${Formatters.formatDateReadable(goal.deadline)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = TextSecondary
                                )
                            }

                            IconButton(
                                onClick = { goalToDelete = goal },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Hapus Target Keuangan",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { goal.progressPercentage.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = JadePrimary,
                            trackColor = PebbleSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Terkumpul: ${if (isBalanceHidden) "••••••" else Formatters.formatRupiah(goal.currentAmount)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                ),
                                color = JadePrimary
                            )
                            Text(
                                text = "Target: ${if (isBalanceHidden) "••••••" else Formatters.formatRupiah(goal.targetAmount)} (${(goal.progressPercentage * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (subTab == 1) {
        // 1. Debt Summary Card
        item {
            DebtSummaryCard(
                totalRemaining = totalRemainingDebt,
                totalPaid = totalPaidDebt,
                activeCount = activeDebtsCount,
                overdueCount = overdueDebtsCount,
                netWorth = netWorth,
                isBalanceHidden = isBalanceHidden
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Filter Chips Row (Accessible >= 48dp touch targets)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf(
                    "ALL" to "SEMUA (${debts.size})",
                    "ACTIVE" to "BELUM LUNAS ($activeDebtsCount)",
                    "PAID" to "LUNAS (${debts.count { it.status == DebtStatus.PAID.key }})"
                )

                filters.forEach { (key, label) ->
                    val isSelected = selectedDebtFilter == key
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) DarkSurface else PebbleSurface)
                            .border(
                                1.dp,
                                if (isSelected) DarkBorder.copy(alpha = 0.35f) else DarkBorder.copy(alpha = 0.20f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(role = Role.Button) { viewModel.setDebtFilter(key) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) TextOnDark else DarkSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 3. Debt List or Celebratory Empty State
        if (filteredDebts.isEmpty()) {
            item {
                DoubleBezelCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    outerPadding = 3.dp,
                    outerRadius = 18.dp,
                    innerRadius = 14.dp,
                    shadowOffset = 2.dp,
                    innerColor = PebbleSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Celebratory Minted Seal Badge
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(JadePrimaryLight)
                                .border(1.dp, JadePrimary.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = JadePrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status Tag
                        EyebrowTag(
                            text = if (selectedDebtFilter == "LUNAS") "SEMUA TERCATAT LUNAS" else "100% BEBAS HUTANG",
                            variant = EyebrowVariant.JADE
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (selectedDebtFilter == "LUNAS")
                                "Semua Cicilan Telah Tuntas"
                            else
                                "Merdeka Finansial Tanpa Cicilan",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.5.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = DarkSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (selectedDebtFilter == "LUNAS")
                                "Catatan pinjaman yang sudah Anda selesaikan tersimpan aman sebagai rekam jejak kedisiplinan keuangan."
                            else
                                "Tidak ada kewajiban hutang yang menggantung. Setiap rupiah arus kasmu 100% milik ketenangan masa depanmu.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            ),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredDebts, key = { it.id }) { debt ->
                DebtItemCard(
                    debt = debt,
                    onPayClick = { viewModel.payingDebt.value = debt },
                    onDeleteClick = { debtToDelete = debt },
                    isBalanceHidden = isBalanceHidden
                )
            }
        }
    }
}

    // Add Goal Dialog
    if (showAddGoalDialog) {
        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
            containerColor = PebbleSurface,
            title = {
                Text(
                    text = "TARGET KEUANGAN BARU",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = DarkSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = goalNameInput,
                        onValueChange = { goalNameInput = it },
                        label = { Text("Nama Target (misal: Liburan)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = goalTargetInput,
                        onValueChange = { goalTargetInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Nominal Target (Rp)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DuitAingDatePickerField(
                        dateIsoString = goalDeadlineInput.ifBlank { LocalDate.now().plusMonths(3).toString() },
                        onDateSelected = { goalDeadlineInput = it },
                        label = "Tenggat Waktu / Deadline",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                DuitAingButton(
                    text = "SIMPAN TARGET",
                    onClick = {
                        val amount = goalTargetInput.toLongOrNull() ?: 0L
                        if (goalNameInput.isNotBlank() && amount > 0) {
                            val newGoal = FinancialGoal(
                                id = "goal_${System.currentTimeMillis()}",
                                name = goalNameInput,
                                targetAmount = amount,
                                currentAmount = 0L,
                                deadline = goalDeadlineInput.ifBlank { "2026-12-31" }
                            )
                            viewModel.saveGoal(newGoal, isNew = true) {
                                showAddGoalDialog = false
                            }
                        }
                    },
                    variant = DuitAingButtonVariant.PRIMARY,
                    height = 42.dp,
                    shadowOffset = 2.dp
                )
            },
            dismissButton = {
                DuitAingButton(
                    text = "BATAL",
                    onClick = { showAddGoalDialog = false },
                    variant = DuitAingButtonVariant.OUTLINE,
                    height = 42.dp,
                    shadowOffset = 2.dp
                )
            }
        )
    }

    // Add Debt Dialog
    AddDebtDialog(
        isOpen = isAddDebtOpen,
        assets = activeAssets,
        defaultAssetId = defaultAsset?.id,
        onDismiss = { viewModel.isAddDebtOpen.value = false },
        onSave = { title, lender, amount, dueDate, note, receiveAssetId ->
            viewModel.addDebt(title, lender, amount, dueDate, note, receiveAssetId)
        }
    )

    // Pay Debt Dialog
    PayDebtDialog(
        debt = payingDebt,
        assets = activeAssets,
        defaultAssetId = defaultAsset?.id,
        onDismiss = { viewModel.payingDebt.value = null },
        onConfirmPay = { debtId, amount, sourceAssetId, paymentDate, note ->
            viewModel.recordDebtPayment(debtId, amount, sourceAssetId, paymentDate, note)
        }
    )

    // Delete Debt Confirmation Dialog
    if (debtToDelete != null) {
        AlertDialog(
            onDismissRequest = { debtToDelete = null },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(14.dp)),
            containerColor = PebbleSurface,
            title = {
                Text(
                    text = "HAPUS CATATAN HUTANG?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = DarkSurface
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus catatan hutang \"${debtToDelete?.title}\"? Tindakan ini tidak dapat dibatalkan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                DuitAingButton(
                    text = "HAPUS",
                    variant = DuitAingButtonVariant.DESTRUCTIVE,
                    height = 40.dp,
                    shadowOffset = 2.dp,
                    onClick = {
                        debtToDelete?.let { viewModel.deleteDebt(it.id) }
                        debtToDelete = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { debtToDelete = null }) {
                    Text(text = "BATAL", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Delete Financial Goal Confirmation Dialog
    if (goalToDelete != null) {
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(14.dp)),
            containerColor = PebbleSurface,
            title = {
                Text(
                    text = "HAPUS TARGET KEUANGAN?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = DarkSurface
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus target \"${goalToDelete?.name}\"? Progres tabungan Anda akan dihapus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                DuitAingButton(
                    text = "HAPUS",
                    variant = DuitAingButtonVariant.DESTRUCTIVE,
                    height = 40.dp,
                    shadowOffset = 2.dp,
                    onClick = {
                        goalToDelete?.let { viewModel.deleteGoal(it.id) }
                        goalToDelete = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { goalToDelete = null }) {
                    Text(text = "BATAL", color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
