package com.cashflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.Budget
import com.cashflow.app.data.model.BudgetWithProgress
import com.cashflow.app.data.model.FavoriteTemplate
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.components.home.*
import com.cashflow.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID

/**
 * Editorial Financial Dashboard for "Duit Aing" (Tahap 2).
 * Hierarki jelas, tidak sesak, fokus ke saldo dan catat cepat.
 *
 * Urutan 7 Blok dari atas:
 * 1. Header: sapaan + ikon cari dan pengaturan (ringkas).
 * 2. Hero card Total Kekayaan: angka besar (dimThousands), tombol mata, badge delta bulan lalu, sparkline kecil.
 * 3. Quick actions 4 tombol sama besar (Pengeluaran, Pemasukan, Transfer, Suara) label 12sp+ & touch target >= 48dp.
 * 4. Maksimal 1 banner insight (yang paling relevan), bisa ditutup.
 * 5. Transaksi Terbaru: 5 item, ikon kategori bulat, nominal berwarna dan rata kanan, "Lihat semua".
 * 6. Arus Kas bulan ini: satu kartu (pemasukan, pengeluaran, selisih, mini bar).
 * 7. Anggaran: maksimal 3 teratas dengan progress bar tebal berwarna (hijau/amber/merah) + "Lihat semua".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: CashflowViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAssets: () -> Unit,
    modifier: Modifier = Modifier,
    onTourBalancePositioned: (Rect) -> Unit = {},
    onTourAiPositioned: (Rect) -> Unit = {}
) {
    val duit = MaterialTheme.duit
    val totalWealth by viewModel.totalWealth.collectAsState()
    val periodIncome by viewModel.periodIncome.collectAsState()
    val periodExpense by viewModel.periodExpense.collectAsState()
    val netCashflow by viewModel.netCashflow.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val activeAssets by viewModel.activeAssets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val ruleBasedInsights by viewModel.ruleBasedInsights.collectAsState()
    val monthStats by viewModel.monthComparisonStats.collectAsState()
    val chartBuckets by viewModel.homeChart.collectAsState()
    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()
    val integrityDiscrepancies by viewModel.integrityDiscrepancies.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(activeAssets) { activeAssets.associateBy { it.id } }
    val expenseCategories = remember(categories) {
        categories.filter { it.type == "expense" && it.isActive }
    }

    var showPeriodSheet by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var budgetToDelete by remember { mutableStateOf<BudgetWithProgress?>(null) }
    val isInsightDismissed by viewModel.isHomeInsightDismissed.collectAsState()
    var insightIndex by remember(ruleBasedInsights) { mutableIntStateOf(0) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    val userName by viewModel.userName.collectAsState()

    val monthDeltaPct = monthStats?.takeIf { it.prevMonthTotalExpense > 0 }?.expensePercentChange?.toInt()

    val currentHour = LocalTime.now().hour
    val timeGreeting = when (currentHour) {
        in 5..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..18 -> "Selamat sore"
        else -> "Selamat malam"
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                viewModel.refreshData()
                isRefreshing = false
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(duit.background)
    ) {
        // ponytail: Home shows at most 5 transactions and 3 budgets; use a lazy list if these previews become unbounded.
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
            // 1. Header: Sapaan Personal + Ikon Tanya AI, Cari, dan Pengaturan (ringkas & tidak terpotong)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { showEditNameDialog = true }
                ) {
                    Text(
                        text = timeGreeting,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = duit.textSecondary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (userName.isNotBlank()) userName.trim() else "Kelola Keuangan",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = duit.textPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tombol Tanya AI Finansial (Target sentuh >= 48dp)
                    IconButton(
                        onClick = { viewModel.openChat() },
                        modifier = Modifier
                            .onGloballyPositioned { onTourAiPositioned(it.boundsInRoot()) }
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(duit.primary.copy(alpha = 0.15f))
                            .border(1.dp, duit.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Tanya Vina (AI)",
                            tint = duit.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Tombol Cari (Target sentuh >= 48dp)
                    IconButton(
                        onClick = onNavigateToTransactions,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(duit.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Cari transaksi",
                            tint = duit.textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Tombol Pengaturan (Target sentuh >= 48dp)
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(
                            onClick = { viewModel.isSettingsOpen.value = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(duit.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Pengaturan",
                                tint = duit.textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        if (integrityDiscrepancies.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(duit.expense)
                                    .border(1.5.dp, duit.surface, CircleShape)
                            )
                        }
                    }
                }
            }

            // 2. Hero Card Total Kekayaan (Angka pas, dimThousands, mata, delta badge, ringkas, padding rapi)
            HeroBalanceCard(
                totalWealth = totalWealth,
                isBalanceHidden = isBalanceHidden,
                onToggleBalanceVisibility = { viewModel.toggleBalanceVisibility() },
                monthDeltaPct = monthDeltaPct,
                onNavigateToAssets = onNavigateToAssets,
                assetCount = activeAssets.size,
                modifier = Modifier.padding(horizontal = 20.dp)
                    .onGloballyPositioned { onTourBalancePositioned(it.boundsInRoot()) }
            )

            // 3. Quick Actions (4 Tombol sama besar: Pengeluaran, Pemasukan, Transfer, Suara)
            Spacer(modifier = Modifier.height(16.dp))
            QuickActions(
                onExpenseClick = { viewModel.openAddTransaction(TransactionType.EXPENSE) },
                onIncomeClick = { viewModel.openAddTransaction(TransactionType.INCOME) },
                onTransferClick = { viewModel.openAddTransaction(TransactionType.TRANSFER) },
                onVoiceClick = { viewModel.isRecordVoiceOpen.value = true }
            )

            // 4. One insight at a time; manual navigation avoids automatic motion while scrolling.
            if (ruleBasedInsights.isNotEmpty() && !isInsightDismissed) {
                Spacer(modifier = Modifier.height(22.dp))
                HomeInsightBanner(
                    insight = ruleBasedInsights[insightIndex],
                    onDismiss = { viewModel.isHomeInsightDismissed.value = true },
                    position = insightIndex + 1,
                    total = ruleBasedInsights.size,
                    onNext = { insightIndex = (insightIndex + 1) % ruleBasedInsights.size }
                )
            }

            // 5. Transaksi Terbaru (5 item, ikon bulat, nominal berwarna rata kanan, "Lihat semua")
            Spacer(modifier = Modifier.height(22.dp))
            RecentTransactions(
                transactions = transactions,
                categoryMap = catMap,
                assetMap = assetMap,
                isBalanceHidden = isBalanceHidden,
                onViewAllClick = onNavigateToTransactions,
                onTransactionClick = { tx ->
                    viewModel.selectedTransactionForDetail.value = tx
                },
                onEditTransaction = { tx ->
                    viewModel.editingTransaction.value = tx
                    viewModel.isAddTransactionOpen.value = true
                },
                onDeleteTransaction = { tx ->
                    viewModel.deleteTransactionWithUndo(tx)
                },
                onSaveAsFavorite = { tx ->
                    val template = FavoriteTemplate(
                        id = UUID.randomUUID().toString(),
                        name = tx.description,
                        type = tx.type,
                        amount = tx.amount,
                        categoryId = tx.categoryId,
                        assetId = tx.assetId,
                        destinationAssetId = tx.destinationAssetId,
                        note = tx.note
                    )
                    viewModel.saveFavoriteTemplate(template)
                },
                onAddTransactionClick = { viewModel.openAddTransaction() }
            )

            // 6. Arus Kas Bulan Ini (Satu kartu: pemasukan, pengeluaran, selisih, mini bar)
            Spacer(modifier = Modifier.height(22.dp))
            CashflowCard(
                netCashflow = netCashflow,
                periodIncome = periodIncome,
                periodExpense = periodExpense,
                selectedPeriod = selectedPeriod,
                onSelectPeriodClick = { showPeriodSheet = true },
                buckets = chartBuckets,
                isBalanceHidden = isBalanceHidden
            )

            // 7. Anggaran (Maksimal 3 teratas dengan progress bar tebal berwarna hijau/amber/merah + "Lihat semua")
            Spacer(modifier = Modifier.height(22.dp))
            BudgetPreview(
                budgets = budgets,
                isBalanceHidden = isBalanceHidden,
                onViewAllClick = { viewModel.openBudgetsScreen() },
                onAddBudgetClick = {
                    editingBudget = null
                    showBudgetDialog = true
                },
                onEditBudget = { b ->
                    editingBudget = b
                    showBudgetDialog = true
                },
                onDeleteBudget = { bwp ->
                    budgetToDelete = bwp
                }
            )
        }
    }

    PeriodSelectionSheet(
        visible = showPeriodSheet,
        selectedPeriod = selectedPeriod,
        onPeriodSelected = {
            viewModel.setPeriodFilter(it)
            showPeriodSheet = false
        },
        onDismissRequest = { showPeriodSheet = false }
    )

    BudgetFormDialog(
        visible = showBudgetDialog,
        editingBudget = editingBudget,
        expenseCategories = expenseCategories,
        onSave = { newBudget ->
            viewModel.saveBudget(newBudget) {
                showBudgetDialog = false
            }
        },
        onUpdate = { updatedBudget ->
            viewModel.updateBudget(updatedBudget) {
                showBudgetDialog = false
            }
        },
        onDismiss = { showBudgetDialog = false }
    )

    val catNameToDelete = budgetToDelete?.category?.name ?: "ini"
    DuitAingConfirmDialog(
        isOpen = budgetToDelete != null,
        title = "HAPUS ANGGARAN?",
        message = "Apakah Anda yakin ingin menghapus batas anggaran kategori \"$catNameToDelete\"? Catatan pengeluaran Anda tetap aman.",
        confirmText = "HAPUS",
        confirmVariant = DuitAingButtonVariant.DANGER,
        onConfirm = {
            budgetToDelete?.let { viewModel.deleteBudget(it.budget.id, confirmed = true) }
            budgetToDelete = null
        },
        onDismiss = { budgetToDelete = null }
    )

    // Modal Ubah Nama Panggilan untuk Sapaan Personal
    if (showEditNameDialog) {
        var tempNameInput by remember(userName) { mutableStateOf(userName) }
        Dialog(onDismissRequest = { showEditNameDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = duit.surface,
                border = BorderStroke(1.dp, duit.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nama Panggilan",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = duit.textPrimary
                        )
                        IconButton(
                            onClick = { showEditNameDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Tutup",
                                tint = duit.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "Nama panggilanmu akan ditampilkan pada sapaan beranda agar terasa lebih hangat & personal.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = duit.textSecondary
                    )

                    OutlinedTextField(
                        value = tempNameInput,
                        onValueChange = { if (it.length <= 25) tempNameInput = it },
                        placeholder = { Text("Contoh: Agung", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = duit.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = duit.surfaceVariant.copy(alpha = 0.35f),
                            focusedBorderColor = duit.primary,
                            unfocusedBorderColor = duit.border
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditNameDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, duit.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = duit.textPrimary)
                        ) {
                            Text("Batal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                viewModel.setUserName(tempNameInput) {
                                    showEditNameDialog = false
                                    viewModel.showSnackbar("Nama panggilan diperbarui.")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = duit.primary,
                                contentColor = duit.onPrimary
                            )
                        ) {
                            Text("Simpan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
