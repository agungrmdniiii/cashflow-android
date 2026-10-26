package com.cashflow.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cashflow.app.data.model.TransactionType
import com.cashflow.app.ui.components.*
import com.cashflow.app.ui.components.home.QuickActions
import com.cashflow.app.ui.theme.CashflowTheme
import org.junit.Assert.*
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
class LargeTextLayoutTest {
    // Read layout semantics directly; Espresso's input bridge does not support the API 37 preview emulator.
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: ComponentActivity
    private val scale = mutableFloatStateOf(1f)
    private val dark = mutableStateOf(false)

    private fun preview(content: @Composable () -> Unit) {
        activity = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext, ComponentActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        instrumentation.runOnMainSync { activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.floatValue)) {
                CashflowTheme(darkTheme = dark.value) { Box(Modifier.width(320.dp)) { content() } }
            }
        } }
    }

    private fun roots(view: android.view.View): List<ViewRootForTest> = when (view) {
        is ViewRootForTest -> listOf(view)
        is android.view.ViewGroup -> (0 until view.childCount).flatMap { roots(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun verifyScales(vararg labels: String, singleLine: Boolean = false) {
        try { for (theme in listOf(false, true)) for (fontScale in listOf(1f, 1.3f, 1.5f)) {
            instrumentation.runOnMainSync { dark.value = theme; scale.floatValue = fontScale }
            for (label in labels) {
                val results = mutableListOf<TextLayoutResult>()
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                while (results.none { it.layoutInput.density.fontScale == fontScale } && android.os.SystemClock.uptimeMillis() < deadline) {
                    android.os.SystemClock.sleep(30)
                    instrumentation.runOnMainSync {
                        results.clear()
                        for (root in roots(activity.window.decorView)) for (node in root.semanticsOwner.getAllSemanticsNodes(false)) {
                            if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true)
                                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                        }
                    }
                }
                assertTrue("No text layout for $label", results.isNotEmpty())
                for (layout in results) {
                    assertEquals(fontScale, layout.layoutInput.density.fontScale, 0f)
                    // A paragraph can retain unused alignment space; compare the drawn lines, allowing pixel rounding.
                    assertTrue("$label is too wide at fontScale=$fontScale, dark=$theme",
                        (0 until layout.lineCount).all { layout.getLineRight(it) - layout.getLineLeft(it) <= layout.size.width + 1f })
                    assertTrue("$label is too tall at fontScale=$fontScale, dark=$theme", layout.multiParagraph.height <= layout.size.height + 1f)
                    assertFalse("$label loses lines at fontScale=$fontScale", layout.multiParagraph.didExceedMaxLines)
                    if (singleLine) assertEquals("$label must not split into fragments at fontScale=$fontScale", 1, layout.lineCount)
                    assertFalse("$label is ellipsized at fontScale=$fontScale", (0 until layout.lineCount).any { layout.isLineEllipsized(it) })
                }
            }
        } } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun buttonsGrowToFitTheirFullLabel() {
        preview { DuitAingButton("Tambah Pengeluaran", {}, Modifier.width(140.dp), leadingIcon = Icons.Rounded.Add) }
        verifyScales("Tambah Pengeluaran")
    }

    @Test fun tabsKeepTheirLabelsAndCountsAtLargeFontSizes() {
        preview { DuitAingSegmentedControl(listOf(SegmentItem(0, "ASET SAYA"), SegmentItem(1, "HUTANG / PIUTANG", 12)), 0, {}) }
        verifyScales("ASET SAYA", "HUTANG / PIUTANG", "12")
    }

    @Test fun transactionTypeLabelsStayReadableInTheForm() {
        preview { TransactionTypeSegmentedControl(TransactionType.EXPENSE, {}) }
        verifyScales("Pengeluaran", "Pemasukan", "Transfer", singleLine = true)
    }

    @Test fun quickActionsKeepTheFullExpenseAndIncomeLabels() {
        preview { QuickActions({}, {}, {}, {}) }
        verifyScales("Pengeluaran", "Pemasukan", "Transfer", "Suara")
    }
}
