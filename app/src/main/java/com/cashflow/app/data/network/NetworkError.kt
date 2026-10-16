package com.cashflow.app.data.network

sealed class NetworkError(open val userMessage: String) : Exception(userMessage) {
    object Offline : NetworkError("Perangkat sedang offline. Periksa koneksi internet Anda.")
    data class Timeout(val timeoutMs: Long) : NetworkError("Koneksi internet terputus (waktu habis $timeoutMs ms).")
    object Unauthorized : NetworkError("Autentikasi gagal (401). Kunci akses atau token tidak valid.")
    object RateLimited : NetworkError("Batas permintaan tercapai (429). Coba lagi beberapa saat lagi.")
    data class ServerError(val code: Int) : NetworkError("Server sedang bermasalah ($code). Coba lagi nanti.")
    data class ClientError(val code: Int, override val userMessage: String) : NetworkError(userMessage)
    data class Unknown(override val message: String) : NetworkError(message)
}
