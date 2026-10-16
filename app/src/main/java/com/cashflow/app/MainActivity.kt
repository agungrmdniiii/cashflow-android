package com.cashflow.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cashflow.app.ui.CashflowApp
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.theme.CashflowTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: CashflowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial shortcut or widget action if present
        intent?.action?.let { action ->
            viewModel.handleIntentAction(action)
        }

        // Apply FLAG_SECURE dynamically according to privacy preference
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isScreenshotBlocked.collect { blocked ->
                    if (blocked) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val hidden by viewModel.isBalanceHidden.collectAsState()
            CashflowTheme(themeMode = themeMode) {
                androidx.compose.runtime.CompositionLocalProvider(com.cashflow.app.ui.components.LocalAmountsHidden provides hidden) {
                    CashflowApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.action?.let { action ->
            viewModel.handleIntentAction(action)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }
}
