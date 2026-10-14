package com.cashflow.app.data.service

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

    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    // Magic Header untuk validasi format file terenkripsi Duit Aing
    private val ENCRYPTED_MAGIC = "DUITAING_ENC_V1".toByteArray(Charsets.UTF_8)

    /**
     * Enkripsi data JSON dengan AES-256-GCM menggunakan password pengguna.
     * Format output biner: [MAGIC (15B)] + [SALT (16B)] + [IV (12B)] + [CIPHERTEXT + GCM_TAG]
     */
    fun encryptWithPassword(plainBytes: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { random.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { random.nextBytes(this) }

        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
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

        if (!magic.contentEquals(ENCRYPTED_MAGIC)) {
            throw IllegalArgumentException("File cadangan bukan format terenkripsi Duit Aing.")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        buffer.get(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        buffer.get(iv)

        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherBytes)
    }

    /**
     * Mengunggah file terenkripsi ke folder Google Drive appData (terisolasi & privat).
     */
    suspend fun uploadToDriveAppData(
        accessToken: String,
        fileName: String,
        fileBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val boundary = "-------DuitAingDriveBoundary" + System.currentTimeMillis()
        try {
            val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
                connectTimeout = 20000
                readTimeout = 20000
            }

            val metadata = JSONObject().apply {
                put("name", fileName)
                put("parents", org.json.JSONArray().put("appDataFolder"))
                put("mimeType", "application/octet-stream")
            }

            DataOutputStream(conn.outputStream).use { out ->
                // Part 1: Metadata
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                out.writeBytes(metadata.toString() + "\r\n")

                // Part 2: Media
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                out.write(fileBytes)
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val id = JSONObject(response).getString("id")
                Result.success(id)
            } else {
                val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                Result.failure(Exception("Upload gagal (HTTP $code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil daftar file backup di folder Google Drive appData.
     */
    suspend fun listDriveAppDataBackups(
        accessToken: String
    ): Result<List<CloudBackupMetadata>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,size,createdTime)&orderBy=createdTime%20desc")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val code = conn.responseCode
            if (code == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(response)
                val files = json.optJSONArray("files") ?: org.json.JSONArray()
                val list = mutableListOf<CloudBackupMetadata>()
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    list.add(
                        CloudBackupMetadata(
                            id = f.getString("id"),
                            name = f.getString("name"),
                            sizeBytes = f.optLong("size", 0L),
                            createdTime = f.optString("createdTime", "")
                        )
                    )
                }
                Result.success(list)
            } else {
                val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                Result.failure(Exception("List backup gagal (HTTP $code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengunduh file backup dari Google Drive appData.
     */
    suspend fun downloadDriveBackup(
        accessToken: String,
        fileId: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 20000
                readTimeout = 20000
            }

            val code = conn.responseCode
            if (code == 200) {
                val bytes = conn.inputStream.use { it.readBytes() }
                Result.success(bytes)
            } else {
                val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                Result.failure(Exception("Download backup gagal (HTTP $code): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
