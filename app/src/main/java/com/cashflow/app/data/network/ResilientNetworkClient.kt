package com.cashflow.app.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

object ResilientNetworkClient {

    /**
     * Menjalankan operasi jaringan dengan:
     * 1. Per-attempt timeout
     * 2. Exponential backoff + random jitter
     * 3. Klasifikasi error yang ramah pengguna (NetworkError)
     * 4. Dukungan pembatalan coroutine tanpa retry tak perlu
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        baseDelayMs: Long = 400L,
        timeoutMs: Long = 10_000L,
        onRetry: ((attempt: Int, error: Throwable, nextDelayMs: Long) -> Unit)? = null,
        block: suspend () -> T
    ): Result<T> {
        var currentDelay = baseDelayMs

        for (attempt in 1..maxRetries) {
            try {
                val result = withTimeout(timeoutMs) {
                    block()
                }
                return Result.success(result)
            } catch (e: Exception) {
                // Jangan telan cancellation exception bila pengguna membatalkan dialog
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }

                val networkError = classifyError(e, timeoutMs)

                // Jika error permanen (misal 401 Unauthorized atau 400 Client Error selain rate limit), fail-fast
                if (networkError is NetworkError.Unauthorized ||
                    (networkError is NetworkError.ClientError && networkError.code != 429)
                ) {
                    return Result.failure(networkError)
                }

                if (attempt == maxRetries) {
                    return Result.failure(networkError)
                }

                // Exponential backoff dengan jitter: delay = base * 2^(attempt-1) + jitter(0..25%)
                val jitter = Random.nextLong(0, (currentDelay * 0.25).toLong().coerceAtLeast(20L))
                val sleepTime = currentDelay + jitter
                onRetry?.invoke(attempt, networkError, sleepTime)
                delay(sleepTime)
                currentDelay *= 2
            }
        }
        return Result.failure(NetworkError.Offline)
    }

    fun classifyError(e: Throwable, timeoutMs: Long = 10_000L): NetworkError {
        return when (e) {
            is NetworkError -> e
            is UnknownHostException, is ConnectException -> NetworkError.Offline
            is SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> NetworkError.Timeout(timeoutMs)
            is IOException -> {
                val msg = e.message ?: ""
                when {
                    msg.contains("401") -> NetworkError.Unauthorized
                    msg.contains("429") -> NetworkError.RateLimited
                    msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") -> NetworkError.ServerError(500)
                    msg.contains("ENETUNREACH") || msg.contains("network unreachable") -> NetworkError.Offline
                    else -> NetworkError.Unknown(msg.ifBlank { "Gangguan koneksi jaringan" })
                }
            }
            else -> NetworkError.Unknown(e.message ?: "Terjadi kesalahan tidak terduga")
        }
    }
}
