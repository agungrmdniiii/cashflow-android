package com.cashflow.app

import com.cashflow.app.data.backup.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class BackupAutomationTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone)

    @Test fun firstBackupUsesTheCurrentWindowOrTheNextOne() {
        val settings = BackupAutomation(enabled = true)
        assertEquals(at("2026-10-25T02:00"), settings.nextWindow(at("2026-10-25T01:00"), null)?.start)
        assertEquals(at("2026-10-25T06:00"), settings.nextWindow(at("2026-10-25T03:00"), null)?.end)
        assertEquals(at("2026-10-26T02:00"), settings.nextWindow(at("2026-10-25T06:00"), null)?.start)
        assertNull(settings.copy(enabled = false).nextWindow(at("2026-10-25T03:00"), null))
    }

    @Test fun successfulDailyAndWeeklyBackupsAreNotRepeatedInTheSamePeriod() {
        val settings = BackupAutomation(enabled = true)
        val last = at("2026-10-25T03:00").toInstant()
        assertEquals(at("2026-10-26T02:00"), settings.nextWindow(at("2026-10-25T04:00"), last)?.start)
        assertEquals(at("2026-11-01T02:00"), settings.copy(frequency = BackupFrequency.WEEKLY).nextWindow(at("2026-10-25T04:00"), last)?.start)
    }

    @Test fun aMissedWindowWaitsForTheNextWindowWithoutSkippingTheWholeWeek() {
        val settings = BackupAutomation(enabled = true, frequency = BackupFrequency.WEEKLY)
        assertEquals(at("2026-10-26T02:00"), settings.nextWindow(at("2026-10-25T08:00"), at("2026-10-01T03:00").toInstant())?.start)
    }

    @Test fun overnightWindowsUseTheStartingDayForTheNextRun() {
        val settings = BackupAutomation(enabled = true, startHour = 22, endHour = 6)
        val first = settings.nextWindow(at("2026-10-25T01:00"), null)!!
        assertEquals(at("2026-10-24T22:00"), first.start)
        assertEquals(at("2026-10-25T06:00"), first.end)
        assertEquals(at("2026-10-25T22:00"), settings.nextWindow(at("2026-10-25T02:00"), at("2026-10-25T01:00").toInstant())?.start)
    }

    @Test fun monthlyBackupsKeepTheirChosenDayAfterAShortMonth() {
        val settings = BackupAutomation(enabled = true, frequency = BackupFrequency.MONTHLY, monthDay = 31)
        assertEquals(at("2027-02-28T02:00"), settings.nextWindow(at("2027-02-01T01:00"), at("2027-01-31T03:00").toInstant())?.start)
        assertEquals(at("2027-03-31T02:00"), settings.nextWindow(at("2027-03-01T01:00"), at("2027-02-28T03:00").toInstant())?.start)
    }

    @Test fun invalidWindowsAreRejected() {
        for (hours in listOf(-1 to 6, 2 to 24, 2 to 2)) {
            try { BackupAutomation(startHour = hours.first, endHour = hours.second); fail(hours.toString()) }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun wifiPolicyNeverAllowsCellularOnlyOrAnUnvalidatedConnection() {
        assertTrue(BackupNetwork.WIFI.allows(validated = true, wifi = true, cellular = false, metered = false))
        assertFalse(BackupNetwork.WIFI.allows(validated = true, wifi = false, cellular = true, metered = false))
        assertFalse(BackupNetwork.WIFI.allows(validated = true, wifi = true, cellular = true, metered = false))
        assertFalse(BackupNetwork.WIFI.allows(validated = true, wifi = true, cellular = false, metered = true))
        assertFalse(BackupNetwork.ANY.allows(validated = false, wifi = true, cellular = false, metered = false))
        assertTrue(BackupNetwork.ANY.allows(validated = true, wifi = false, cellular = true, metered = true))
    }
}
