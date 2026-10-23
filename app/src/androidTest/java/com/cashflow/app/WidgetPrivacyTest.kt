package com.cashflow.app

import android.content.Intent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.widget.DuitAingQuickWidget
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Privacy preference only; never creates, edits, or deletes financial records. */
class WidgetPrivacyTest {
    @Test fun launcherOffersFourDistinctWidgetOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providers = AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == context.packageName }
        val names = providers.map { it.provider.className }.toSet()
        assertTrue("Saldo ringkas must be available in the Android widget picker", "com.cashflow.app.widget.DuitAingBalanceWidget" in names)
        assertTrue("Catat cepat must be available in the Android widget picker", "com.cashflow.app.widget.DuitAingEntryWidget" in names)
        assertTrue("Ringkasan bulanan must be available in the Android widget picker", "com.cashflow.app.widget.DuitAingSummaryWidget" in names)
        assertEquals(4, providers.size)
        assertEquals(4, providers.map { it.loadLabel(context.packageManager).toString() }.toSet().size)
    }

    @Test fun widgetEyeToggleUsesTheSavedPrivacyPreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager(context)
        val original = preferences.isBalanceHidden()
        try {
            preferences.setBalanceHidden(true)
            context.sendBroadcast(Intent(context, DuitAingQuickWidget::class.java)
                .setAction("com.cashflow.app.widget.TOGGLE_BALANCE_VISIBILITY"))
            val deadline = SystemClock.uptimeMillis() + 4000
            while (preferences.isBalanceHidden() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertFalse("The widget eye should reveal the balance when tapped from the hidden state", preferences.isBalanceHidden())
        } finally {
            preferences.setBalanceHidden(original)
            DuitAingQuickWidget.updateAllWidgets(context)
        }
    }

    @Test fun hiddenWidgetNeverContainsFinancialAmountsInTextOrAccessibility() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = PreferenceManager(context)
        val original = preferences.isBalanceHidden()
        try {
            preferences.setBalanceHidden(true)
            for (layout in listOf(R.layout.widget_duit_aing_quick, R.layout.widget_duit_aing_balance, R.layout.widget_duit_aing_summary)) {
                val remote = DuitAingQuickWidget.buildWidgetViews(context, layout)
                instrumentation.runOnMainSync {
                    val view = remote.apply(context, null)
                assertEquals("Rp ••••••", view.findViewById<TextView>(R.id.tv_widget_balance).text.toString())
                assertEquals("Saldo disembunyikan", view.findViewById<TextView>(R.id.tv_widget_subtitle).text.toString())
                val description = view.findViewById<LinearLayout>(R.id.layout_widget_info).contentDescription.toString()
                assertFalse(description.any { it.isDigit() })
                assertTrue(description.contains("Tampilkan saldo aset"))
                    if (layout == R.layout.widget_duit_aing_summary) {
                        assertEquals("Rp ••••••", view.findViewById<TextView>(R.id.tv_widget_income).text.toString())
                        assertEquals("Rp ••••••", view.findViewById<TextView>(R.id.tv_widget_expense).text.toString())
                    }
                }
            }
        } finally { preferences.setBalanceHidden(original) }
    }

    @Test fun realWidgetHostsToggleTogetherAndStayHiddenAfterRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = instrumentation.targetContext
        val preferences = PreferenceManager(context)
        val original = preferences.isBalanceHidden()
        val manager = AppWidgetManager.getInstance(context)
        val providerInfo = DuitAingQuickWidget.providers.map { provider -> manager.installedProviders.first { it.provider == ComponentName(context, provider) } }
        val ids = mutableListOf<Int>()
        var host: AppWidgetHost? = null
        var activity: ComponentActivity? = null
        val views = mutableListOf<AppWidgetHostView>()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        try {
            preferences.setBalanceHidden(false)
            instrumentation.runOnMainSync { host = AppWidgetHost(context, 0x44554954).also { it.startListening() } }
            val boundHost = host!!
            for (info in providerInfo) {
                val id = boundHost.allocateAppWidgetId().also { ids.add(it) }
                assertTrue("The isolated host should be allowed to bind its widget", manager.bindAppWidgetIdIfAllowed(id, info.provider))
            }
            activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
            val screen = activity!!
            instrumentation.runOnMainSync {
                val layout = LinearLayout(screen).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 32, 16, 0)
                    setOnApplyWindowInsetsListener { view, insets ->
                        view.setPadding(16, insets.getInsets(android.view.WindowInsets.Type.systemBars()).top + 32, 16, 0)
                        insets
                    }
                }
                val smallRow = LinearLayout(screen)
                for ((index, id) in ids.withIndex()) {
                    val view = boundHost.createView(screen, id, providerInfo[index])
                    views.add(view)
                    val density = context.resources.displayMetrics.density
                    if (index == 1 || index == 2) {
                        smallRow.addView(view, LinearLayout.LayoutParams(0, (180 * density).toInt(), 1f))
                        if (index == 2) layout.addView(smallRow)
                    } else layout.addView(view, LinearLayout.LayoutParams(-1, ((if (index == 0) 100 else 300) * density).toInt()))
                }
                screen.setContentView(layout)
            }
            DuitAingQuickWidget.updateAllWidgets(context)
            fun waitFor(message: String, condition: () -> Boolean) {
                val deadline = SystemClock.uptimeMillis() + 7000
                while (SystemClock.uptimeMillis() < deadline) {
                    var ready = false
                    instrumentation.runOnMainSync { ready = condition() }
                    if (ready) return
                    SystemClock.sleep(50)
                }
                fail(message)
            }
            fun balances() = views.mapNotNull { it.findViewById<TextView>(R.id.tv_widget_balance)?.text?.toString() }
            fun screenshot(name: String) {
                instrumentation.waitForIdleSync()
                // Host callbacks update text before SurfaceFlinger presents the next frame.
                SystemClock.sleep(250)
                val file = File(context.getExternalFilesDir(null), "widget-audit/$name.png")
                file.parentFile!!.mkdirs()
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            waitFor("All financial widgets should display a balance") { balances().size == 3 && balances().all { it.startsWith("Rp") && it.any(Char::isDigit) } }
            screenshot("visible")
            instrumentation.runOnMainSync { assertTrue(views[0].findViewById<LinearLayout>(R.id.layout_widget_info).performClick()) }
            waitFor("The eye must mask both widgets") { preferences.isBalanceHidden() && balances().all { it == "Rp ••••••" } }
            instrumentation.runOnMainSync {
                assertEquals("Rp ••••••", views.last().findViewById<TextView>(R.id.tv_widget_income).text.toString())
                assertEquals("Rp ••••••", views.last().findViewById<TextView>(R.id.tv_widget_expense).text.toString())
            }
            screenshot("hidden")
            DuitAingQuickWidget.updateAllWidgets(context)
            waitFor("Refresh must not reveal a hidden balance") { balances().all { it == "Rp ••••••" } }
            assertTrue(PreferenceManager(context).isBalanceHidden())
            instrumentation.runOnMainSync { assertTrue(views[1].findViewById<LinearLayout>(R.id.layout_widget_info).performClick()) }
            waitFor("The second widget should reveal both balances") { !preferences.isBalanceHidden() && balances().all { it.startsWith("Rp") && it.any(Char::isDigit) } }
            if (preferences.isInitialSetupCompleted()) {
                val repository = (context.applicationContext as CashflowApplication).repository
                kotlinx.coroutines.runBlocking { repository.awaitInitialLoad() }
                val before = repository.transactions.value.toList()
                instrumentation.runOnMainSync { assertTrue(views[2].findViewById<android.view.View>(R.id.btn_widget_income).performClick()) }
                fun incomeFormIsOpen() = instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("Catat pemasukan dana")?.isNotEmpty() == true
                val deadline = SystemClock.uptimeMillis() + 7000
                while (!incomeFormIsOpen() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                assertTrue("The income shortcut must open the income form", incomeFormIsOpen())
                assertEquals("Opening a widget shortcut must never save a transaction", before, repository.transactions.value)
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
        } finally {
            host?.stopListening()
            ids.forEach { host?.deleteAppWidgetId(it) }
            instrumentation.runOnMainSync { activity?.finish() }
            preferences.setBalanceHidden(original)
            DuitAingQuickWidget.updateAllWidgets(context)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test fun layoutsStayReadableAtTheirMinimumSizeWithLargeFonts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val original = PreferenceManager(base).isBalanceHidden()
        val problems = mutableListOf<String>()
        try {
            for (hidden in listOf(true, false)) {
                PreferenceManager(base).setBalanceHidden(hidden)
                for (scale in listOf(1f, 1.5f)) {
                val context = base.createConfigurationContext(android.content.res.Configuration(base.resources.configuration).apply { fontScale = scale })
                for ((layout, size) in listOf(R.layout.widget_duit_aing_balance to (156 to 160), R.layout.widget_duit_aing_entry to (176 to 144), R.layout.widget_duit_aing_summary to (280 to 280))) {
                    val remote = DuitAingQuickWidget.buildWidgetViews(context, layout)
                    instrumentation.runOnMainSync {
                        val view = remote.apply(context, null)
                        val density = context.resources.displayMetrics.density
                        view.measure(android.view.View.MeasureSpec.makeMeasureSpec((size.first * density).toInt(), android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec((size.second * density).toInt(), android.view.View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                        fun inspect(node: android.view.View) {
                            if (node is TextView && node.visibility == android.view.View.VISIBLE) {
                                val text = node.layout ?: return
                                val location = "${context.resources.getResourceEntryName(layout)} at $scale: ${node.text}"
                                if (text.height > node.height - node.compoundPaddingTop - node.compoundPaddingBottom) problems.add("Clipped $location (${text.height}px in ${node.height}px)")
                                for (line in 0 until text.lineCount) if (text.getEllipsisCount(line) > 0) problems.add("Truncated $location (view=${node.width}px layout=${text.width}px textSize=${node.textSize}px min=${node.autoSizeMinTextSize}px needed=${node.paint.measureText(node.text.toString())}px requested=${view.isLayoutRequested})")
                            }
                            if (node is android.view.ViewGroup) for (i in 0 until node.childCount) inspect(node.getChildAt(i))
                        }
                        inspect(view)
                    }
                }
                }
            }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        } finally { PreferenceManager(base).setBalanceHidden(original) }
    }
}
