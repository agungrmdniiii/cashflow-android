package com.cashflow.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            voiceManager.startListening()
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

    // Start listening when dialog opens if permission is already granted
    LaunchedEffect(Unit) {
        if (hasMicPermission) {
            voiceManager.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.stopListening()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 20.dp)
                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = PebbleSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
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
                                .background(JadePrimaryContainer)
                                .border(1.dp, JadePrimary.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = JadePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "RECORD CASH FLOW",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = DarkSurface
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
                                .background(PebbleSurface)
                                .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Tutup",
                                tint = DarkSurface,
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
                    // RECORDING / LISTENING STATE
                    VoiceRecordingView(
                        voiceState = voiceState,
                        audioRms = audioRms,
                        hasPermission = hasMicPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onStopListening = {
                            voiceManager.stopListening()
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
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordingView(
    voiceState: VoiceState,
    audioRms: Float,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStopListening: () -> Unit,
    onSimulate: (String) -> Unit,
    onManualClick: () -> Unit,
    onTransferClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Spacer(modifier = Modifier.height(8.dp))

    // Microphone Core Block with Neo-Brutalist Frame
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (voiceState is VoiceState.Listening) JadePrimary
                else if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF28342F)
                else LightDarkSurface
            )
            .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = "Mendengarkan",
            tint = Color.White,
            modifier = Modifier.size(42.dp)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Geometric Audio Waveform Activity Bars
    Row(
        modifier = Modifier
            .height(28.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                    .background(if (voiceState is VoiceState.Listening) JadePrimary else DarkBorder.copy(alpha = 0.3f))
                    .border(0.5.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(2.dp))
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Title State
    when {
        !hasPermission -> {
            Text(
                text = "AKSES MICROPHONE DIPERLUKAN",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = StatusNegative,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Izinkan akses microphone untuk mencatat transaksi dengan suara.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            DuitAingButton(
                text = "IZINKAN AKSES",
                onClick = onRequestPermission,
                leadingIcon = Icons.Rounded.Mic,
                variant = DuitAingButtonVariant.PRIMARY,
                height = 44.dp,
                shadowOffset = 2.dp
            )
        }
        voiceState is VoiceState.Processing -> {
            Text(
                text = "MEMPROSES...",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = DarkSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mengekstrak jenis, nominal, kategori, dan aset...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        voiceState is VoiceState.Error -> {
            Text(
                text = (voiceState as VoiceState.Error).message,
                style = MaterialTheme.typography.bodyMedium,
                color = StatusNegative,
                textAlign = TextAlign.Center
            )
        }
        else -> {
            Text(
                text = "MENDENGARKAN...",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = DarkSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "“Sebutkan transaksi Anda.”",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = DarkSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Contoh: “Beli makan 30 ribu pakai BCA”",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = TextSecondary
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // Quick Simulation / Test Chips (Essential for emulator and instant testing)
    Text(
        text = "CONTOH SUARA CEPAT:",
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        ),
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val testPhrases = listOf(
            "Beli kopi 20 ribu pakai GoPay",
            "Gaji masuk lima juta ke BCA",
            "Bayar bensin 50 ribu dari BCA",
            "Transfer 1 juta dari BCA ke Tabungan",
            "Makan siang 35 ribu pakai BCA",
            "Kemarin makan malam 85 ribu pakai cash"
        )

        testPhrases.forEach { phrase ->
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PebbleSurface)
                    .border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { onSimulate(phrase) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = phrase,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = DarkSurface
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))
    HorizontalDivider(color = PebbleBorder, thickness = 0.8.dp)
    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "ATAU CATAT SECARA MANUAL:",
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        ),
        color = TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Catat Manual (Pengeluaran / Pemasukan)
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PebbleSurface)
                .border(1.dp, DarkBorder.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onManualClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = DarkSurface,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "CATAT MANUAL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp,
                        fontSize = 11.sp
                    ),
                    color = DarkSurface
                )
            }
        }

        // Transfer Antar Rekening
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PebbleSurface)
                .border(1.dp, DarkBorder.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onTransferClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = DarkSurface,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "TRANSFER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp,
                        fontSize = 11.sp
                    ),
                    color = DarkSurface
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Action Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DuitAingButton(
            text = "BATAL",
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            variant = DuitAingButtonVariant.OUTLINE,
            height = 48.dp,
            shadowOffset = 2.dp
        )

        DuitAingButton(
            text = "SELESAI",
            onClick = onStopListening,
            modifier = Modifier.weight(1f),
            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
            variant = DuitAingButtonVariant.PRIMARY,
            height = 48.dp,
            shadowOffset = 2.dp
        )
    }
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
    var editedAmount by remember(result) { mutableStateOf(result.amount?.toString() ?: "") }
    var selectedAsset by remember(result) { mutableStateOf(result.matchedAsset) }
    var selectedDestAsset by remember(result) { mutableStateOf(result.destinationAsset) }

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
                .border(1.dp, DarkBorder.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = PebbleSurfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.FormatQuote,
                    contentDescription = null,
                    tint = DarkSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "\"${result.rawText}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = DarkSurface
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
            TransactionType.INCOME -> StatusPositive
            TransactionType.EXPENSE -> StatusNegative
            TransactionType.TRANSFER -> StatusTransfer
            else -> JadePrimary
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EyebrowTag(
                text = "DITANGKAP",
                variant = EyebrowVariant.JADE
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
            color = DarkSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Nominal Section (With Missing Alert if null)
        if (result.isAmountMissing || amountLong <= 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StatusNegative.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = StatusNegativeContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "NOMINAL BELUM DIKETAHUI",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = StatusNegative
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editedAmount,
                        onValueChange = { editedAmount = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("Ketik nominal (Rp)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10000L, 15000L, 20000L, 25000L, 35000L, 50000L, 100000L).forEach { quickNominal ->
                            AssistChip(
                                onClick = { editedAmount = quickNominal.toString() },
                                label = { Text(Formatters.formatRupiah(quickNominal), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
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
        HorizontalDivider(color = DarkBorder.copy(alpha = 0.15f), thickness = 1.dp)
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
                    color = TextSecondary
                )
                Text(
                    text = (result.detectedCategory?.name ?: "Lainnya").uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = DarkSurface
                )
            }

            // Quick Category selector chips from recentCategories if category is missing
            if (result.detectedCategory == null && recentCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TERAKHIR DIGUNAKAN:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentCategories.forEach { cat ->
                        AssistChip(
                            onClick = {
                                onResultChanged(result.copy(detectedCategory = cat))
                            },
                            label = { Text(cat.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
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
                color = TextSecondary
            )

            if (selectedAsset != null) {
                Text(
                    text = selectedAsset!!.name.uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = JadePrimary
                )
            } else {
                Text(
                    text = "ASET BELUM DIPILIH",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = StatusNegative
                )
            }
        }

        // Missing / Mismatch Asset Handling
        if (result.assetMismatchName != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Aset \"${result.assetMismatchName}\" belum tersedia di akun Anda. Pilih aset lain:",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = StatusNegative
            )
        }

        if (selectedAsset == null) {
            if (recentAssets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TERAKHIR DIGUNAKAN:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentAssets.forEach { ast ->
                        FilterChip(
                            selected = false,
                            onClick = { selectedAsset = ast },
                            label = { Text(ast.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, JadePrimary.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeAssets.forEach { ast ->
                    FilterChip(
                        selected = false,
                        onClick = { selectedAsset = ast },
                        label = { Text(ast.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
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
                    color = TextSecondary
                )

                if (selectedDestAsset != null) {
                    Text(
                        text = selectedDestAsset!!.name.uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = StatusTransfer
                    )
                } else {
                    Text(
                        text = "PILIH TUJUAN",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = StatusNegative
                    )
                }
            }

            if (selectedDestAsset == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeAssets.filter { it.id != selectedAsset?.id }.forEach { ast ->
                        FilterChip(
                            selected = false,
                            onClick = { selectedDestAsset = ast },
                            label = { Text(ast.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, DarkBorder.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Tanggal & Sumber
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "TANGGAL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = TextSecondary
            )
            Text(
                text = Formatters.formatDateReadable(result.transactionDate),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = DarkSurface
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "METODE INPUT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = TextSecondary
            )
            Text(
                text = "VOICE INPUT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = JadePrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: [Bicara Lagi] [Simpan Transaksi →] (Signature CTA)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DuitAingButton(
                text = "ULANGI",
                onClick = onRetry,
                modifier = Modifier.weight(0.9f),
                leadingIcon = Icons.Rounded.Refresh,
                variant = DuitAingButtonVariant.OUTLINE,
                height = 50.dp,
                shadowOffset = 2.dp
            )

            DuitAingButton(
                text = "SIMPAN TRANSAKSI",
                onClick = {
                    if (isReadyToSave) {
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
                enabled = isReadyToSave,
                modifier = Modifier.weight(1.35f),
                leadingIcon = Icons.Rounded.Check,
                trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                variant = DuitAingButtonVariant.PRIMARY,
                height = 50.dp,
                shadowOffset = 3.dp
            )
        }
    }
}
