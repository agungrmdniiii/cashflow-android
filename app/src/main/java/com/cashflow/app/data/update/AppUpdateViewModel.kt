package com.cashflow.app.data.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashflow.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, PAUSED, VERIFYING, READY, ERROR }
data class UpdateStatus(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: UpdateRelease? = null,
    val progress: Int = 0,
    val apk: File? = null,
    val message: String = ""
)

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _status = MutableStateFlow(UpdateStatus(UpdatePhase.CHECKING))
    val status = _status.asStateFlow()
    private val preferences = application.getSharedPreferences("pending_app_update", Context.MODE_PRIVATE)
    private val downloads = application.getSystemService(android.app.DownloadManager::class.java)
    private var job: Job? = null

    init {
        operation {
            val raw = preferences.getString("release", null)
            if (raw == null) { _status.value = UpdateStatus(); return@operation }
            try {
                val release = UpdateRelease.parse(raw)
                if (!release.isNewerThan(BuildConfig.VERSION_CODE.toLong()) || !release.supportsAndroid(Build.VERSION.SDK_INT)) {
                    discardDownload()
                    updateApkFile(application, release).delete()
                    preferences.edit().clear().commit()
                    _status.value = UpdateStatus()
                } else recover(release)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _status.value = UpdateStatus(UpdatePhase.ERROR, message = "Status unduhan tidak dapat dipulihkan. Periksa pembaruan kembali.")
            }
        }
    }

    private fun operation(block: suspend () -> Unit) {
        val previous = job
        previous?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) { previous?.join(); block() }
    }

    fun check() {
        if (_status.value.phase in listOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.PAUSED, UpdatePhase.VERIFYING, UpdatePhase.READY)) return
        _status.value = UpdateStatus(UpdatePhase.CHECKING)
        operation {
            try {
                val connection = openGitHubConnection(UpdateRelease.FEED_URL)
                val text = try {
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(65537)
                        var size = 0
                        while (size < buffer.size) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer, size, buffer.size - size)
                            if (count == -1) break
                            size += count
                        }
                        require(size <= 65536) { "Informasi pembaruan terlalu besar." }
                        String(buffer, 0, size, Charsets.UTF_8)
                    }
                } finally { connection.disconnect() }
                val release = UpdateRelease.parse(text)
                currentCoroutineContext().ensureActive()
                when {
                    !release.isNewerThan(BuildConfig.VERSION_CODE.toLong()) -> {
                        discardDownload()
                        preferences.edit().clear().commit()
                        _status.value = UpdateStatus(UpdatePhase.CURRENT, message = "Anda menggunakan versi terbaru.")
                    }
                    !release.supportsAndroid(Build.VERSION.SDK_INT) -> _status.value = UpdateStatus(UpdatePhase.ERROR, message = "Pembaruan ini memerlukan Android API ${release.minSdk} atau lebih baru.")
                    else -> {
                        val old = preferences.getString("release", null)?.let { runCatching { UpdateRelease.parse(it) }.getOrNull() }
                        if (old != release) {
                            discardDownload()
                            preferences.edit().putString("release", release.toJson()).commit()
                        }
                        recover(release)
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (_: FileNotFoundException) {
                currentCoroutineContext().ensureActive()
                _status.value = UpdateStatus(UpdatePhase.ERROR, message = "Belum ada paket pembaruan yang diterbitkan. Coba periksa kembali nanti.")
            } catch (e: IllegalArgumentException) {
                currentCoroutineContext().ensureActive()
                _status.value = UpdateStatus(UpdatePhase.ERROR, message = e.message ?: "Informasi pembaruan tidak valid.")
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _status.value = UpdateStatus(UpdatePhase.ERROR, message = "Pembaruan belum dapat diperiksa. Periksa koneksi internet, lalu coba lagi.")
            }
        }
    }

    private suspend fun recover(release: UpdateRelease) {
        val application = getApplication<Application>()
        val apk = updateApkFile(application, release)
        if (apk.isFile) {
            _status.value = UpdateStatus(UpdatePhase.VERIFYING, release, message = "Memeriksa paket yang tersimpan...")
            try {
                verifyDownloadedFile(apk, release)
                verifyUpdateApk(application, apk, release)
                currentCoroutineContext().ensureActive()
                discardDownload()
                _status.value = UpdateStatus(UpdatePhase.READY, release, 100, apk)
                return
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { apk.delete() }
        }
        val id = preferences.getLong("download_id", -1)
        if (id >= 0) monitor(release, id)
        else _status.value = UpdateStatus(UpdatePhase.AVAILABLE, release)
    }

    fun download() {
        val release = _status.value.release ?: return
        if (_status.value.phase !in listOf(UpdatePhase.AVAILABLE, UpdatePhase.ERROR, UpdatePhase.READY)) return
        _status.value = UpdateStatus(UpdatePhase.DOWNLOADING, release)
        operation {
            try {
                discardDownload()
                val destination = File(getApplication<Application>().getExternalFilesDir("updates") ?: error("Penyimpanan unduhan tidak tersedia"), "DuitAing-${release.versionCode}.apk")
                destination.delete()
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(release.apkUrl))
                    .setTitle("Pembaruan Duit Aing ${release.versionName}")
                    .setDescription("Android akan melanjutkan unduhan saat koneksi tersedia.")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(getApplication(), "updates", destination.name)
                val id = downloads.enqueue(request)
                if (!preferences.edit().putString("release", release.toJson()).putLong("download_id", id).commit()) {
                    downloads.remove(id)
                    error("Status unduhan tidak dapat disimpan.")
                }
                monitor(release, id)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _status.value = UpdateStatus(UpdatePhase.ERROR, release, message = "Unduhan belum dapat dimulai. Periksa koneksi, ruang penyimpanan, dan apakah Pengelola Unduhan Android aktif.")
            }
        }
    }

    private suspend fun monitor(release: UpdateRelease, id: Long) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val state = downloads.query(android.app.DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) null else Triple(
                    cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)))
            }
            currentCoroutineContext().ensureActive()
            if (state == null) {
                preferences.edit().remove("download_id").commit()
                _status.value = UpdateStatus(UpdatePhase.ERROR, release, message = "Unduhan sudah tidak tersedia di Android. Tekan Unduh ulang untuk memulai kembali.")
                return
            }
            val progress = (state.third.coerceIn(0, release.size) * 100 / release.size).toInt()
            if (state.third > release.size) {
                discardDownload()
                _status.value = UpdateStatus(UpdatePhase.ERROR, release, message = "Ukuran unduhan melebihi paket yang diterbitkan. Unduh ulang pembaruan.")
                return
            }
            when (state.first) {
                android.app.DownloadManager.STATUS_PENDING -> _status.value = UpdateStatus(UpdatePhase.PAUSED, release, progress,
                    message = "Menunggu Android memulai unduhan. Proses berlanjut saat jaringan dan sistem siap.")
                android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                    _status.value = UpdateStatus(UpdatePhase.VERIFYING, release, 100, message = "Memeriksa keutuhan dan tanda tangan APK...")
                    try {
                        val app = getApplication<Application>()
                        val source = File(requireNotNull(app.getExternalFilesDir("updates")) { "Penyimpanan unduhan tidak tersedia." }, "DuitAing-${release.versionCode}.apk")
                        val apk = prepareDownloadedUpdate(app, source, release)
                        currentCoroutineContext().ensureActive()
                        discardDownload()
                        _status.value = UpdateStatus(UpdatePhase.READY, release, 100, apk)
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        discardDownload()
                        _status.value = UpdateStatus(UpdatePhase.ERROR, release, message = e.message ?: "Paket tidak dapat diverifikasi. Unduh ulang pembaruan.")
                    }
                    return
                }
                android.app.DownloadManager.STATUS_FAILED -> {
                    _status.value = UpdateStatus(UpdatePhase.ERROR, release, progress, message = downloadFailureMessage(state.second))
                    return
                }
                android.app.DownloadManager.STATUS_PAUSED -> _status.value = UpdateStatus(UpdatePhase.PAUSED, release, progress,
                    message = pausedDownloadMessage(state.second))
                else -> _status.value = UpdateStatus(UpdatePhase.DOWNLOADING, release, progress)
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    fun cancelDownload() {
        val release = _status.value.release ?: return
        if (_status.value.phase !in listOf(UpdatePhase.DOWNLOADING, UpdatePhase.PAUSED)) return
        operation {
            discardDownload()
            _status.value = UpdateStatus(UpdatePhase.AVAILABLE, release, message = "Unduhan dibatalkan. Unduhan berikutnya dimulai dari awal.")
        }
    }

    private fun discardDownload() {
        val id = preferences.getLong("download_id", -1)
        if (id >= 0) downloads.remove(id)
        preferences.edit().remove("download_id").commit()
    }

    suspend fun verifiedInstallFile(): File = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val snapshot = _status.value
        require(snapshot.phase == UpdatePhase.READY) { "Paket pembaruan belum siap dipasang." }
        val release = requireNotNull(snapshot.release)
        val apk = requireNotNull(snapshot.apk)
        try {
            verifyDownloadedFile(apk, release)
            verifyUpdateApk(getApplication(), apk, release)
            apk
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            apk.delete()
            _status.value = UpdateStatus(UpdatePhase.ERROR, release, message = "Paket tersimpan tidak dapat diverifikasi. Unduh ulang pembaruan.")
            throw e
        }
    }
}

internal fun updateApkFile(context: Context, release: UpdateRelease): File =
    File(File(context.filesDir, "updates").apply { mkdirs() }, "DuitAing-${release.versionCode}.apk")

internal suspend fun prepareDownloadedUpdate(context: Context, source: File, release: UpdateRelease): File {
    val apk = updateApkFile(context, release)
    val partial = File(apk.parentFile, "${apk.name}.part")
    try {
        source.inputStream().use { copyVerifiedDownload(it, partial, release) {} }
        verifyUpdateApk(context, partial, release)
        currentCoroutineContext().ensureActive()
        require(partial.renameTo(apk)) { "Paket tidak dapat disimpan. Periksa ruang penyimpanan, lalu coba lagi." }
        return apk
    } finally { partial.delete() }
}

internal suspend fun verifyDownloadedFile(file: File, release: UpdateRelease) {
    require(file.isFile && file.length() == release.size) { "Paket tersimpan tidak lengkap. Unduh ulang pembaruan." }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = input.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
    }
    require(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "Keutuhan paket tidak sesuai. Unduh ulang pembaruan." }
}

internal fun downloadFailureMessage(reason: Int): String = when (reason) {
    android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Ruang penyimpanan tidak cukup. Kosongkan ruang, lalu tekan Unduh ulang."
    android.app.DownloadManager.ERROR_CANNOT_RESUME -> "Server tidak dapat melanjutkan unduhan ini. Tekan Unduh ulang untuk memulai kembali."
    android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Penyimpanan unduhan tidak tersedia. Periksa penyimpanan perangkat, lalu coba lagi."
    else -> "Unduhan gagal di Android. Periksa koneksi, lalu tekan Unduh ulang."
}

internal fun pausedDownloadMessage(reason: Int): String = when (reason) {
    android.app.DownloadManager.PAUSED_WAITING_TO_RETRY -> "Koneksi atau server terputus. Kemajuan tersimpan dan Android akan mencoba kembali secara otomatis."
    android.app.DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Unduhan tersimpan. Sambungkan ke Wi-Fi agar Android dapat melanjutkannya."
    else -> "Unduhan tersimpan. Android akan melanjutkannya saat koneksi tersedia atau pembatasan jaringan berakhir."
}

internal fun openGitHubConnection(address: String): HttpURLConnection {
    var url = URL(address)
    repeat(6) {
        val host = url.host.lowercase()
        require(url.protocol == "https" && url.userInfo == null && url.port in listOf(-1, 443) && (host == "github.com" || host.endsWith(".githubusercontent.com"))) { "Sumber pembaruan tidak dipercaya." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", "DuitAing/${BuildConfig.VERSION_NAME}")
        }
        try {
            when (connection.responseCode) {
                200 -> return connection
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location") ?: error("Alamat unduhan tidak tersedia")
                    url = URL(url, location)
                    connection.disconnect()
                }
                404 -> { connection.disconnect(); throw FileNotFoundException() }
                else -> { connection.disconnect(); error("Server pembaruan belum tersedia") }
            }
        } catch (e: Exception) { connection.disconnect(); throw e }
    }
    error("Terlalu banyak pengalihan unduhan")
}

internal suspend fun copyVerifiedDownload(input: InputStream, file: File, release: UpdateRelease, onProgress: (Int) -> Unit) {
    val digest = MessageDigest.getInstance("SHA-256")
    var received = 0L
    var lastProgress = -1
    try {
        file.outputStream().use { output ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                currentCoroutineContext().ensureActive()
                received += count
                require(received <= release.size) { "Ukuran unduhan melebihi paket yang diterbitkan." }
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                val progress = (received * 100 / release.size).toInt()
                if (progress != lastProgress) { onProgress(progress); lastProgress = progress }
            }
        }
        require(received == release.size) { "Unduhan belum lengkap. Coba unduh kembali." }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        require(hash == release.sha256) { "Berkas pembaruan tidak sesuai. Coba unduh kembali." }
    } catch (e: Exception) { file.delete(); throw e }
}

@Suppress("DEPRECATION")
internal fun verifyUpdateApk(context: Context, file: File, release: UpdateRelease) {
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val installed = context.packageManager.getPackageInfo(context.packageName, flags)
    val candidate = context.packageManager.getPackageArchiveInfo(file.path, flags)
    require(candidate != null && candidate.packageName == context.packageName) { "Paket ini bukan pembaruan Duit Aing." }
    val code = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
    require(code == release.versionCode && candidate.versionName == release.versionName) { "Versi paket pembaruan tidak sesuai." }
    fun signatures(info: android.content.pm.PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            ?.map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }
            ?.toSet().orEmpty()
    val trusted = signatures(installed)
    require(trusted.isNotEmpty() && trusted == signatures(candidate)) { "Tanda tangan pembaruan tidak cocok dengan aplikasi yang terpasang." }
}

internal fun updateInstallIntent(context: Context, apk: File): Intent {
    val parent = apk.canonicalFile.parentFile
    require(apk.isFile && (parent == File(context.cacheDir, "updates").canonicalFile || parent == File(context.filesDir, "updates").canonicalFile)) { "Berkas pembaruan tidak tersedia. Unduh kembali." }
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk), "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
