package com.cashflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.cashflow.app.ui.screens.*
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
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()

    // Biometric Security Lock State
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()

    // Handle Snackbar messages
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
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

    // Android Back Handler: If not on Home tab (tab 0) and no sheet is open, back takes to Home
    BackHandler(
        enabled = currentTab != 0 &&
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            DuitAingBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        floatingActionButton = {
            // Prominent Voice Record FAB on non-Home screens with tactile elevation
            if (currentTab != 0) {
                FloatingActionButton(
                    onClick = { viewModel.isRecordVoiceOpen.value = true },
                    containerColor = JadePrimary,
                    contentColor = PebbleSurface,
                    shape = RoundedCornerShape(14.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
                    modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Record Cash Flow",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
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
                    if (targetState > initialState) {
                        (slideInHorizontally(
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() }
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() }
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                            )
                        )
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() }
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() }
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                            )
                        )
                    }
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
        }
    }

    // Modal Overlays
    if (isRecordVoiceOpen) {
        RecordCashFlowDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.isRecordVoiceOpen.value = false }
        )
    }

    if (isAddTxOpen || editingTx != null) {
        AddEditTransactionSheet(
            viewModel = viewModel,
            existingTx = editingTx,
            initialType = addTxInitialType,
            onDismiss = {
                viewModel.isAddTransactionOpen.value = false
                viewModel.editingTransaction.value = null
            }
        )
    }

    if (selectedTxForDetail != null) {
        TransactionDetailSheet(
            viewModel = viewModel,
            transaction = selectedTxForDetail!!,
            onDismiss = { viewModel.selectedTransactionForDetail.value = null }
        )
    }

    if (selectedAssetForDetail != null) {
        AssetDetailSheet(
            viewModel = viewModel,
            asset = selectedAssetForDetail!!,
            onDismiss = { viewModel.selectedAssetForDetail.value = null }
        )
    }

    if (isAddAssetOpen) {
        AddAssetDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.isAddAssetOpen.value = false }
        )
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
    onTabSelected: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val navItems = listOf(
        Triple(0, "Beranda", Icons.Rounded.Home),
        Triple(1, "Transaksi", Icons.AutoMirrored.Rounded.ReceiptLong),
        Triple(2, "Laporan", Icons.Rounded.BarChart),
        Triple(3, "Aset", Icons.Rounded.AccountBalanceWallet)
    )

    val navBarShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = navBarShape,
                ambientColor = Color(0x181E2322),
                spotColor = Color(0x351E2322)
            )
            .clip(navBarShape)
            .background(PebbleSurface)
            .border(
                width = 1.dp,
                color = DarkBorder.copy(alpha = 0.15f),
                shape = navBarShape
            )
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { (index, title, icon) ->
                val isSelected = currentTab == index

                // Spring bouncy scale for icon
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.20f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "NavIconScale"
                )

                // Playful tilt on select
                val iconRotation by animateFloatAsState(
                    targetValue = if (isSelected) -4f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = 500f
                    ),
                    label = "NavIconRotation"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) JadePrimary else TextSecondary,
                    animationSpec = tween(durationMillis = 180),
                    label = "NavTextColor"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) JadePrimary else TextSecondary,
                    animationSpec = tween(durationMillis = 180),
                    label = "NavIconColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .semantics { selected = isSelected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Tactile indicator container with soft shadow and refined border
                        val pillShape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .then(
                                    if (isSelected) {
                                        Modifier.shadow(
                                            elevation = 2.dp,
                                            shape = pillShape,
                                            ambientColor = Color(0x101E2322),
                                            spotColor = Color(0x201E2322)
                                        )
                                    } else Modifier
                                )
                                .clip(pillShape)
                                .background(if (isSelected) JadePrimaryContainer else Color.Transparent)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            1.dp,
                                            JadePrimary.copy(alpha = 0.35f),
                                            pillShape
                                        )
                                    } else Modifier
                                )
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                        rotationZ = iconRotation
                                    }
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

