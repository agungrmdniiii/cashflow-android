package com.cashflow.app.data.backup

import com.cashflow.app.data.service.CloudBackupService
import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Authenticated blocks keep complete backups bounded in memory, including during decryption. */
object BackupArchive {
    enum class Kind { JSON, ZIP, ENCRYPTED_JSON, ENCRYPTED_ZIP }
    const val MAX_ARCHIVE_BYTES = 272L * 1024 * 1024
    const val MAX_ENCRYPTED_BYTES = 273L * 1024 * 1024
    private val magic = "DUITAING_ZIP_V1".toByteArray(Charsets.US_ASCII)
    private const val BLOCK_BYTES = 65536
    private val random = SecureRandom()

    fun kind(file: File): Kind {
        val header = ByteArray(minOf(file.length(), 64).toInt())
        DataInputStream(file.inputStream()).use { it.readFully(header) }
        val text = header.toString(Charsets.UTF_8)
        return when {
            text.startsWith("DUITAING_ZIP_V1") -> Kind.ENCRYPTED_ZIP
            text.startsWith("DUITAING_ENC_V1") || text.startsWith("DUITAING_ENC_V2") -> Kind.ENCRYPTED_JSON
            header.size >= 4 && header[0] == 80.toByte() && header[1] == 75.toByte() &&
                ((header[2] == 3.toByte() && header[3] == 4.toByte()) || (header[2] == 5.toByte() && header[3] == 6.toByte())) -> Kind.ZIP
            text.trimStart('\uFEFF', ' ', '\t', '\r', '\n').startsWith("{") -> Kind.JSON
            else -> throw IllegalArgumentException("Berkas ini bukan cadangan Duit Aing yang dikenali.")
        }
    }

    private fun protectDestination(source: File, destination: File) {
        require(source.canonicalFile != destination.canonicalFile) { "Sumber dan tujuan cadangan harus berbeda." }
        require(!destination.exists() || destination.length() == 0L) { "Berkas tujuan sudah berisi data." }
    }

    fun encrypt(source: File, destination: File, password: String, checkActive: () -> Unit = {}) {
        protectDestination(source, destination)
        require(password.length >= 8) { "Gunakan kata sandi cadangan minimal 8 karakter." }
        val size = source.length()
        require(source.isFile && size in 0..MAX_ARCHIVE_BYTES) { "Ukuran arsip melebihi 272 MB." }
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(8).also(random::nextBytes)
        val header = magic + salt + prefix + ByteBuffer.allocate(8).putLong(size).array()
        val key = CloudBackupService.passwordKey(password, salt)
        try {
            DataInputStream(source.inputStream().buffered()).use { input ->
                destination.outputStream().buffered().use { output ->
                    output.write(header)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    var remaining = size
                    var index = 0
                    do {
                        checkActive()
                        val plain = ByteArray(minOf(remaining, BLOCK_BYTES.toLong()).toInt())
                        input.readFully(plain)
                        val counter = ByteBuffer.allocate(4).putInt(index++).array()
                        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, prefix + counter))
                        cipher.updateAAD(header + counter)
                        output.write(cipher.doFinal(plain))
                        remaining -= plain.size
                    } while (remaining > 0)
                    require(input.read() == -1) { "Arsip berubah saat dicadangkan. Coba lagi." }
                }
            }
        } catch (e: Exception) { destination.delete(); throw e }
    }

    fun decrypt(source: File, destination: File, password: String, checkActive: () -> Unit = {}) {
        protectDestination(source, destination)
        try {
            require(source.length() <= MAX_ENCRYPTED_BYTES) { "Cadangan melebihi batas ukuran." }
            DataInputStream(source.inputStream().buffered()).use { input ->
                val header = ByteArray(magic.size + 16 + 8 + 8).also(input::readFully)
                require(header.copyOfRange(0, magic.size).contentEquals(magic)) { "Format cadangan terenkripsi tidak dikenali." }
                val salt = header.copyOfRange(magic.size, magic.size + 16)
                val prefix = header.copyOfRange(magic.size + 16, magic.size + 24)
                var remaining = ByteBuffer.wrap(header, header.size - 8, 8).long
                require(remaining in 0..MAX_ARCHIVE_BYTES) { "Ukuran cadangan tidak valid." }
                val key = CloudBackupService.passwordKey(password, salt)
                destination.outputStream().buffered().use { output ->
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    var index = 0
                    do {
                        checkActive()
                        val count = minOf(remaining, BLOCK_BYTES.toLong()).toInt()
                        val encrypted = ByteArray(count + 16).also(input::readFully)
                        val counter = ByteBuffer.allocate(4).putInt(index++).array()
                        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, prefix + counter))
                        cipher.updateAAD(header + counter)
                        output.write(cipher.doFinal(encrypted))
                        remaining -= count
                    } while (remaining > 0)
                    require(input.read() == -1) { "Ada data tambahan di luar cadangan." }
                }
            }
        } catch (e: Exception) {
            destination.delete()
            throw IllegalArgumentException("Cadangan tidak dapat dibuka. Periksa kata sandi atau gunakan berkas yang utuh.", e)
        }
    }

    fun copyBounded(input: InputStream, destination: File, maximum: Long = MAX_ENCRYPTED_BYTES) {
        require(!destination.exists() || destination.length() == 0L) { "Berkas tujuan sudah berisi data." }
        try {
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(BLOCK_BYTES)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    require(total <= maximum) { "Cadangan melebihi batas ukuran." }
                    output.write(buffer, 0, count)
                }
            }
        } catch (e: Exception) { destination.delete(); throw e }
    }
}
