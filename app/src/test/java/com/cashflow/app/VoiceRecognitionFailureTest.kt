package com.cashflow.app

import com.cashflow.app.voice.recognitionFailure
import org.junit.Assert.*
import org.junit.Test

class VoiceRecognitionFailureTest {
    @Test fun unavailableIndonesianModelExplainsTheProblem() {
        val failure = recognitionFailure(13)
        assertTrue(failure.message.contains("bahasa Indonesia", ignoreCase = true))
        assertTrue(failure.message.contains("offline", ignoreCase = true))
    }

    @Test fun unsupportedLanguageDoesNotTellTheUserToSpeakLouder() {
        assertTrue(recognitionFailure(12).message.contains("bahasa", ignoreCase = true))
    }

    @Test fun onlineFallbackIsOfferedOnlyWhenItNeedsConsent() {
        assertTrue(recognitionFailure(13).canUseOnline)
        assertTrue(recognitionFailure(12).canUseOnline)
        assertFalse(recognitionFailure(13, onlineAllowed = true).canUseOnline)
        assertFalse(recognitionFailure(9).canUseOnline)
        assertFalse(recognitionFailure(2).canUseOnline)
    }

    @Test fun permissionAndNetworkErrorsRemainSpecific() {
        assertTrue(recognitionFailure(9).message.contains("Izin", ignoreCase = true))
        assertTrue(recognitionFailure(2).message.contains("internet", ignoreCase = true))
    }
}
