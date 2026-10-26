package com.cashflow.app

import com.cashflow.app.data.update.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ReleaseHistoryTest {
    private fun release(tag: String, date: String, preview: Boolean = false) = JSONObject()
        .put("tag_name", tag).put("published_at", date).put("body", "- Memperbaiki pencatatan transaksi.")
        .put("prerelease", preview).put("draft", false)

    @Test fun historyIncludesOlderBetaAndSortsByPublishedDateRatherThanVersionName() {
        val latest = release("v1.0.0-build.132", "2026-10-23T12:34:08Z")
        val beta = release("v1.1.0-beta.1", "2026-10-19T11:22:45Z", true)
        val rows = parseReleaseHistory(JSONArray().put(beta).put(latest).put(latest).toString())
        assertEquals(2, rows.size)
        assertEquals(132L, rows.first().versionCode)
        assertEquals("1.1.0-beta.1", rows.last().versionName)
        assertTrue(rows.last().preview)
        assertNull(rows.last().versionCode)
    }

    @Test fun unpublishedInstalledNotesRemainAvailableAndPublishedCopiesDoNotDuplicateThem() {
        val installed = ReleaseHistoryEntry("v1.0.0-build.142", "1.0.0", 142, null, "- Riwayat pembaruan.")
        val old = parseReleaseHistory(JSONArray().put(release("v1.0.0-build.132", "2026-10-23T12:34:08Z")).toString())
        assertNull(mergeReleaseHistory(installed, old).first().publishedAt)
        val published = parseReleaseHistory(JSONArray().put(release(installed.tag, "2026-10-25T12:00:00Z")).toString())
        val merged = mergeReleaseHistory(installed, published + old + old)
        assertEquals(2, merged.size)
        assertEquals(installed.notes, merged.first().notes)
        assertEquals(Instant.parse("2026-10-25T12:00:00Z"), merged.first().publishedAt)
    }

    @Test fun draftsInvalidVersionsDatesAndOversizedBodiesDoNotBecomeHistoryEntries() {
        val rows = JSONArray()
            .put(release("v1.0.0-build.132", "2026-10-23T12:34:08Z").put("draft", true))
            .put(release("v999", "2026-10-23T12:34:08Z"))
            .put(release("v1.0.0-build.117", "invalid"))
            .put(release("v1.0.0-build.116", "2026-10-23T12:34:08Z").put("body", "x".repeat(12001)))
        assertTrue(parseReleaseHistory(rows.toString()).isEmpty())
        assertThrows(Exception::class.java) { parseReleaseHistory("{}") }
        assertThrows(Exception::class.java) { parseReleaseHistory("x".repeat(HISTORY_LIMIT + 1)) }
    }
}
