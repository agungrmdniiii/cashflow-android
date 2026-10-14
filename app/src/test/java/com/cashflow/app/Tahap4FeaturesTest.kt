package com.cashflow.app

import com.cashflow.app.data.service.CloudBackupService
import com.cashflow.app.data.model.RecurringFrequency
import com.cashflow.app.data.model.RecurringTransaction
import com.cashflow.app.data.service.FinancialInsightService
import com.cashflow.app.data.service.RecurringSchedulerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Tahap4FeaturesTest {

    @Test
    fun testClampDayOfMonth_ShortMonths() {
        // Non-leap year February (2026 has 28 days)
        val febNonLeap = RecurringSchedulerService.clampDayOfMonth(2026, 2, 31)
        assertEquals(LocalDate.of(2026, 2, 28), febNonLeap)

        // Leap year February (2024 has 29 days)
        val febLeap = RecurringSchedulerService.clampDayOfMonth(2024, 2, 31)
        assertEquals(LocalDate.of(2024, 2, 29), febLeap)

        // 30-day month (April)
        val april = RecurringSchedulerService.clampDayOfMonth(2026, 4, 31)
        assertEquals(LocalDate.of(2026, 4, 30), april)

        // Normal 31-day month (January)
        val jan = RecurringSchedulerService.clampDayOfMonth(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 1, 31), jan)
    }

    @Test
    fun testGenerateScheduledDates_MonthlyDay31() {
        val rec = RecurringTransaction(
            name = "Langganan Layanan",
            type = "expense",
            amount = 150000,
            categoryId = "cat_sub",
            assetId = "asset_bca",
            frequency = RecurringFrequency.MONTHLY.value,
            interval = 1,
            startDate = "2026-01-31"
        )

        val scheduled = RecurringSchedulerService.generateScheduledDates(
            recurring = rec,
            upToDate = LocalDate.of(2026, 4, 30)
        )

        assertEquals(4, scheduled.size)
        assertEquals(LocalDate.of(2026, 1, 31), scheduled[0])
        assertEquals(LocalDate.of(2026, 2, 28), scheduled[1]) // Clamped to 28
        assertEquals(LocalDate.of(2026, 3, 31), scheduled[2])
        assertEquals(LocalDate.of(2026, 4, 30), scheduled[3]) // Clamped to 30
    }

    @Test
    fun testMonthOverMonthComparison() {
        val currentMonth = 2500000L
        val lastMonth = 2000000L

        val diff = currentMonth - lastMonth
        val pct = ((diff.toDouble() / lastMonth) * 100.0)

        assertEquals(500000L, diff)
        assertEquals(25.0, pct, 0.01)
    }

    @Test
    fun testSavingsRateCalculation() {
        val income = 10000000L
        val expense = 6500000L
        val savings = income - expense
        val savingsRate = (savings.toDouble() / income.toDouble()) * 100.0

        assertEquals(35.0, savingsRate, 0.01)
    }

    @Test
    fun testAES256GCM_EncryptionDecryptionRoundtrip() {
        val plainText = """{"appName":"DuitAing","version":7,"assets":[{"name":"BCA","balance":5000000}]}"""
        val password = "SuperSecretPassword123!"

        val encryptedBytes = CloudBackupService.encryptWithPassword(plainText.toByteArray(Charsets.UTF_8), password)
        assertNotNull(encryptedBytes)
        assertTrue(encryptedBytes.isNotEmpty())

        val decryptedBytes = CloudBackupService.decryptWithPassword(encryptedBytes, password)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(plainText, decryptedText)
    }

    @Test
    fun testAES256GCM_WrongPasswordFails() {
        val plainText = """{"test":true}"""
        val password = "correctPassword"
        val encryptedBytes = CloudBackupService.encryptWithPassword(plainText.toByteArray(Charsets.UTF_8), password)

        var failed = false
        try {
            CloudBackupService.decryptWithPassword(encryptedBytes, "wrongPassword")
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Decryption with wrong password must throw an exception", failed)
    }
}
