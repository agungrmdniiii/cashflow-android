package com.cashflow.app

import com.cashflow.app.data.network.NetworkError
import com.cashflow.app.data.network.ResilientNetworkClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException

class ResilientNetworkTest {

    private class WaitingConnection : HttpURLConnection(URL("http://localhost")) {
        val released = CountDownLatch(1)
        var disconnected = false
        override fun disconnect() { disconnected = true; released.countDown() }
        override fun connect() {}
        override fun usingProxy() = false
    }

    @Test fun timeoutDisconnectsABlockedRequestBeforeReturning() = runBlocking {
        val connection = WaitingConnection()
        val started = System.nanoTime()
        try {
            ResilientNetworkClient.withConnectionTimeout(connection, 50) {
                connection.released.await(3, TimeUnit.SECONDS)
                "late response"
            }
            fail("Expected timeout")
        } catch (_: TimeoutCancellationException) { }
        assertTrue(connection.disconnected)
        assertTrue("A blocked request must not outlive the deadline", (System.nanoTime() - started) / 1_000_000 < 1500)
    }

    @Test fun callerCancellationDisconnectsRequestAndDoesNotReturnAnAnswer() = runBlocking {
        val connection = WaitingConnection()
        val result = withTimeoutOrNull(50) {
            ResilientNetworkClient.withConnectionTimeout(connection, 5000) {
                connection.released.await(3, TimeUnit.SECONDS)
                "late response"
            }
        }
        assertNull(result)
        assertTrue(connection.disconnected)
    }

    @Test
    fun testClassifyError_AccurateMapping() {
        assertTrue(ResilientNetworkClient.classifyError(UnknownHostException("No address")) is NetworkError.Offline)
        assertTrue(ResilientNetworkClient.classifyError(ConnectException("Connection refused")) is NetworkError.Offline)
        assertTrue(ResilientNetworkClient.classifyError(SocketTimeoutException("Read timed out")) is NetworkError.Timeout)
        assertTrue(ResilientNetworkClient.classifyError(IOException("HTTP 401 Unauthorized")) is NetworkError.Unauthorized)
        assertTrue(ResilientNetworkClient.classifyError(IOException("HTTP 429 Too Many Requests")) is NetworkError.RateLimited)
        assertTrue(ResilientNetworkClient.classifyError(IOException("HTTP 500 Internal Server Error")) is NetworkError.ServerError)
    }

    @Test
    fun testExecuteWithRetry_SuccessFirstAttempt() = runBlocking {
        var attempts = 0
        val res = ResilientNetworkClient.executeWithRetry<String>(maxRetries = 3) {
            attempts++
            "OK_RESPONSE"
        }

        assertTrue(res.isSuccess)
        assertEquals("OK_RESPONSE", res.getOrNull())
        assertEquals(1, attempts)
    }

    @Test
    fun testExecuteWithRetry_TransientFailureThenSuccess() = runBlocking {
        var attempts = 0
        val retriesLogged = mutableListOf<Int>()

        val res = ResilientNetworkClient.executeWithRetry<String>(
            maxRetries = 3,
            baseDelayMs = 20L, // Fast for unit tests
            onRetry = { attempt, _, _ -> retriesLogged.add(attempt) }
        ) {
            attempts++
            if (attempts < 2) {
                throw IOException("Temporary glitch 503")
            }
            "RECOVERED_DATA"
        }

        assertTrue(res.isSuccess)
        assertEquals("RECOVERED_DATA", res.getOrNull())
        assertEquals(2, attempts)
        assertEquals(listOf(1), retriesLogged)
    }

    @Test
    fun testExecuteWithRetry_FastFailOn401() = runBlocking {
        var attempts = 0
        val res = ResilientNetworkClient.executeWithRetry<String>(
            maxRetries = 3,
            baseDelayMs = 20L
        ) {
            attempts++
            throw IOException("HTTP 401 Unauthorized")
        }

        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is NetworkError.Unauthorized)
        assertEquals("401 must fail-fast without redundant retries", 1, attempts)
    }

    @Test
    fun testExecuteWithRetry_ExhaustionReturnsClassifiedError() = runBlocking {
        var attempts = 0
        val res = ResilientNetworkClient.executeWithRetry<String>(
            maxRetries = 3,
            baseDelayMs = 20L
        ) {
            attempts++
            throw UnknownHostException("dns lookup failed")
        }

        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is NetworkError.Offline)
        assertEquals(3, attempts)
    }
}
