package com.cashflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.ui.screens.*
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*

@Composable
fun CashflowApp(viewModel: CashflowViewModel) {
    // Keep the preferred height independent of animated IME insets; the sheet still limits it to the available space.
    val formHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .90f }
    val initialLoadComplete by viewModel.initialLoadComplete.collectAsState()
    ReportDrawnWhen { initialLoadComplete }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    var tourStep by rememberSaveable { mutableIntStateOf(0) }
    var showAppGuide by rememberSaveable { mutableStateOf(false) }
    val tourPage = AppTourStep.entries[tourStep.coerceIn(0, AppTourStep.entries.lastIndex)]
    val tourTargets = remember { mutableStateMapOf<Int, Rect>() }
    val tourPending by viewModel.isAppTourPending.collectAsState()

    val currentTab by viewModel.currentTab.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog & Sheet States
    val isRecordVoiceOpen by viewModel.isRecordVoiceOpen.collectAsState()
    val isAddTxOpen by viewModel.isAddTransactionOpen.collectAsState()
    val addTxInitialType by viewModel.addTransactionInitialType.collectAsState()
    val selectedTxForDetail by viewModel.selectedTransactionForDetail.collectAsState()
    val selectedAssetForDetail by viewModel.selectedAssetForDetail.collectAsState()
    val editingTx by viewModel.editingTransaction.collectAsState()
    val isAddAssetOpen by viewModel.isAddAssetOpen.collectAsState()
    val editingAsset by viewModel.editingAsset.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val isBudgetsScreenOpen by viewModel.isBudgetsScreenOpen.collectAsState()
    val isRecurringScreenOpen by viewModel.isRecurringScreenOpen.collectAsState()
    val isChatOpen by viewModel.isChatOpen.collectAsState()

    // Biometric Security Lock State
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()

    // Fresh install / empty assets setup state
    val assets by viewModel.assets.collectAsState()
    val isInitialSetupCompleted by viewModel.isInitialSetupCompleted.collectAsState()

    // Handle Snackbar messages & actions (with BATALKAN undo action support)
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    if (!initialLoadComplete) {
        SplashScreen()
        return
    }

    if (isAppLocked && isBiometricEnabled) {
        BiometricLockScreen(
            onUnlockSuccess = { viewModel.unlockApp() }
        )
        return
    }

    if (assets.none { it.isActive } && !isInitialSetupCompleted) {
        InitialSetupScreen(viewModel = viewModel)
        return
    }

    LaunchedEffect(tourPending) {
        if (tourPending) showOnboarding = true
    }
    LaunchedEffect(showOnboarding, tourStep) {
        if (showOnboarding) viewModel.selectTab(tourPage.tab)
    }
    val dismissTour = {
        showOnboarding = false
        tourStep = 0
        viewModel.dismissAppTour()
    }

    // Android Back Handler for dedicated Budgets screen
    BackHandler(enabled = isBudgetsScreenOpen) {
        viewModel.closeBudgetsScreen()
    }

    // Android Back Handler for dedicated Recurring Transactions screen
    BackHandler(enabled = isRecurringScreenOpen) {
        viewModel.isRecurringScreenOpen.value = false
    }

    // Android Back Handler for Financial AI Chat Assistant
    BackHandler(enabled = isChatOpen) {
        viewModel.closeChat()
    }

    // Android Back Handler: If not on Home tab (tab 0) and no sheet is open, back takes to Home
    BackHandler(
        enabled = currentTab != 0 &&
                !isBudgetsScreenOpen &&
                !isRecurringScreenOpen &&
                !isChatOpen &&
                !isRecordVoiceOpen &&
                !isAddTxOpen &&
                selectedTxForDetail == null &&
                selectedAssetForDetail == null &&
                editingTx == null &&
                !isAddAssetOpen &&
                !isSettingsOpen &&
                !showOnboarding && !showAppGuide
    ) {
        viewModel.selectTab(0)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = if (showOnboarding) Modifier.clearAndSetSemantics {} else Modifier,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val isDark = LocalIsDarkTheme.current
                    val snackbarBg = if (isDark) Color(0xFFF2F2EE) else Color(0xFF111111)
                    val snackbarBorder = if (isDark) Color(0xFFE6E6E1) else Color(0xFF2B2B2B)
                    val snackbarText = if (isDark) Color(0xFF111111) else Color(0xFFF2F2EE)
                    val snackbarActionColor = if (isDark) Color(0xFF111111) else MaterialTheme.duit.accentLime

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(snackbarBg)
                            .border(1.dp, snackbarBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = snackbarActionColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = snackbarText
                            )
                            data.visuals.actionLabel?.let { actionLabel ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = actionLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = snackbarActionColor,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(role = Role.Button) {
                                            data.performAction()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isBudgetsScreenOpen && !isRecurringScreenOpen) DuitAingBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.selectTab(it) },
                onAddTransactionClick = { viewModel.openAddTransaction() },
                onTargetPositioned = { key, rect -> tourTargets[key] = rect }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                modifier = if (isBudgetsScreenOpen || isRecurringScreenOpen) Modifier.clearAndSetSemantics {} else Modifier,
                targetState = currentTab,
                transitionSpec = {
                    val slideDistance = 48
                    if (targetState > initialState) {
                        (fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                         slideInHorizontally(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) { slideDistance }
                        ).togetherWith(
                         fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                         slideOutHorizontally(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) { -slideDistance }
                        )
                    } else {
                        (fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                         slideInHorizontally(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) { -slideDistance }
                        ).togetherWith(
                         fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                         slideOutHorizontally(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) { slideDistance }
                        )
                    }
                },
                label = "ScreenTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToTransactions = { viewModel.selectTab(1) },
                        onNavigateToAssets = { viewModel.selectTab(3) },
                        onTourBalancePositioned = { tourTargets[5] = it },
                        onTourAiPositioned = { tourTargets[18] = it }
                    )
                    1 -> TransactionsScreen(viewModel = viewModel, onTourSearchPositioned = { tourTargets[16] = it })
                    2 -> ReportsScreen(viewModel = viewModel, onTourCalendarPositioned = { tourTargets[17] = it })
                    3 -> AssetsScreen(viewModel = viewModel)
                }
            }

            // Dedicated Budgets Screen (Slide-in transition from Home)
            androidx.compose.animation.AnimatedVisibility(
                visible = isBudgetsScreenOpen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(260)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200))
            ) {
                BudgetsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.closeBudgetsScreen() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dedicated Recurring Transactions Screen (Slide-in transition)
            androidx.compose.animation.AnimatedVisibility(
                visible = isRecurringScreenOpen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(260)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200))
            ) {
                RecurringTransactionsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.isRecurringScreenOpen.value = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showOnboarding && !tourPage.isTransactionForm && !tourPage.isChatPreview) {
        OnboardingSheet(
            step = tourStep,
            target = tourTargets[tourPage.targetId],
            onNext = {
                if (tourStep < AppTourStep.entries.lastIndex) tourStep++ else {
                    dismissTour()
                    viewModel.openAddTransaction()
                }
            },
            onBack = { if (tourStep > 0) tourStep-- },
            onDismiss = dismissTour,
            onOpenGuide = { dismissTour(); showAppGuide = true }
        )
    }
    }

    if (showOnboarding && (tourPage.isTransactionForm || tourPage.isChatPreview)) {
        Dialog(onDismissRequest = dismissTour, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val formTargets = remember { mutableStateMapOf<Int, Rect>() }
            Box(Modifier.fillMaxSize().background(MaterialTheme.duit.background)) {
                if (tourPage.isTransactionForm) AddEditTransactionContent(
                    viewModel = viewModel,
                    onDismiss = dismissTour,
                    tourTarget = tourPage.targetId,
                    onTourTargetPositioned = { key, rect -> formTargets[key] = rect },
                    modifier = Modifier.fillMaxSize().statusBarsPadding().clearAndSetSemantics {}
                )
                else FinancialChatContent(
                    viewModel = viewModel,
                    onDismiss = dismissTour,
                    isTourPreview = true,
                    onTourTargetPositioned = { key, rect -> formTargets[key] = rect },
                    modifier = Modifier.fillMaxSize().statusBarsPadding().clearAndSetSemantics {}
                )
                OnboardingSheet(
                    step = tourStep,
                    target = formTargets[tourPage.targetId],
                    onNext = { tourStep++ },
                    onBack = { tourStep-- },
                    onDismiss = dismissTour
                )
            }
        }
    }

    if (showAppGuide) {
        AppGuideSheet(
            onStartTour = { showAppGuide = false; tourStep = 0; showOnboarding = true },
            onDismiss = { showAppGuide = false }
        )
    }

    // Modal Overlays
    if (isRecordVoiceOpen) {
        RecordCashFlowDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.isRecordVoiceOpen.value = false }
        )
    }

    // 1. Unified Transaction Modal (Detail & Edit seamlessly animated with AnimatedContent)
    val showTxModal = isAddTxOpen || editingTx != null || selectedTxForDetail != null
    var lastViewedTx by remember { mutableStateOf<Transaction?>(null) }
    if (selectedTxForDetail != null) {
        lastViewedTx = selectedTxForDetail
    }

    if (showTxModal) {
        val inEditMode = isAddTxOpen || editingTx != null
        val activeTx = selectedTxForDetail ?: lastViewedTx

        DuitAingModalBottomSheet(
            onDismissRequest = {
                viewModel.isAddTransactionOpen.value = false
                viewModel.editingTransaction.value = null
                viewModel.selectedTransactionForDetail.value = null
                lastViewedTx = null
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                // A new form has no detail/edit transition; avoid lookahead remeasurement on every IME frame.
                if (activeTx == null && editingTx == null) {
                    AddEditTransactionContent(
                        viewModel = viewModel,
                        initialType = addTxInitialType,
                        onDismiss = {
                            viewModel.isAddTransactionOpen.value = false
                            viewModel.editingTransaction.value = null
                            viewModel.selectedTransactionForDetail.value = null
                            lastViewedTx = null
                        },
                        modifier = Modifier.height(formHeight)
                    )
                } else AnimatedContent(
                    targetState = inEditMode,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        (if (targetState) {
                            // Detail -> Edit transition (ketika ingin mengedit!)
                            (slideInVertically(
                                initialOffsetY = { (it * 0.12f).toInt() },
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { (-it * 0.08f).toInt() },
                                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
                            )
                        } else {
                            // Edit -> Detail transition (batal edit / kembali ke detail)
                            (slideInVertically(
                                initialOffsetY = { (-it * 0.08f).toInt() },
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { (it * 0.12f).toInt() },
                                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
                            )
                        }).using(null)
                    },
                    label = "TransactionModalAnimatedContent"
                ) { isEditing ->
                    if (isEditing) {
                        AddEditTransactionContent(
                            viewModel = viewModel,
                            existingTx = editingTx,
                            initialType = addTxInitialType,
                            onDismiss = {
                                viewModel.isAddTransactionOpen.value = false
                                viewModel.editingTransaction.value = null
                                viewModel.selectedTransactionForDetail.value = null
                                lastViewedTx = null
                            },
                            onBack = if (lastViewedTx != null && editingTx != null) {
                                {
                                    viewModel.isAddTransactionOpen.value = false
                                    viewModel.editingTransaction.value = null
                                    viewModel.selectedTransactionForDetail.value = lastViewedTx
                                }
                            } else null,
                            modifier = Modifier.height(formHeight)
                        )
                    } else if (activeTx != null) {
                        TransactionDetailContent(
                            viewModel = viewModel,
                            transaction = activeTx,
                            onDismiss = {
                                viewModel.selectedTransactionForDetail.value = null
                                lastViewedTx = null
                            },
                            onEdit = {
                                viewModel.editingTransaction.value = activeTx
                                viewModel.isAddTransactionOpen.value = true
                            },
                            modifier = Modifier.wrapContentHeight()
                        )
                    }
                }
            }
        }
    }

    // 2. Unified Asset Modal (Detail & Edit seamlessly animated with AnimatedContent)
    val showAssetModal = isAddAssetOpen || editingAsset != null || selectedAssetForDetail != null
    var lastViewedAsset by remember { mutableStateOf<Asset?>(null) }
    if (selectedAssetForDetail != null) {
        lastViewedAsset = selectedAssetForDetail
    }

    if (showAssetModal) {
        val inAssetEditMode = isAddAssetOpen || editingAsset != null
        val activeAsset = selectedAssetForDetail ?: lastViewedAsset

        DuitAingModalBottomSheet(
            onDismissRequest = {
                viewModel.isAddAssetOpen.value = false
                viewModel.editingAsset.value = null
                viewModel.selectedAssetForDetail.value = null
                lastViewedAsset = null
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (activeAsset == null && editingAsset == null) {
                    AddAssetContent(
                        viewModel = viewModel,
                        onDismiss = {
                            viewModel.isAddAssetOpen.value = false
                            viewModel.editingAsset.value = null
                            viewModel.selectedAssetForDetail.value = null
                            lastViewedAsset = null
                        },
                        modifier = Modifier.height(formHeight)
                    )
                } else AnimatedContent(
                    targetState = inAssetEditMode,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        (if (targetState) {
                            (slideInVertically(
                                initialOffsetY = { (it * 0.12f).toInt() },
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { (-it * 0.08f).toInt() },
                                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
                            )
                        } else {
                            (slideInVertically(
                                initialOffsetY = { (-it * 0.08f).toInt() },
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { (it * 0.12f).toInt() },
                                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                                ) + fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
                            )
                        }).using(null)
                    },
                    label = "AssetModalAnimatedContent"
                ) { isEditing ->
                    if (isEditing) {
                        AddAssetContent(
                            viewModel = viewModel,
                            assetToEdit = editingAsset,
                            onDismiss = {
                                viewModel.isAddAssetOpen.value = false
                                viewModel.editingAsset.value = null
                                viewModel.selectedAssetForDetail.value = null
                                lastViewedAsset = null
                            },
                            onBack = if (lastViewedAsset != null && editingAsset != null) {
                                {
                                    viewModel.isAddAssetOpen.value = false
                                    viewModel.editingAsset.value = null
                                    viewModel.selectedAssetForDetail.value = lastViewedAsset
                                }
                            } else null,
                            modifier = Modifier.height(formHeight)
                        )
                    } else if (activeAsset != null) {
                        AssetDetailContent(
                            viewModel = viewModel,
                            asset = activeAsset,
                            onDismiss = {
                                viewModel.selectedAssetForDetail.value = null
                                lastViewedAsset = null
                            },
                            onEdit = {
                                viewModel.editingAsset.value = activeAsset
                                viewModel.isAddAssetOpen.value = true
                            },
                            modifier = Modifier.fillMaxHeight(0.90f)
                        )
                    }
                }
            }
        }
    }

    if (isSettingsOpen) {
        SettingsSheet(
            viewModel = viewModel,
            onOpenOnboarding = { showAppGuide = true },
            onDismiss = { viewModel.isSettingsOpen.value = false }
        )
    }

    if (isChatOpen) {
        FinancialChatSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.closeChat() }
        )
    }

    val pendingChange by viewModel.dataChangeConfirmation.collectAsState()
    pendingChange?.let { change ->
        var showPrevious by remember(change) { mutableStateOf(false) }
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LaunchedEffect(change) { keyboard?.hide() }
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingDataChange,
            title = { Text(change.title) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(change.message)
                    if (change.reviewTransactions.isNotEmpty()) {
                        TextButton(onClick = { showPrevious = !showPrevious }) {
                            Text(if (showPrevious) "Sembunyikan pembanding" else "Periksa transaksi sebelumnya")
                        }
                        if (showPrevious) change.reviewTransactions.forEach { tx ->
                            val source = viewModel.assets.value.firstOrNull { it.id == tx.assetId }?.name ?: "Rekening tidak tersedia"
                            val destination = viewModel.assets.value.firstOrNull { it.id == tx.destinationAssetId }?.name ?: "Rekening tidak tersedia"
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text(tx.description, fontWeight = FontWeight.SemiBold)
                            Text("${Formatters.formatDateReadable(tx.transactionDate)} • ${tx.transactionTime}")
                            Text(if (tx.type == "transfer") "$source → $destination" else source)
                            Text(Formatters.displayRupiah(tx.amount))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmPendingDataChange,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (change.destructive) MaterialTheme.duit.expense else MaterialTheme.duit.textPrimary
                    )
                ) { Text(change.confirmLabel ?: if (change.title.startsWith("Hapus")) "Hapus" else if (change.title.startsWith("Simpan")) "Simpan perubahan" else "Lanjutkan") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPendingDataChange) { Text("Batal") }
            }
        )
    }
}

/**
 * Section 8: Refined Bottom Navigation Micro-Interaction
 * - Sliding/pulsing active indicator
 * - Icon subtle scale on select
 * - Smooth typography and color transitions
 * - Subtle Android haptic feedback
 */
@Composable
private fun DuitAingBottomNavBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    onAddTransactionClick: () -> Unit,
    onTargetPositioned: (Int, Rect) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    val duit = MaterialTheme.duit
    val navBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val largeText = density.fontScale > 1.15f
    // Convert each line before summing; Android scales different sp sizes nonlinearly.
    val labelHeight = with(density) { MaterialTheme.typography.labelSmall.lineHeight.toDp() * 2 }
    val navBarHeight = maxOf(72.dp, labelHeight + 51.dp)

    val leftTabs = listOf(
        Triple(0, "Beranda", Icons.Rounded.Home),
        Triple(1, "Transaksi", Icons.AutoMirrored.Rounded.ReceiptLong)
    )
    val rightTabs = listOf(
        Triple(2, "Laporan", Icons.Rounded.BarChart),
        Triple(3, "Aset", Icons.Rounded.AccountBalanceWallet)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = AppElevation.floating,
                shape = navBarShape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.16f)
            )
            .clip(navBarShape)
            .background(duit.surface)
            .border(
                width = 1.dp,
                color = duit.border,
                shape = navBarShape
            )
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarHeight)
                .padding(horizontal = if (largeText) AppSpacing.xxs else AppSpacing.sm, vertical = AppSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left 2 tabs
            leftTabs.forEach { (index, title, icon) ->
                val isSelected = currentTab == index
                BottomNavTabItem(
                    title = title,
                    icon = icon,
                    isSelected = isSelected,
                    onSelect = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(index)
                    },
                    modifier = Modifier.weight(1f).onGloballyPositioned { onTargetPositioned(index, it.boundsInRoot()) }
                )
            }

            // Center FAB (Prominent Round Fintech Button)
            val centerInteractionSource = remember { MutableInteractionSource() }
            val centerPressed by centerInteractionSource.collectIsPressedAsState()
            val centerScale by animateFloatAsState(
                targetValue = if (centerPressed) 0.92f else 1.0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "CenterFabScale"
            )

            val isDark = LocalIsDarkTheme.current
            val duit = MaterialTheme.duit
            val fabBg = if (isDark) duit.primary else Color(0xFF111111)
            val fabIconColor = if (isDark) Color(0xFF111111) else duit.accentLime

            Box(
                modifier = Modifier
                    .padding(horizontal = if (largeText) AppSpacing.xxs else AppSpacing.xs)
                    .size(52.dp)
                    .onGloballyPositioned { onTargetPositioned(4, it.boundsInRoot()) }
                    .graphicsLayer {
                        scaleX = centerScale
                        scaleY = centerScale
                    }
                    .shadow(
                        elevation = AppElevation.floating,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.12f),
                        spotColor = Color.Black.copy(alpha = 0.24f)
                    )
                    .clip(CircleShape)
                    .background(fabBg)
                    .clickable(
                        interactionSource = centerInteractionSource,
                        indication = null,
                        role = Role.Button
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddTransactionClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Tambah Transaksi",
                    tint = fabIconColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Right 2 tabs
            rightTabs.forEach { (index, title, icon) ->
                val isSelected = currentTab == index
                BottomNavTabItem(
                    title = title,
                    icon = icon,
                    isSelected = isSelected,
                    onSelect = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(index)
                    },
                    modifier = Modifier.weight(1f).onGloballyPositioned { onTargetPositioned(index, it.boundsInRoot()) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val duit = MaterialTheme.duit

    // Active Tab Pill: Terang = lime (#C8F25A) dengan ikon hitam (#111111); Gelap = primaryContainer (#232B10) dengan ikon lime (#C8F25A)
    val pillBg = if (isSelected) {
        if (isDark) duit.primaryContainer else duit.accentLime
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        if (isDark) duit.accentLime else Color(0xFF111111)
    } else {
        duit.textSecondary
    }

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "NavTabIconScale"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .semantics { selected = isSelected }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onSelect
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .width(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pillBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = contentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = if (isSelected) duit.textPrimary else duit.textSecondary,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

