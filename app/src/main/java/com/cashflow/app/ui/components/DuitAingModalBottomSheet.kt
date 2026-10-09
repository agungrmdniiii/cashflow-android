package com.cashflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.DarkBorder
import com.cashflow.app.ui.theme.PebbleSurface
import com.cashflow.app.ui.theme.TextTertiary

/**
 * Standardized Neo-Brutalist Modal Bottom Sheet for "Duit Aing".
 * Replaces scattered centered dialogs with a uniform, tactile bottom sheet interface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuitAingModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = {
        // Tactile Neo-Brutalist drag handle
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 6.dp)
                .width(44.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TextTertiary.copy(alpha = 0.4f))
        )
    },
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = PebbleSurface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = dragHandle,
        modifier = modifier
    ) {
        content()
    }
}
