package com.cashflow.app.data.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashflow.app.BuildConfig
import com.cashflow.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

data class ReleaseHistoryEntry(
    val tag: String,
    val versionName: String,
    val versionCode: Long?,
    val publishedAt: Instant?,
    val notes: String,
    val preview: Boolean = false
) {
    val title: String get() = "Versi $versionName" + (versionCode?.let { " · Build $it" } ?: "")
}

internal const val HISTORY_LIMIT = 1024 * 1024

internal fun parseReleaseHistory(text: String): List<ReleaseHistoryEntry> {
    require(text.length <= HISTORY_LIMIT) { "Riwayat pembaruan terlalu besar." }
    val rows = JSONArray(text)
    require(rows.length() <= 100) { "Terlalu banyak rilis dalam satu halaman." }
    return (0 until rows.length()).mapNotNull { index ->
        runCatching {
            val row = rows.getJSONObject(index)
            if (row.optBoolean("draft", false)) return@runCatching null
            val tag = row.getString("tag_name")
            val match = Regex("v(\\d+\\.\\d+\\.\\d+)(?:-build\\.(\\d+)|(-[A-Za-z0-9.-]+))?").matchEntire(tag) ?: return@runCatching null
            val code = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLong()?.also { require(it in 1..Int.MAX_VALUE.toLong()) }
            val name = match.groupValues[1] + match.groupValues[3]
            require(name.length <= 64)
            val date = Instant.parse(row.getString("published_at"))
            val notes = row.optString("body", "").trim().ifBlank { "Catatan perubahan belum tersedia untuk rilis ini." }
            require(notes.length <= 12000)
            ReleaseHistoryEntry(tag, name, code, date, notes, row.optBoolean("prerelease", false))
        }.getOrNull()
    }.distinctBy { it.tag }.sortedByDescending { it.publishedAt }
}

internal fun mergeReleaseHistory(installed: ReleaseHistoryEntry, published: List<ReleaseHistoryEntry>): List<ReleaseHistoryEntry> =
    listOf(installed.copy(publishedAt = published.firstOrNull { it.tag == installed.tag }?.publishedAt)) +
        published.distinctBy { it.tag }.filter { it.tag != installed.tag }.sortedByDescending { it.publishedAt }

data class ReleaseHistoryStatus(
    val entries: List<ReleaseHistoryEntry> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

class ReleaseHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ReleaseHistoryStatus())
    val status = mutable.asStateFlow()
    private var published = emptyList<ReleaseHistoryEntry>()
    private var installed: ReleaseHistoryEntry? = null
    private var nextPage = 1
    private val cache = File(application.filesDir, "release-history.json")

    init { refresh() }
    fun refresh() = load(1)
    fun loadMore() = load(nextPage)

    private fun load(page: Int) {
        if (mutable.value.loading) return
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (installed == null) {
                    val resources = getApplication<Application>().resources
                    installed = ReleaseHistoryEntry("v${BuildConfig.VERSION_NAME}-build.${BuildConfig.VERSION_CODE}",
                        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), null,
                        resources.openRawResource(R.raw.release_notes).bufferedReader().use { it.readText() })
                    val bundled = resources.openRawResource(R.raw.release_history).bufferedReader().use { parseReleaseHistory(it.readText()) }
                    val saved = runCatching { if (cache.isFile && cache.length() <= HISTORY_LIMIT) parseReleaseHistory(cache.readText()) else emptyList() }.getOrDefault(emptyList())
                    published = (saved + bundled).distinctBy { it.tag }
                    mutable.value = mutable.value.copy(entries = mergeReleaseHistory(installed!!, published))
                }
                val connection = openGitHubConnection("https://api.github.com/repos/agungrmdniiii/cashflow-android/releases?per_page=30&page=$page")
                val (text, more) = try {
                    val bytes = connection.inputStream.use { input ->
                        val buffer = ByteArray(HISTORY_LIMIT + 1)
                        var count = 0
                        while (count < buffer.size) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read == -1) break
                            count += read
                        }
                        buffer.copyOf(count)
                    }
                    coroutineContext.ensureActive()
                    require(bytes.size <= HISTORY_LIMIT) { "Riwayat pembaruan terlalu besar." }
                    bytes.toString(Charsets.UTF_8) to (connection.getHeaderField("Link")?.contains("rel=\"next\"") == true)
                } finally { connection.disconnect() }
                val incoming = parseReleaseHistory(text)
                published = (incoming + published).distinctBy { it.tag }.sortedByDescending { it.publishedAt }
                nextPage = page + 1
                // ponytail: keep the latest100 notes offline; older pages remain available online.
                val saved = JSONArray().apply { published.take(100).forEach { entry -> put(JSONObject().apply {
                    put("tag_name", entry.tag); put("published_at", entry.publishedAt.toString())
                    put("body", entry.notes); put("prerelease", entry.preview)
                }) } }.toString()
                if (saved.toByteArray(Charsets.UTF_8).size <= HISTORY_LIMIT) runCatching { cache.writeText(saved) }
                mutable.value = ReleaseHistoryStatus(mergeReleaseHistory(installed!!, published), hasMore = more)
            } catch (failure: CancellationException) { throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false,
                    error = "Riwayat belum dapat diperbarui. Catatan tersimpan tetap dapat dibaca. Periksa koneksi lalu coba lagi.")
            }
        }
    }
}
