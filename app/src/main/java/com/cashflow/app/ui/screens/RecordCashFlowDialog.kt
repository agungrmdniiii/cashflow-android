package com.cashflow.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.airbnb.lottie.compose.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.cashflow.app.data.model.Asset
import com.cashflow.app.data.model.Category
import com.cashflow.app.data.model.Transaction
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.theme.*
import com.cashflow.app.voice.ParsedVoiceResult
import com.cashflow.app.voice.VoiceRecognitionManager
import com.cashflow.app.voice.VoiceState
import com.cashflow.app.voice.VoiceTransactionParser
import java.util.UUID

@Composable
fun RecordCashFlowDialog(
    viewModel: CashflowViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val voiceManager = remember { VoiceRecognitionManager(context) }
    val voiceState by voiceManager.voiceState.collectAsState()
    val audioRms by voiceManager.audioRms.collectAsState()
    val onlineVoice by viewModel.isOnlineVoiceEnabled.collectAsState()

    val activeAssets by viewModel.activeAssets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val defaultAsset by viewModel.defaultAsset.collectAsState()
    val recentCategories by viewModel.recentCategories.collectAsState()
    val recentAssets by viewModel.recentAssets.collectAsState()

    var parsedResult by remember { mutableStateOf<ParsedVoiceResult?>(null) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isPermissionDenied by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            isPermissionDenied = false
            voiceManager.startListening()
        } else {
            isPermissionDenied = true
        }
    }

    // State machine reaction to voice results
    LaunchedEffect(voiceState) {
        if (voiceState is VoiceState.Success) {
            val spokenText = (voiceState as VoiceState.Success).recognizedText
            val result = VoiceTransactionParser.parse(
                spokenText = spokenText,
                availableAssets = activeAssets,
                availableCategories = categories,
                defaultAsset = defaultAsset
            )
            parsedResult = result
        }
    }

    val permissionLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(permissionLifecycle, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasMicPermission && !granted) {
                    voiceManager.stopListening()
                    isPermissionDenied = true
                }
                hasMicPermission = granted
                if (granted) isPermissionDenied = false
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                voiceManager.stopListening()
            }
        }
        permissionLifecycle.lifecycle.addObserver(observer)
        onDispose { permissionLifecycle.lifecycle.removeObserver(observer) }
    }

    // Start listening when dialog opens if permission is already granted
    LaunchedEffect(Unit) {
        if (hasMicPermission) {
            voiceManager.startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopListening()
        }
    }

    val duit = MaterialTheme.duit

    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(duit.primaryContainer)
                                .border(1.dp, duit.primary.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = duit.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Catat dengan suara",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = duit.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                role = Role.Button,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(duit.surfaceVariant)
                                .border(1.dp, duit.border, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Tutup",
                                tint = duit.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // IF PREVIEW IS READY: Show Transaction Preview (Confirmation Screen)
                if (parsedResult != null) {
                    VoiceConfirmationView(
                        result = parsedResult!!,
                        activeAssets = activeAssets,
                        recentCategories = recentCategories,
                        recentAssets = recentAssets,
                        onResultChanged = { parsedResult = it },
                        onEdit = {
                            viewModel.isRecordVoiceOpen.value = false
                            onDismiss()
                            viewModel.editingTransaction.value = null
                            viewModel.addTransactionInitialType.value = parsedResult?.type ?: TransactionType.EXPENSE
                            viewModel.isAddTransactionOpen.value = true
                        },
                        onSave = { txToSave ->
                            viewModel.saveTransaction(txToSave) { success ->
                                if (success) {
                                    viewModel.isRecordVoiceOpen.value = false
                                    onDismiss()
                                }
                            }
                        },
                        onRetry = {
                            parsedResult = null
                            voiceManager.reset()
                            if (hasMicPermission) {
                                voiceManager.startListening()
                            }
                        }
                    )
                } else {
                    val openAppSettings = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            viewModel.showSnackbar("Buka Pengaturan HP > Aplikasi > Duit Aing > Izin Mikrofon")
                        }
                    }

                    val retryListening = {
                        voiceManager.reset()
                        if (hasMicPermission) {
                            voiceManager.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    // RECORDING / LISTENING STATE
                    VoiceRecordingView(
                        voiceState = voiceState,
                        audioRms = audioRms,
                        usesOnlineVoice = onlineVoice,
                        hasPermission = hasMicPermission,
                        isPermissionDenied = isPermissionDenied,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onOpenSettings = openAppSettings,
                        onRetryListening = retryListening,
                        onStopListening = { voiceManager.finishListening() },
                        onUseOnlineVoice = {
                            viewModel.setOnlineVoiceEnabled(true)
                            voiceManager.startListening()
                        },
                        onSimulate = { sampleText ->
                            voiceManager.simulateVoiceInput(sampleText)
                        },
                        onManualClick = {
                            voiceManager.stopListening()
                            viewModel.isRecordVoiceOpen.value = false
                            onDismiss()
                            viewModel.editingTransaction.value = null
                            viewModel.addTransactionInitialType.value = TransactionType.EXPENSE
                            viewModel.isAddTransactionOpen.value = true
                        },
                        onTransferClick = {
                            voiceManager.stopListening()
                            viewModel.isRecordVoiceOpen.value = false
                            onDismiss()
                            viewModel.editingTransaction.value = null
                            viewModel.addTransactionInitialType.value = TransactionType.TRANSFER
                            viewModel.isAddTransactionOpen.value = true
                        }
                    )
                }
            }
        }
    }

@Composable
private fun VoiceRecordingView(
    voiceState: VoiceState,
    audioRms: Float,
    usesOnlineVoice: Boolean,
    hasPermission: Boolean,
    isPermissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryListening: () -> Unit,
    onStopListening: () -> Unit,
    onUseOnlineVoice: () -> Unit,
    onSimulate: (String) -> Unit,
    onManualClick: () -> Unit,
    onTransferClick: () -> Unit
) {
    val duit = MaterialTheme.duit
    Spacer(modifier = Modifier.height(8.dp))

    val micPulseComp by rememberLottieComposition(LottieCompositionSpec.RawRes(com.cashflow.app.R.raw.lottie_mic_pulse))
    val micLimeArgb = duit.accentLime.toArgb()
    val micDynamicProps = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = com.airbnb.lottie.LottieProperty.COLOR,
            value = micLimeArgb,
            keyPath = arrayOf("**")
        ),
        rememberLottieDynamicProperty(
            property = com.airbnb.lottie.LottieProperty.STROKE_COLOR,
            value = micLimeArgb,
            keyPath = arrayOf("**")
        )
    )

    // Microphone Core Block with Lottie Pulse Animation
    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        if (voiceState is VoiceState.Listening) {
            LottieAnimation(
                composition = micPulseComp,
                iterations = LottieConstants.IterateForever,
                dynamicProperties = micDynamicProps,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (voiceState is VoiceState.Listening) duit.primary
                    else duit.surfaceVariant
                )
                .border(1.dp, duit.border, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = if (voiceState is VoiceState.Listening) duit.onPrimary else duit.textPrimary,
                modifier = Modifier.size(38.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Geometric Audio Waveform Activity Bars
    Row(
        modifier = Modifier
            .height(28.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barWeights = listOf(0.4f, 0.7f, 1.0f, 0.85f, 1.1f, 0.65f, 0.45f)
        barWeights.forEach { multiplier ->
            val dynamicHeight = if (voiceState is VoiceState.Listening) {
                ((audioRms * 3.5f * multiplier).coerceIn(4f, 26f)).dp
            } else 4.dp

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(dynamicHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (voiceState is VoiceState.Listening) duit.primary else duit.track)
                    .border(0.5.dp, duit.border, RoundedCornerShape(2.dp))
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    val title = when {
        !hasPermission && isPermissionDenied -> "IZIN MIKROFON DITOLAK"
        !hasPermission -> "IZINKAN AKSES MIKROFON"
        voiceState is VoiceState.Processing -> "Memproses ucapan..."
        voiceState is VoiceState.Error -> "Belum terdengar"
        voiceState is VoiceState.Listening -> "Mendengarkan..."
        else -> "Siap merekam"
    }
    val description = when {
        !hasPermission && isPermissionDenied -> "Buka pengaturan untuk mengizinkan mikrofon."
        !hasPermission -> "Mikrofon diperlukan untuk mencatat lewat suara."
        voiceState is VoiceState.Error -> voiceState.message
        voiceState is VoiceState.Processing -> ""
        else -> ""
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = if (isPermissionDenied || voiceState is VoiceState.Error) duit.expense else duit.textPrimary,
        textAlign = TextAlign.Center
    )
    if (description.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = duit.textSecondary,
            textAlign = TextAlign.Center
        )
    }
    if (voiceState is VoiceState.Error && voiceState.canUseOnline) {
        TextButton(onClick = onUseOnlineVoice, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Gunakan layanan suara Android")
        }
    }
    Spacer(Modifier.height(24.dp))
    DuitAingButton(
        text = when {
            !hasPermission && isPermissionDenied -> "PENGATURAN"
            !hasPermission -> "IZINKAN MIKROFON"
            voiceState is VoiceState.Error -> "COBA LAGI"
            voiceState is VoiceState.Processing -> "Memproses..."
            voiceState is VoiceState.Listening -> "Selesai"
            else -> "Mulai merekam"
        },
        onClick = when {
            !hasPermission && isPermissionDenied -> onOpenSettings
            !hasPermission -> onRequestPermission
            voiceState is VoiceState.Listening -> onStopListening
            else -> onRetryListening
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = voiceState !is VoiceState.Processing,
        variant = DuitAingButtonVariant.PRIMARY,
        height = 48.dp,
        shadowOffset = 0.dp
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onManualClick, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("CATAT MANUAL", color = duit.textPrimary)
        }
        TextButton(onClick = onTransferClick, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("Transfer", color = duit.textPrimary)
        }
    }

    Text(
        text = "Contoh ucapan",
        style = MaterialTheme.typography.labelMedium,
        color = duit.textSecondary
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "Beli kopi 20 ribu pakai GoPay",
            "Gaji masuk lima juta ke BCA",
            "Bayar bensin 50 ribu dari BCA",
            "Transfer 1 juta dari BCA ke Tabungan",
            "Makan siang 35 ribu pakai BCA",
            "Kemarin makan malam 85 ribu pakai cash"
        ).forEach { phrase ->
            OutlinedButton(onClick = { onSimulate(phrase) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(phrase, color = duit.textPrimary)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (usesOnlineVoice) {
        Text(
            text = "Audio dapat diproses online oleh layanan suara Android.",
            style = MaterialTheme.typography.bodySmall,
            color = duit.textSecondary,
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun VoiceConfirmationView(
    result: ParsedVoiceResult,
    activeAssets: List<Asset>,
    recentCategories: List<Category>,
    recentAssets: List<Asset>,
    onResultChanged: (ParsedVoiceResult) -> Unit,
    onEdit: () -> Unit,
    onSave: (Transaction) -> Unit,
    onRetry: () -> Unit
) {
    val duit = MaterialTheme.duit
    var editedAmount by remember(result) { mutableStateOf(result.amount?.toString() ?: "") }
    var selectedAsset by remember(result) { mutableStateOf(result.matchedAsset) }
    var selectedDestAsset by remember(result) { mutableStateOf(result.destinationAsset) }
    var isSaving by remember(result) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val amountLong = editedAmount.toLongOrNull() ?: 0L
    val isReadyToSave = amountLong > 0 && selectedAsset != null &&
            (result.type != TransactionType.TRANSFER || selectedDestAsset != null)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Raw recognized text quote
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, duit.border, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = duit.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.FormatQuote,
                    contentDescription = null,
                    tint = duit.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "\"${result.rawText}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = duit.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transaction Type Badge with "DITANGKAP" Tag
        val typeDisplay = when (result.type) {
            TransactionType.INCOME -> "PEMASUKAN"
            TransactionType.EXPENSE -> "PENGELUARAN"
            TransactionType.TRANSFER -> "TRANSFER"
            else -> "TRANSAKSI"
        }
        val typeColor = when (result.type) {
            TransactionType.INCOME -> duit.income
            TransactionType.EXPENSE -> duit.expense
            TransactionType.TRANSFER -> duit.transfer
            else -> duit.primary
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EyebrowTag(
                text = "DITANGKAP",
                variant = EyebrowVariant.PRIMARY
            )
            Text(
                text = typeDisplay,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                ),
                color = typeColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Description
        Text(
            text = result.description,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp
            ),
            color = duit.textPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Nominal Section (With Missing Alert if null)
        if (result.isAmountMissing || amountLong <= 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, duit.expense.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = duit.expenseContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "NOMINAL BELUM DIKETAHUI",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = duit.expense
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editedAmount,
                        onValueChange = { editedAmount = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("Ketik nominal (Rp)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        visualTransformation = ThousandSeparatorVisualTransformation(),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PILIH CEPAT:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = duit.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FadingHorizontalScrollRow(
                        fadeColor = duit.surfaceVariant,
                        contentPadding = PaddingValues(start = 2.dp, end = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        listOf(10000L, 15000L, 20000L, 25000L, 35000L, 50000L, 100000L).forEach { quickNominal ->
                            AssistChip(
                                onClick = { editedAmount = quickNominal.toString() },
                                label = { Text(Formatters.formatRupiah(quickNominal), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, duit.border, RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            }
        } else {
            val prefix = when (result.type) {
                TransactionType.INCOME -> "+"
                TransactionType.EXPENSE -> "−"
                else -> ""
            }
            Text(
                text = prefix + Formatters.formatRupiah(amountLong),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = typeColor,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = duit.border, thickness = 1.dp)
        Spacer(modifier = Modifier.height(14.dp))

        // Details Grid
        // 1. Kategori
        if (result.type != TransactionType.TRANSFER) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KATEGORI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = duit.textSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = (result.detectedCategory?.name ?: "Lainnya").uppercase(),
                    modifier = Modifier.weight(1f, fill = false),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = duit.textPrimary
                )
            }

            // Quick Category selector chips from recentCategories if category is missing
            if (result.detectedCategory == null && recentCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TERAKHIR DIGUNAKAN:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = duit.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    recentCategories.forEach { cat ->
                        AssistChip(
                            onClick = {
                                onResultChanged(result.copy(detectedCategory = cat))
                            },
                            label = { Text(cat.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, duit.border, RoundedCornerShape(6.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // 2. Aset Sumber
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (result.type == TransactionType.TRANSFER) "ASET SUMBER" else "ASET",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = duit.textSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))

            if (selectedAsset != null) {
                Text(
                    text = selectedAsset!!.name.uppercase(),
                    modifier = Modifier.weight(1f, fill = false),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = duit.primary
                )
            } else {
                Text(
                    text = "ASET BELUM DIPILIH",
                    modifier = Modifier.weight(1f, fill = false),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = duit.expense
                )
            }
        }

        // Missing / Mismatch Asset Handling
        if (result.assetMismatchName != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Aset \"${result.assetMismatchName}\" belum tersedia di akun Anda. Pilih aset lain:",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = duit.expense
            )
        }

        if (selectedAsset == null) {
            if (recentAssets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TERAKHIR DIGUNAKAN:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = duit.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    recentAssets.forEach { ast ->
                        FilterChip(
                            selected = false,
                            onClick = { selectedAsset = ast },
                            label = { Text(ast.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, duit.border, RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                activeAssets.forEach { ast ->
                    FilterChip(
                        selected = false,
                        onClick = { selectedAsset = ast },
                        label = { Text(ast.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.border(1.dp, duit.border, RoundedCornerShape(6.dp))
                    )
                }
            }
        }

        // 3. Aset Tujuan (if transfer)
        if (result.type == TransactionType.TRANSFER) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ASET TUJUAN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = duit.textSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))

                if (selectedDestAsset != null) {
                    Text(
                        text = selectedDestAsset!!.name.uppercase(),
                        modifier = Modifier.weight(1f, fill = false),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = duit.transfer
                    )
                } else {
                    Text(
                        text = "PILIH TUJUAN",
                        modifier = Modifier.weight(1f, fill = false),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = duit.expense
                    )
                }
            }

            if (selectedDestAsset == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    activeAssets.filter { it.id != selectedAsset?.id }.forEach { ast ->
                        FilterChip(
                            selected = false,
                            onClick = { selectedDestAsset = ast },
                            label = { Text(ast.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, duit.border, RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Tanggal & Sumber
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TANGGAL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = duit.textSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Formatters.formatDateReadable(result.transactionDate),
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = duit.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "METODE INPUT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = duit.textSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "VOICE INPUT",
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp
                ),
                color = duit.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: [Bicara Lagi] [Simpan Transaksi →] (Signature CTA)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            DuitAingButton(
                text = "ULANGI",
                onClick = onRetry,
                modifier = Modifier.weight(0.9f),
                leadingIcon = Icons.Rounded.Refresh,
                variant = DuitAingButtonVariant.OUTLINE,
                height = 50.dp,
                shadowOffset = 0.dp
            )

            DuitAingButton(
                text = "SIMPAN TRANSAKSI",
                onClick = {
                    if (isReadyToSave && !isSaving) {
                        isSaving = true
                        val finalTx = Transaction(
                            id = "tx_${UUID.randomUUID()}",
                            type = result.type?.value ?: TransactionType.EXPENSE.value,
                            amount = amountLong,
                            description = result.description,
                            categoryId = result.detectedCategory?.id,
                            assetId = selectedAsset!!.id,
                            destinationAssetId = selectedDestAsset?.id,
                            transactionDate = result.transactionDate,
                            transactionTime = result.transactionTime,
                            note = result.note,
                            inputMethod = "voice"
                        )
                        onSave(finalTx)
                    }
                },
                enabled = isReadyToSave && !isSaving,
                isLoading = isSaving,
                modifier = Modifier.weight(1.35f),
                leadingIcon = Icons.Rounded.Check,
                variant = DuitAingButtonVariant.PRIMARY,
                height = 50.dp,
                shadowOffset = 0.dp
            )
        }
    }
}
