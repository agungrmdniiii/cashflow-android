package com.cashflow.app.data.service

import android.net.Network
import com.cashflow.app.data.backup.BackupArchive
import com.cashflow.app.data.backup.BackupManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class CloudBackupMetadata(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdTime: String
)

object CloudBackupService {

    private const val PBKDF2_ITERATIONS = 600_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    // Magic Header untuk validasi format file terenkripsi Duit Aing
    private val ENCRYPTED_MAGIC = "DUITAING_ENC_V2".toByteArray(Charsets.UTF_8)
    private val LEGACY_MAGIC = "DUITAING_ENC_V1".toByteArray(Charsets.UTF_8)

    internal fun passwordKey(password: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }

    /**
     * Enkripsi data JSON dengan AES-256-GCM menggunakan password pengguna.
     * Format output biner: [MAGIC (15B)] + [SALT (16B)] + [IV (12B)] + [CIPHERTEXT + GCM_TAG]
     */
    fun encryptWithPassword(plainBytes: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { random.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { random.nextBytes(this) }

        val secretKey = passwordKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(ENCRYPTED_MAGIC)
        val cipherBytes = cipher.doFinal(plainBytes)

        val buffer = ByteBuffer.allocate(ENCRYPTED_MAGIC.size + salt.size + iv.size + cipherBytes.size)
        buffer.put(ENCRYPTED_MAGIC)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(cipherBytes)
        return buffer.array()
    }

    /**
     * Dekripsi file terenkripsi dengan password pengguna.
     * Akan melempar Exception jika kata sandi salah atau file rusak/korup.
     */
    fun decryptWithPassword(encryptedBytes: ByteArray, password: String): ByteArray {
        if (encryptedBytes.size < ENCRYPTED_MAGIC.size + SALT_LENGTH_BYTES + IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Format data cadangan tidak valid atau rusak.")
        }

        val buffer = ByteBuffer.wrap(encryptedBytes)
        val magic = ByteArray(ENCRYPTED_MAGIC.size)
        buffer.get(magic)

        val isLegacy = magic.contentEquals(LEGACY_MAGIC)
        if (!magic.contentEquals(ENCRYPTED_MAGIC) && !isLegacy) {
            throw IllegalArgumentException("File cadangan bukan format terenkripsi Duit Aing.")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        buffer.get(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        buffer.get(iv)

        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)

        val secretKey = passwordKey(password, salt, if (isLegacy) 10_000 else PBKDF2_ITERATIONS)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        if (!isLegacy) cipher.updateAAD(ENCRYPTED_MAGIC)
        return cipher.doFinal(cipherBytes)
    }

    data class Page(val files: List<CloudBackupMetadata>, val nextPageToken: String?)
    class RequestFailed(val status: Int) : IOException(when (status) {
        401 -> "Sesi Drive berakhir. Hubungkan kembali akun Anda."
        403 -> "Drive menolak akses. Periksa ruang penyimpanan dan izin akun."
        429 -> "Drive sedang membatasi permintaan. Coba lagi nanti."
        else -> "Drive belum dapat dihubungi (HTTP $status). Coba lagi."
    })

    private suspend fun <T> request(token: String, url: String, network: Network?, action: (HttpURLConnection, () -> Unit) -> T): Result<T> =
        withContext(Dispatchers.IO) { suspendCancellableCoroutine { continuation ->
            var connection: HttpURLConnection? = null
            try {
                require(token.isNotBlank()) { "Hubungkan akun Google terlebih dahulu." }
                val conn = ((network?.openConnection(URL(url)) ?: URL(url).openConnection()) as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20000
                    readTimeout = 60000
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connection = conn
                continuation.invokeOnCancellation { conn.disconnect() }
                val result = action(conn) { if (!continuation.isActive) throw kotlinx.coroutines.CancellationException() }
                if (continuation.isActive) continuation.resume(Result.success(result))
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(Result.failure(e))
            } finally { connection?.disconnect() }
        } }

    private fun response(conn: HttpURLConnection): JSONObject {
        if (conn.responseCode !in 200..299) throw RequestFailed(conn.responseCode)
        return conn.inputStream.use { JSONObject(BackupManager.readBackupBytes(it).toString(Charsets.UTF_8)) }
    }

    suspend fun uploadToDriveAppData(accessToken: String, file: File, network: Network? = null): Result<String> =
        request(accessToken, "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,size,md5Checksum", network) { conn, checkActive ->
            require(file.length() in 1..BackupArchive.MAX_ENCRYPTED_BYTES) { "Ukuran cadangan tidak valid." }
            val boundary = "DuitAing" + java.util.UUID.randomUUID().toString()
            val metadata = JSONObject().put("name", file.name).put("parents", org.json.JSONArray().put("appDataFolder"))
                .put("mimeType", "application/octet-stream")
            val prefix = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray(Charsets.UTF_8)
            val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val size = file.length()
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.setFixedLengthStreamingMode(prefix.size.toLong() + size + suffix.size)
            val digest = java.security.MessageDigest.getInstance("MD5")
            conn.outputStream.buffered().use { output ->
                output.write(prefix)
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(65536)
                    var copied = 0L
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        copied += count
                        require(copied <= size) { "Arsip berubah saat diunggah." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    require(copied == size) { "Arsip belum lengkap." }
                }
                output.write(suffix)
            }
            val result = response(conn)
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            require(result.optLong("size", -1) == size && result.optString("md5Checksum") == checksum) { "Drive belum mengonfirmasi cadangan yang utuh. Coba lagi." }
            result.getString("id")
        }

    suspend fun listDriveAppDataBackups(accessToken: String, pageToken: String? = null, network: Network? = null): Result<Page> {
        val page = pageToken?.let { "&pageToken=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return request(accessToken, "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=50&fields=nextPageToken,files(id,name,size,createdTime)&orderBy=createdTime%20desc&q=trashed%3Dfalse$page", network) { conn, _ ->
            val result = response(conn)
            val files = result.optJSONArray("files") ?: org.json.JSONArray()
            val records = (0 until files.length()).map { index ->
                val f = files.getJSONObject(index)
                CloudBackupMetadata(f.getString("id"), f.getString("name"), f.optLong("size", 0), f.optString("createdTime"))
            }.filter { it.name.startsWith("duitaing-backup-") && it.sizeBytes in 1..BackupArchive.MAX_ENCRYPTED_BYTES }
            Page(records, result.optString("nextPageToken").takeIf { it.isNotBlank() })
        }
    }

    suspend fun downloadDriveBackup(accessToken: String, fileId: String, destination: File, network: Network? = null): Result<Unit> {
        require(fileId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "Identitas cadangan tidak valid." }
        return request(accessToken, "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", network) { conn, checkActive ->
            if (conn.responseCode != 200) throw RequestFailed(conn.responseCode)
            require(!destination.exists() || destination.length() == 0L) { "Berkas tujuan sudah berisi data." }
            try {
                conn.inputStream.buffered().use { input -> destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        require(total <= BackupArchive.MAX_ENCRYPTED_BYTES) { "Cadangan Drive melebihi batas ukuran." }
                        output.write(buffer, 0, count)
                    }
                } }
            } catch (e: Exception) { destination.delete(); throw e }
        }
    }

    fun encryptBackupPayload(jsonString: String, password: String): ByteArray {
        return encryptWithPassword(jsonString.toByteArray(Charsets.UTF_8), password)
    }

    fun decryptBackupPayload(encryptedBytes: ByteArray, password: String): String {
        val decrypted = decryptWithPassword(encryptedBytes, password)
        return String(decrypted, Charsets.UTF_8)
    }
}
