package com.cashflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.ChatMessage
import com.cashflow.app.data.model.ChatSender
import com.cashflow.app.data.model.ChatTransactionEvidence
import com.cashflow.app.data.model.summarizeTransactions
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.data.service.FinancialInsightService
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.components.DuitAingModalBottomSheet
import com.cashflow.app.ui.components.FadingHorizontalScrollRow
import com.cashflow.app.ui.theme.AppSpacing
import com.cashflow.app.ui.theme.duit
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialChatSheet(
    viewModel: CashflowViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DuitAingModalBottomSheet(
        onDismissRequest = onDismiss,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) {
        FinancialChatContent(viewModel, onDismiss)
    }
}

@Composable
fun FinancialChatContent(
    viewModel: CashflowViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
    isTourPreview: Boolean = false,
    onTourTargetPositioned: (Int, Rect) -> Unit = { _, _ -> }
) {
    val duit = MaterialTheme.duit
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isChatLoading.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val assets by viewModel.assets.collectAsState()
    val isBalanceHidden by viewModel.isBalanceHidden.collectAsState()
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val assetMap = remember(assets) { assets.associateBy { it.id } }
    var selectedEvidence by remember { mutableStateOf<ChatTransactionEvidence?>(null) }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val suggestions = remember(chatMessages) { FinancialInsightService.suggestedQuestions(chatMessages) }

    // Auto-scroll ke pesan terbaru
    LaunchedEffect(chatMessages.size, isLoading) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

        Column(
            modifier = modifier
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. Header Sheet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(duit.primary.copy(alpha = 0.15f))
                            .border(1.dp, duit.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = duit.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(Modifier.onGloballyPositioned { onTourTargetPositioned(21, it.boundsInRoot()) }) {
                        Text(
                            text = "Tanya Vina",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = duit.textPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(duit.accentLime)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Vina • 100% offline",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                color = duit.textSecondary
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tombol Bersihkan Percakapan
                    IconButton(
                        onClick = { if (!isTourPreview) viewModel.clearChat() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Bersihkan Percakapan",
                            tint = duit.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Tombol Tutup
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup",
                            tint = duit.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // 2. Daftar Pesan Percakapan (Scrollable)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chatMessages, key = { it.id }, contentType = { it.sender }) { msg ->
                    ChatBubbleItem(message = msg, onEvidence = {
                        focusManager.clearFocus()
                        selectedEvidence = it
                    })
                }

                if (isLoading) {
                    item(key = "typing_indicator") {
                        AssistantTypingIndicator()
                    }
                }
            }

            // 3. Rekomendasi Pertanyaan Cepat (Inspirasi awal)
            if (!isLoading && suggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (chatMessages.size <= 1) "MULAI PERCAKAPAN" else "LANJUTKAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            fontSize = 11.sp
                        ),
                        color = duit.textSecondary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )

                    FadingHorizontalScrollRow(
                        fadeColor = duit.surface,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        suggestions.forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(duit.surfaceVariant)
                                    .border(1.dp, duit.border, RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) {
                                        if (!isTourPreview) {
                                            viewModel.sendChatMessage(suggestion)
                                            focusManager.clearFocus()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = duit.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = duit.border, thickness = 1.dp)

            // 4. Input Bar Percakapan
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(duit.surface)
                    .onGloballyPositioned { onTourTargetPositioned(20, it.boundsInRoot()) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Tanyakan apa saja ke Vina...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            color = duit.textSecondary
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = duit.surfaceVariant,
                        unfocusedContainerColor = duit.surfaceVariant,
                        focusedBorderColor = duit.primary,
                        unfocusedBorderColor = duit.border,
                        focusedTextColor = duit.textPrimary,
                        unfocusedTextColor = duit.textPrimary
                    ),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isTourPreview && inputText.isNotBlank() && !isLoading) {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendChatMessage(textToSend)
                                focusManager.clearFocus()
                            }
                        }
                    )
                )

                // Tombol Kirim
                val isSendEnabled = !isTourPreview && inputText.isNotBlank() && !isLoading
                IconButton(
                    onClick = {
                        if (isSendEnabled) {
                            val textToSend = inputText
                            inputText = ""
                            viewModel.sendChatMessage(textToSend)
                            focusManager.clearFocus()
                        }
                    },
                    enabled = isSendEnabled,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSendEnabled) duit.primary else duit.surfaceVariant)
                        .border(
                            1.dp,
                            if (isSendEnabled) duit.borderStrong else duit.border,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Kirim Pesan",
                        tint = if (isSendEnabled) duit.onPrimary else duit.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    selectedEvidence?.let { evidence ->
        val rows = remember(evidence) { evidence.transactions.sortedWith(compareByDescending<com.cashflow.app.data.model.Transaction> {
            it.transactionDate }.thenByDescending { it.transactionTime }) }
        val totals = remember(evidence) { summarizeTransactions(evidence.transactions) }
        ReportTransactionsSheet(
            title = evidence.label,
            periodLabel = "${Formatters.formatDateReadable(evidence.range.start.toString())} – ${Formatters.formatDateReadable(evidence.range.end.toString())}\nData saat jawaban dibuat.",
            transactions = rows, categories = categoryMap, assets = assetMap, isBalanceHidden = isBalanceHidden,
            summaryText = if (evidence.isCashflow) "Pemasukan ${Formatters.formatRupiah(totals.income)}\nPengeluaran ${Formatters.formatRupiah(totals.expense)}\nArus kas bersih ${Formatters.formatRupiah(totals.income - totals.expense)}" else null,
            onDismiss = { selectedEvidence = null },
            onOpen = { snapshot ->
                val current = viewModel.transactions.value.firstOrNull { it.id == snapshot.id }
                if (current == null) viewModel.showSnackbar("Transaksi ini sudah dihapus. Rincian menampilkan data saat jawaban dibuat.")
                else {
                    selectedEvidence = null
                    onDismiss()
                    if (current != snapshot) viewModel.showSnackbar("Detail menampilkan catatan terbaru; jawaban Vina memakai data sebelumnya.")
                    viewModel.selectedTransactionForDetail.value = current
                }
            })
    }
}

@Composable
private fun ChatBubbleItem(message: ChatMessage, onEvidence: (ChatTransactionEvidence) -> Unit) {
    val duit = MaterialTheme.duit
    val isUser = message.sender == ChatSender.USER
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(duit.primary.copy(alpha = 0.15f))
                    .border(1.dp, duit.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = duit.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) duit.primary else duit.surfaceVariant
                    )
                    .border(
                        1.dp,
                        if (isUser) duit.borderStrong else duit.border,
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                FormattedMarkdownText(
                    rawText = message.text,
                    textColor = if (isUser) duit.onPrimary else duit.textPrimary
                )
            }

            if (!isUser && !message.isError) message.transactionEvidence.forEach { evidence ->
                Spacer(Modifier.height(8.dp))
                Text("${if (evidence.isComparison) "Pembanding" else "Periode"}: ${Formatters.formatDateReadable(evidence.range.start.toString())} – ${Formatters.formatDateReadable(evidence.range.end.toString())}",
                    style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
                TextButton(onClick = { onEvidence(evidence) }) {
                    Text(if (evidence.isComparison) "Lihat transaksi pembanding" else "Lihat transaksi")
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = duit.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun AssistantTypingIndicator() {
    val duit = MaterialTheme.duit

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(duit.primary.copy(alpha = 0.15f))
                .border(1.dp, duit.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = duit.primary,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .background(duit.surfaceVariant)
                .border(1.dp, duit.border, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = duit.primary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Vina sedang menganalisis data keuangan Anda...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = duit.textSecondary
                )
            }
        }
    }
}

/**
 * Merender teks markdown ringan (bold **teks**, bullet points •) dengan tipografi bersih.
 */
@Composable
private fun FormattedMarkdownText(
    rawText: String,
    textColor: Color
) {
    val annotatedString = remember(rawText) {
        buildAnnotatedString {
            var currentIndex = 0
            val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
            val matches = boldRegex.findAll(rawText)

            for (match in matches) {
                // Teks biasa sebelum bold
                if (match.range.first > currentIndex) {
                    append(rawText.substring(currentIndex, match.range.first))
                }
                // Teks bold
                val boldContent = match.groupValues[1]
                val boldStart = length
                append(boldContent)
                addStyle(
                    style = SpanStyle(fontWeight = FontWeight.ExtraBold),
                    start = boldStart,
                    end = length
                )
                currentIndex = match.range.last + 1
            }

            // Sisa teks setelah match terakhir
            if (currentIndex < rawText.length) {
                append(rawText.substring(currentIndex))
            }
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        color = textColor
    )
}
