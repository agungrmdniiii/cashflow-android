package com.cashflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.ui.theme.*

/**
 * Standardized Neo-Brutalist Confirmation Dialog for "Duit Aing"
 * Guarantees 100% uniform design, typography, buttons, and silky-smooth spring animation.
 */
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = transitionState,
            enter = scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(200)) +
                    slideInVertically(
                        initialOffsetY = { 50 },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    ),
            exit = scaleOut(targetScale = 0.92f, animationSpec = tween(160)) +
                    fadeOut(animationSpec = tween(160))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, DarkBorder),
                colors = CardDefaults.cardColors(containerColor = PebbleSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 21.sp,
                            fontSize = 13.5.sp
                        ),
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DuitAingButton(
                            text = dismissText,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            variant = DuitAingButtonVariant.OUTLINE,
                            height = 42.dp,
                            shadowOffset = 2.dp
                        )

                        DuitAingButton(
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.3f),
                            variant = confirmVariant,
                            height = 42.dp,
                            shadowOffset = 2.dp
                        )
                    }
                }
            }
        }
    }
}
