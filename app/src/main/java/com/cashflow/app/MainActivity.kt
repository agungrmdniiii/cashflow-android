package com.cashflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.cashflow.app.ui.CashflowApp
import com.cashflow.app.ui.CashflowViewModel
import com.cashflow.app.ui.theme.CashflowTheme

class MainActivity : FragmentActivity() {

    private val viewModel: CashflowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial shortcut or widget action if present
        intent?.action?.let { action ->
            viewModel.handleIntentAction(action)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            CashflowTheme(themeMode = themeMode) {
                CashflowApp(viewModel = viewModel)
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

    override fun onStop() {
        super.onStop()
        // Lock app when user leaves if biometric is enabled
        viewModel.lockApp()
    }
}
