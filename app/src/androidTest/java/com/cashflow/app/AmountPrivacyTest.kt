package com.cashflow.app

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.cashflow.app.ui.components.*
import org.junit.Rule
import org.junit.Test

@androidx.test.filters.SdkSuppress(maxSdkVersion = 36)
class AmountPrivacyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenAmountsDoNotExposeTheirValueToSemantics() {
        compose.setContent {
            CompositionLocalProvider(LocalAmountsHidden provides true) {
                Text(Formatters.displayRupiah(987654L))
            }
        }
        compose.onNodeWithText("Rp ••••••").assertIsDisplayed()
        compose.onNodeWithText("Rp 987.654").assertDoesNotExist()
    }
}
