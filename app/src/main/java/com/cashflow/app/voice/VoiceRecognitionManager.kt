package com.cashflow.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Processing : VoiceState()
    data class Success(val recognizedText: String) : VoiceState()
    data class Error(val message: String, val canUseOnline: Boolean = false) : VoiceState()
}

class VoiceRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionSession = 0

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopListening()
            _voiceState.value = VoiceState.Error("Izin mikrofon belum diberikan. Izinkan melalui Pengaturan Android atau catat secara manual.")
            return
        }
        stopListening()

        val onlineAllowed = com.cashflow.app.data.preferences.PreferenceManager(context).isOnlineVoiceEnabled()
        val onDeviceAvailable = android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!onlineAllowed && !onDeviceAvailable) {
            _voiceState.value = VoiceState.Error("Pengenalan suara offline belum tersedia di HP ini. Gunakan layanan suara Android atau catat manual.", canUseOnline = isRecognitionAvailable)
            return
        }

        if (onlineAllowed && !isRecognitionAvailable) {
            _voiceState.value = VoiceState.Error("Layanan pengenalan suara Android belum tersedia. Aktifkan atau perbarui layanan suara di HP, atau catat manual.")
            return
        }
        val session = recognitionSession
        try {
            speechRecognizer = (if (!onlineAllowed && android.os.Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (session != recognitionSession) return
                        _voiceState.value = VoiceState.Listening
                    }

                    override fun onBeginningOfSpeech() {
                        if (session != recognitionSession) return
                        _voiceState.value = VoiceState.Listening
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (session != recognitionSession) return
                        _audioRms.value = rmsdB.coerceIn(0f, 10f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (session != recognitionSession) return
                        _voiceState.value = VoiceState.Processing
                    }

                    override fun onError(error: Int) {
                        if (session != recognitionSession) return
                        android.util.Log.w("DuitAingVoice", "Recognition failed: code=$error, onDevice=${!onlineAllowed}")
                        this@VoiceRecognitionManager.stopListening()
                        _voiceState.value = recognitionFailure(error, onlineAllowed)
                    }

                    override fun onResults(results: Bundle?) {
                        if (session != recognitionSession) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        this@VoiceRecognitionManager.stopListening()
                        if (text.isNotBlank()) {
                            _voiceState.value = VoiceState.Success(text)
                        } else {
                            _voiceState.value = VoiceState.Error("Tidak ada suara yang terdeteksi. Coba bicara lagi.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            _voiceState.value = VoiceState.Listening
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            stopListening()
            _voiceState.value = VoiceState.Error("Gagal memulai perekam suara: ${e.localizedMessage}")
        }
    }

    fun finishListening() {
        if (_voiceState.value != VoiceState.Listening) return
        _voiceState.value = VoiceState.Processing
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
            stopListening()
            _voiceState.value = VoiceState.Error("Gagal menyelesaikan rekaman. Coba rekam kembali.")
        }
    }

    fun stopListening() {
        recognitionSession++ // Ignore late callbacks from a cancelled or replaced session.
        val recognizer = speechRecognizer
        speechRecognizer = null
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
            // Continue releasing the recognizer even if cancellation fails.
        }
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // ignore cleanup errors
        } finally {
            _audioRms.value = 0f
            _voiceState.value = VoiceState.Idle
        }
    }

    fun simulateVoiceInput(text: String) {
        stopListening()
        _voiceState.value = VoiceState.Processing
        _voiceState.value = VoiceState.Success(text)
    }

    fun reset() {
        stopListening()
        _voiceState.value = VoiceState.Idle
        _audioRms.value = 0f
    }
}

internal fun recognitionFailure(error: Int, onlineAllowed: Boolean = false): VoiceState.Error = VoiceState.Error(
    message = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> if (onlineAllowed) "Model bahasa Indonesia belum siap di layanan suara Android. Siapkan bahasa Indonesia melalui pengaturan layanan suara HP, lalu coba lagi." else "Model bahasa Indonesia offline belum siap di HP ini. Siapkan bahasa melalui layanan suara HP, atau gunakan layanan suara Android."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Pengenal suara ini belum mendukung bahasa Indonesia. Gunakan layanan suara Android atau catat manual."
        SpeechRecognizer.ERROR_NO_MATCH -> "Ucapan belum dikenali. Coba bicara lagi."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada ucapan yang terdengar. Tekan Coba lagi untuk merekam."
        SpeechRecognizer.ERROR_AUDIO -> "Mikrofon tidak dapat digunakan. Periksa akses mikrofon HP dan tutup aplikasi perekam lain."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Koneksi internet layanan suara bermasalah. Coba lagi atau catat manual."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diberikan. Izinkan melalui Pengaturan Android atau catat manual."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Layanan suara sedang digunakan. Tunggu sebentar lalu coba lagi."
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Layanan suara Android sedang bermasalah. Coba lagi atau catat manual."
        else -> "Pengenalan suara gagal (kode $error). Coba lagi atau catat manual."
    },
    canUseOnline = !onlineAllowed && error in setOf(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
)
