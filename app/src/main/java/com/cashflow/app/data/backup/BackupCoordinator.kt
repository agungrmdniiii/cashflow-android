package com.cashflow.app.data.backup

import android.accounts.Account
import android.app.PendingIntent
import android.app.job.JobScheduler
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.provider.DocumentsContract
import com.cashflow.app.CashflowApplication
import com.cashflow.app.data.preferences.PreferenceManager
import com.cashflow.app.data.service.AutomaticBackupJob
import com.cashflow.app.data.service.CloudBackupService
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PreparedRestore(val validation: BackupValidationResult.Valid, val zip: File?)
class DriveAuthorizationRequired(val resolution: PendingIntent?) : IllegalStateException("Hubungkan kembali akun Google untuk melanjutkan cadangan.")

class BackupCoordinator(context: Context) {
    private val app = context.applicationContext as CashflowApplication
    val store = BackupSettingsStore(app)
    private val repository get() = app.repository
    private val cache get() = File(app.cacheDir, "backups").apply { mkdirs() }

    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        // ponytail: one backup/data change per process; split by account only if simultaneous destinations are introduced.
        private val mutex = Mutex()
        private val dataChanges = Mutex()
        private val progress = MutableStateFlow<String?>(null)
        val status = progress.asStateFlow()
        @Volatile private var active: Job? = null
        fun cancelRunning() { active?.cancel() }
        suspend fun stopAndJoin() { active?.cancelAndJoin() }

        suspend fun <T> changeData(context: Context, action: suspend () -> T): T = dataChanges.withLock {
            val settings = BackupSettingsStore(context)
            settings.preferences.edit().putBoolean("data_change", true).apply()
            context.getSystemService(JobScheduler::class.java).cancel(AutomaticBackupJob.JOB_ID)
            try {
                stopAndJoin()
                mutex.withLock {
                    settings.preferences.edit().remove("automatic_success_LOCAL").remove("automatic_success_DRIVE").apply()
                    progress.value = "Memproses perubahan data…"
                    try { action() } finally { progress.value = null }
                }
            } finally {
                settings.preferences.edit().remove("data_change").apply()
                runCatching { AutomaticBackupJob.schedule(context, replace = true) }.onFailure {
                    settings.recordError(settings.automation().destination, "Jadwal perlu diaktifkan kembali melalui pengaturan cadangan.")
                }
            }
        }
    }

    private suspend fun <T> operation(destination: BackupDestination?, message: String, action: suspend () -> T): T = withContext(Dispatchers.IO) {
        check(mutex.tryLock()) { "Cadangan lain sedang diproses. Tunggu sampai selesai." }
        try {
            active = currentCoroutineContext().job
            check(!store.preferences.getBoolean("data_change", false)) { "Data sedang dipulihkan atau dihapus. Tunggu sampai selesai." }
            progress.value = message
            action()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            destination?.let { store.recordError(it, e.message ?: "Cadangan belum dapat diselesaikan. Coba lagi.") }
            if (e is DriveAuthorizationRequired || e is CloudBackupService.RequestFailed && e.status == 401)
                store.preferences.edit().putBoolean("drive_reauth", true).apply()
            throw e
        } finally { progress.value = null; active = null; mutex.unlock() }
    }

    private suspend fun <T> awaitGoogle(task: Task<T>): T = withTimeout(45000) {
        suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Koneksi Google belum berhasil. Periksa akun dan coba lagi.")) }
                .addOnCanceledListener { continuation.cancel() }
        }
    }

    suspend fun authorize(accountName: String): AuthorizationResult = awaitGoogle(Identity.getAuthorizationClient(app).authorize(
        AuthorizationRequest.builder().setAccount(Account(accountName, "com.google"))
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).setOptOutIncludingGrantedScopes(true).build()))

    fun acceptAuthorization(accountName: String, result: AuthorizationResult) {
        check(!result.hasResolution() && !result.accessToken.isNullOrBlank() && DRIVE_SCOPE in result.grantedScopes) { "Izin cadangan Drive belum diberikan." }
        if (store.driveAccount != accountName) {
            store.preferences.edit().remove("success_DRIVE").remove("automatic_success_DRIVE").apply()
            if (store.automation().destination == BackupDestination.DRIVE) store.save(store.automation().copy(enabled = false))
        }
        store.driveAccount = accountName
        store.preferences.edit().remove("drive_reauth").remove("error_DRIVE").apply()
        AutomaticBackupJob.schedule(app, replace = true)
    }

    private suspend fun token(): String {
        val account = store.driveAccount ?: throw DriveAuthorizationRequired(null)
        val result = authorize(account)
        if (result.hasResolution()) throw DriveAuthorizationRequired(result.pendingIntent)
        check(DRIVE_SCOPE in result.grantedScopes) { "Izin cadangan Drive belum diberikan." }
        return result.accessToken?.takeIf { it.isNotBlank() } ?: throw DriveAuthorizationRequired(null)
    }

    suspend fun disconnectDrive(): Boolean {
        cancelRunning()
        store.save(store.automation().let { if (it.destination == BackupDestination.DRIVE) it.copy(enabled = false) else it })
        AutomaticBackupJob.schedule(app, replace = true)
        return try {
            store.driveAccount?.let { account ->
                awaitGoogle(Identity.getAuthorizationClient(app).revokeAccess(RevokeAccessRequest.builder()
                    .setAccount(Account(account, "com.google")).setScopes(listOf(Scope(DRIVE_SCOPE))).build()))
            }
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        finally {
            store.driveAccount = null
            store.preferences.edit().remove("drive_reauth").apply()
        }
    }

    private fun allowed(caps: NetworkCapabilities, policy: BackupNetwork): Boolean = policy.allows(
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR), !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))

    fun driveNetwork(requested: Network? = null): Network {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val network = requested ?: connectivity.activeNetwork ?: error("Menunggu koneksi internet.")
        val caps = connectivity.getNetworkCapabilities(network) ?: error("Menunggu koneksi internet.")
        check(allowed(caps, store.automation().network)) {
            if (store.automation().network == BackupNetwork.WIFI) "Menunggu Wi-Fi tanpa batas kuota. Anda dapat mengubah pilihan jaringan."
            else "Koneksi internet belum siap. Coba lagi setelah tersambung."
        }
        return network
    }

    private suspend fun <T> driveTransfer(requested: Network? = null, action: suspend (Network) -> T): T {
        try {
            return coroutineScope {
                val network = driveNetwork(requested)
                val policy = store.automation().network
                val transfer = currentCoroutineContext().job
                val manager = app.getSystemService(ConnectivityManager::class.java)
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onLost(changed: Network) { if (changed == network) transfer.cancel() }
                    override fun onCapabilitiesChanged(changed: Network, caps: NetworkCapabilities) {
                        if (changed == network && !allowed(caps, policy)) transfer.cancel()
                    }
                    override fun onBlockedStatusChanged(changed: Network, blocked: Boolean) {
                        if (changed == network && blocked) transfer.cancel()
                    }
                }
                // Observe the chosen network, including VPNs; never bypass a user's VPN to satisfy Wi-Fi.
                manager.registerNetworkCallback(NetworkRequest.Builder()
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
                try { driveNetwork(network); action(network) }
                finally { runCatching { manager.unregisterNetworkCallback(callback) } }
            }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw IOException("Jaringan berubah atau dibatasi. Cadangan dihentikan dan dapat dicoba kembali pada jaringan yang diizinkan.", e)
        }
    }

    private fun temporary(extension: String): File = File(cache, "duitaing-backup-" +
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + UUID.randomUUID().toString().take(8) + extension)

    private suspend fun zip(): File {
        repository.awaitInitialLoad()
        val file = temporary(".zip")
        try {
            repository.createBackupZip(file).getOrThrow()
            currentCoroutineContext().ensureActive()
            check(BackupManager.validateBackupZip(file) is BackupValidationResult.Valid) { "Cadangan belum lengkap. Coba lagi." }
            return file
        } catch (e: Exception) { file.delete(); throw e }
    }

    suspend fun prepareLocal(): File = operation(BackupDestination.LOCAL, "Menyiapkan cadangan…") { zip() }

    private fun digest(file: File): ByteArray = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) {
            val count = input.read(buffer); if (count == -1) break; digest.update(buffer, 0, count)
        } }; digest.digest()
    }

    private suspend fun writePrepared(file: File, uri: Uri) {
        val hash = digest(file)
        val expected = file.length()
        requireNotNull(app.contentResolver.openOutputStream(uri, "w")) { "Lokasi cadangan tidak dapat ditulis." }.use { output ->
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count == -1) break; output.write(buffer, 0, count)
            } }; output.flush()
        }
        val readBack = MessageDigest.getInstance("SHA-256")
        var total = 0L
        requireNotNull(app.contentResolver.openInputStream(uri)) { "Cadangan belum dapat diverifikasi." }.use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count == -1) break
                total += count
                check(total <= expected) { "Cadangan tersimpan berbeda dari sumber." }
                readBack.update(buffer, 0, count)
            }
        }
        check(total == expected && hash.contentEquals(readBack.digest())) { "Cadangan belum tersimpan lengkap. Coba lagi." }
    }

    private fun success(destination: BackupDestination, automatic: Boolean) {
        val now = System.currentTimeMillis()
        store.recordSuccess(destination, automatic, now)
        com.cashflow.app.data.service.NotificationHelper.clearBackupFailure(app, destination)
        PreferenceManager(app).setBackupSaved(if (destination == BackupDestination.DRIVE) "Drive terenkripsi + foto" else "ZIP + foto", now)
    }

    suspend fun saveLocal(file: File, uri: Uri) = operation(BackupDestination.LOCAL, "Menyimpan cadangan…") {
        var saved = false
        try {
            writePrepared(file, uri)
            currentCoroutineContext().ensureActive()
            success(BackupDestination.LOCAL, false)
            saved = true
        } finally {
            // This URI comes from CreateDocument, so only this new, incomplete document is removed.
            if (!saved && uri.scheme == "content") runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
        }
    }

    suspend fun runAutomaticOrDrive(destination: BackupDestination, automatic: Boolean, requestedNetwork: Network? = null) =
        operation(destination, "Mencadangkan data…") {
            suspend fun perform(network: Network?) {
            val accessToken = if (destination == BackupDestination.DRIVE) token() else null
            val file = zip()
            var encrypted: File? = null
            var created: Uri? = null
            var saved = false
            try {
                currentCoroutineContext().ensureActive()
                if (destination == BackupDestination.DRIVE) {
                    val protected = temporary(".duitaing").also { encrypted = it }
                    val job = currentCoroutineContext().job
                    BackupArchive.encrypt(file, protected, BackupPasswordStore(app).read()) { job.ensureActive() }
                    currentCoroutineContext().ensureActive()
                    driveNetwork(network)
                    CloudBackupService.uploadToDriveAppData(accessToken!!, protected, network).getOrThrow()
                } else {
                    val tree = store.localFolder ?: error("Pilih folder untuk cadangan otomatis terlebih dahulu.")
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    created = DocumentsContract.createDocument(app.contentResolver, parent, "application/zip", file.name)
                        ?: throw IOException("Folder cadangan tidak dapat ditulis. Pilih ulang foldernya.")
                    writePrepared(file, created!!)
                }
                currentCoroutineContext().ensureActive()
                success(destination, automatic)
                saved = true
            } finally {
                file.delete(); encrypted?.delete()
                if (!saved && created != null) runCatching { DocumentsContract.deleteDocument(app.contentResolver, created!!) }
            }
            }
            if (destination == BackupDestination.DRIVE) driveTransfer(requestedNetwork) { perform(it) } else perform(null)
        }

    suspend fun listDrive(pageToken: String? = null): CloudBackupService.Page = operation(null, "Membaca cadangan Drive…") {
        driveTransfer { network -> CloudBackupService.listDriveAppDataBackups(token(), pageToken, network).getOrThrow() }
    }

    suspend fun downloadDrive(id: String): File = operation(null, "Mengunduh cadangan…") {
        val file = temporary(".download")
        try { driveTransfer { network -> CloudBackupService.downloadDriveBackup(token(), id, file, network).getOrThrow() }; file }
        catch (e: Exception) { file.delete(); throw e }
    }

    suspend fun inspect(file: File, password: String? = null): PreparedRestore = operation(null, "Memeriksa cadangan…") {
        var decoded: File? = null
        try {
            val source = when (BackupArchive.kind(file)) {
                BackupArchive.Kind.ENCRYPTED_ZIP -> temporary(".zip").also {
                    decoded = it
                    val job = currentCoroutineContext().job
                    BackupArchive.decrypt(file, it, password ?: error("Masukkan kata sandi cadangan.")) { job.ensureActive() }
                }
                BackupArchive.Kind.ENCRYPTED_JSON -> temporary(".json").also {
                    decoded = it
                    val bytes = file.inputStream().use(BackupManager::readBackupBytes)
                    it.writeText(CloudBackupService.decryptBackupPayload(bytes, password ?: error("Masukkan kata sandi cadangan.")), Charsets.UTF_8)
                }
                else -> file
            }
            val isZip = BackupArchive.kind(source) == BackupArchive.Kind.ZIP
            val validation = if (isZip) BackupManager.validateBackupZip(source) else source.inputStream().use {
                BackupManager.validateBackupJson(BackupManager.readBackupBytes(it).toString(Charsets.UTF_8).removePrefix("\uFEFF"))
            }
            require(validation is BackupValidationResult.Valid) { (validation as BackupValidationResult.Invalid).reason }
            currentCoroutineContext().ensureActive()
            PreparedRestore(validation, if (isZip) source else null).also { if (!isZip) decoded?.delete() }
        } catch (e: Exception) { decoded?.delete(); throw e }
    }
}
