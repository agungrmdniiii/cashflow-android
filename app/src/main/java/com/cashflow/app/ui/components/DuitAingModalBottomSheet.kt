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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.duit

/**
 * Standardized Neo-Brutalist Modal Bottom Sheet for "Duit Aing".
 * Replaces scattered centered dialogs with a uniform, tactile bottom sheet interface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuitAingModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = androidx.compose.material3.BottomSheetDefaults.windowInsets,
    dragHandle: @Composable (() -> Unit)? = {
        val duit = MaterialTheme.duit
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 8.dp)
                .width(44.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(duit.borderStrong)
        )
    },
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val duit = MaterialTheme.duit
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = duit.surface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = dragHandle,
        contentWindowInsets = { windowInsets },
        modifier = modifier
    ) {
        content()
    }
}
