package com.cashflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
    var showSplash by remember { mutableStateOf(true) }
    var showOnboarding by remember { mutableStateOf(false) }

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

    // Biometric Security Lock State
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()

    // Fresh install / empty assets setup state
    val activeAssets by viewModel.activeAssets.collectAsState()
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

    if (showSplash) {
        SplashScreen(
            onDismiss = { showSplash = false }
        )
        return
    }

    if (isAppLocked && isBiometricEnabled) {
        BiometricLockScreen(
            onUnlockSuccess = { viewModel.unlockApp() }
        )
        return
    }

    if (activeAssets.isEmpty() && !isInitialSetupCompleted) {
        InitialSetupScreen(viewModel = viewModel)
        return
    }

    // Android Back Handler for dedicated Budgets screen
    BackHandler(enabled = isBudgetsScreenOpen) {
        viewModel.closeBudgetsScreen()
    }

    // Android Back Handler for dedicated Recurring Transactions screen
    BackHandler(enabled = isRecurringScreenOpen) {
        viewModel.isRecurringScreenOpen.value = false
    }

    // Android Back Handler: If not on Home tab (tab 0) and no sheet is open, back takes to Home
    BackHandler(
        enabled = currentTab != 0 &&
                !isBudgetsScreenOpen &&
                !isRecurringScreenOpen &&
                !isRecordVoiceOpen &&
                !isAddTxOpen &&
                selectedTxForDetail == null &&
                selectedAssetForDetail == null &&
                editingTx == null &&
                !isAddAssetOpen &&
                !isSettingsOpen &&
                !showOnboarding
    ) {
        viewModel.selectTab(0)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PebbleSurface)
                            .border(1.5.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = JadePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = TextPrimary
                            )
                            data.visuals.actionLabel?.let { actionLabel ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = actionLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = JadePrimary,
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
            DuitAingBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.selectTab(it) },
                onAddTransactionClick = { viewModel.openAddTransaction() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    ) + scaleIn(
                        initialScale = 0.97f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    )).togetherWith(
                        fadeOut(
                            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                        )
                    )
                },
                label = "ScreenTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToTransactions = { viewModel.selectTab(1) },
                        onNavigateToAssets = { viewModel.selectTab(3) }
                    )
                    1 -> TransactionsScreen(viewModel = viewModel)
                    2 -> ReportsScreen(viewModel = viewModel)
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
                    .fillMaxWidth()
                    .imePadding()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = inEditMode,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        if (targetState) {
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
                        }
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
                            modifier = Modifier.fillMaxHeight(0.90f)
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
                    .fillMaxWidth()
                    .imePadding()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = inAssetEditMode,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        if (targetState) {
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
                        }
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
                            modifier = Modifier.fillMaxHeight(0.90f)
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
            onOpenOnboarding = { showOnboarding = true },
            onDismiss = { viewModel.isSettingsOpen.value = false }
        )
    }

    if (showOnboarding) {
        OnboardingSheet(
            onDismiss = { showOnboarding = false }
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
    onAddTransactionClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    val navBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

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
                elevation = 14.dp,
                shape = navBarShape,
                ambientColor = Color(0x150F3D2E),
                spotColor = Color(0x250F3D2E)
            )
            .clip(navBarShape)
            .background(PebbleSurface)
            .border(
                width = 1.dp,
                color = PebbleBorder,
                shape = navBarShape
            )
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
                    modifier = Modifier.weight(1f)
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

            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = centerScale
                        scaleY = centerScale
                    }
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x250F3D2E),
                        spotColor = Color(0x450F3D2E)
                    )
                    .clip(CircleShape)
                    .background(FintechPrimary)
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
                    tint = Color.White,
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
                    modifier = Modifier.weight(1f)
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
    val activeColor = if (isDark) FintechAccentMint else FintechPrimary
    val inactiveColor = if (isDark) TextSecondaryDark else TextSecondary

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "NavTabIconScale"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 180),
        label = "NavTabTextColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 180),
        label = "NavTabIconColor"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 450f),
        label = "NavIndicatorWidth"
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
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) activeColor.copy(alpha = if (isDark) 0.12f else 0.08f) else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier
                    .size(23.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.5.sp
                ),
                color = textColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isSelected) activeColor else Color.Transparent)
            )
        }
    }
}

