package com.cashflow.app

import com.cashflow.app.data.service.FinancialInsightService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GeminiFallbackTest {
    @Test fun overloadAndInvalidKeysDoNotTriggerAnotherSlowNetworkAttempt() = runBlocking {
        for (message in listOf("HTTP 503: unavailable", "HTTP 429: quota", "HTTP 400: invalid key", "Read timed out")) {
            var calls = 0
            val result = FinancialInsightService.withGeminiModelFallback {
                calls++
                if (calls == 1) Result.failure(IOException(message)) else Result.success("unwanted retry")
            }
            assertTrue(message, result.isFailure)
            assertEquals(message, 1, calls)
        }
    }

    @Test fun unsupportedModelCanStillUseTheCompatibilityFallback() = runBlocking {
        val result = FinancialInsightService.withGeminiModelFallback { model ->
            if (model == "gemini-3.8-flash") Result.failure(IOException("HTTP 404: model not found"))
            else Result.success("compatible answer")
        }
        assertEquals("compatible answer", result.getOrThrow())
    }
}
