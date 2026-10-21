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

enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }
data class UpdateStatus(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: UpdateRelease? = null,
    val progress: Int = 0,
    val apk: File? = null,
    val message: String = ""
)

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _status = MutableStateFlow(UpdateStatus())
    val status = _status.asStateFlow()
    private var job: Job? = null

    fun check() {
        if (_status.value.phase in listOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.READY)) return
        _status.value = UpdateStatus(UpdatePhase.CHECKING)
        job = viewModelScope.launch(Dispatchers.IO) {
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
                _status.value = when {
                    !release.isNewerThan(BuildConfig.VERSION_CODE.toLong()) -> UpdateStatus(UpdatePhase.CURRENT, message = "Anda menggunakan versi terbaru.")
                    !release.supportsAndroid(Build.VERSION.SDK_INT) -> UpdateStatus(UpdatePhase.ERROR, message = "Pembaruan ini memerlukan Android API ${release.minSdk} atau lebih baru.")
                    else -> UpdateStatus(UpdatePhase.AVAILABLE, release)
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

    fun download() {
        val release = _status.value.release ?: return
        if (_status.value.phase == UpdatePhase.DOWNLOADING) return
        job?.cancel()
        _status.value = UpdateStatus(UpdatePhase.DOWNLOADING, release)
        job = viewModelScope.launch(Dispatchers.IO) {
            var partial: File? = null
            var completed: File? = null
            try {
                val directory = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
                val target = File.createTempFile("download-", ".part", directory)
                partial = target
                directory.listFiles { file -> file.extension == "apk" }?.forEach { it.delete() }
                val connection = openGitHubConnection(release.apkUrl)
                try {
                    require(connection.contentLengthLong < 0 || connection.contentLengthLong == release.size) { "Ukuran unduhan tidak sesuai. Unduh ulang pembaruan." }
                    connection.inputStream.use { input ->
                        copyVerifiedDownload(input, target, release) { progress ->
                            _status.value = UpdateStatus(UpdatePhase.DOWNLOADING, release, progress)
                        }
                    }
                } finally { connection.disconnect() }
                currentCoroutineContext().ensureActive()
                verifyUpdateApk(getApplication(), target, release)
                currentCoroutineContext().ensureActive()
                val apk = File(directory, "DuitAing-${release.versionCode}.apk")
                require(target.renameTo(apk)) { "Berkas pembaruan tidak dapat disiapkan." }
                completed = apk
                _status.value = UpdateStatus(UpdatePhase.READY, release, 100, apk)
            } catch (e: CancellationException) { throw e
            } catch (e: IllegalArgumentException) {
                currentCoroutineContext().ensureActive()
                _status.value = UpdateStatus(UpdatePhase.ERROR, release, message = e.message ?: "Paket pembaruan tidak valid.")
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                _status.value = UpdateStatus(UpdatePhase.ERROR, release, message = "Unduhan gagal. Periksa koneksi dan ruang penyimpanan, lalu coba lagi.")
            } finally {
                partial?.delete()
                if (completed != null && _status.value.apk != completed) completed.delete()
            }
        }
    }

    fun cancelDownload() {
        val release = _status.value.release ?: return
        job?.cancel()
        _status.value = UpdateStatus(UpdatePhase.AVAILABLE, release)
    }
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
    require(apk.isFile && apk.canonicalFile.parentFile == File(context.cacheDir, "updates").canonicalFile) { "Berkas pembaruan tidak tersedia. Unduh kembali." }
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk), "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
