package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.theme.AppSpacing
import com.cashflow.app.ui.theme.duit

/**
 * Standardized Neo-Brutalist Confirmation Bottom Sheet for "Duit Aing".
 * Formatted as a uniform bottom sheet per design specifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuitAingConfirmDialog(
    isOpen: Boolean,
    title: String,
    message: String,
    confirmText: String = "HAPUS",
    confirmVariant: DuitAingButtonVariant = DuitAingButtonVariant.DANGER,
    dismissText: String = "BATAL",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val duit = MaterialTheme.duit
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = duit.surface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(duit.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                ),
                color = duit.textPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 21.sp,
                    fontSize = 13.5.sp
                ),
                color = duit.textSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                DuitAingButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variant = DuitAingButtonVariant.SECONDARY,
                    height = 44.dp,
                    shadowOffset = 0.dp
                )

                DuitAingButton(
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    variant = confirmVariant,
                    height = 44.dp,
                    shadowOffset = 0.dp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
