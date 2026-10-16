package com.cashflow.app

import com.cashflow.app.data.service.CloudBackupService
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SecurityHardeningTest {

    @Test
    fun newBackupsUseVersionTwoAndLegacyBackupsRemainReadable() {
        val password = "Audit backup password 2026!"
        val plain = "{\"legacy\":true}".toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 16).toByte() }
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 10_000, 256)
        val key = javax.crypto.spec.SecretKeySpec(javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        val legacy = "DUITAING_ENC_V1".toByteArray(Charsets.UTF_8) + salt + iv + cipher.doFinal(plain)
        assertArrayEquals(plain, CloudBackupService.decryptWithPassword(legacy, password))
        val current = CloudBackupService.encryptWithPassword(plain, password)
        assertTrue(current.copyOfRange(0, 15).toString(Charsets.UTF_8) == "DUITAING_ENC_V2")
        assertArrayEquals(plain, CloudBackupService.decryptWithPassword(current, password))
    }

    @Test
    fun testAesGcmEncryption_TamperResistance() {
        val originalPlaintext = "{\"test\": \"sensitive_financial_data\", \"nominal\": 5000000}"
        val plainBytes = originalPlaintext.toByteArray(Charsets.UTF_8)
        val password = "StrongMasterPassword2026!"

        val encryptedBytes = CloudBackupService.encryptWithPassword(plainBytes, password)
        assertNotNull(encryptedBytes)
        assertTrue("Ciphertext should be larger than plaintext due to magic + salt + IV + tag", encryptedBytes.size > plainBytes.size)

        // 1. Valid decryption
        val decryptedBytes = CloudBackupService.decryptWithPassword(encryptedBytes, password)
        val decrypted = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(originalPlaintext, decrypted)

        // 2. Tampered ciphertext (flip a byte in payload)
        val tamperedBytes = encryptedBytes.copyOf()
        val tamperIndex = tamperedBytes.size - 5
        tamperedBytes[tamperIndex] = (tamperedBytes[tamperIndex].toInt() xor 0xFF).toByte()

        try {
            CloudBackupService.decryptWithPassword(tamperedBytes, password)
            fail("Decryption of tampered ciphertext should throw an exception")
        } catch (_: Exception) {
            // Expected cryptographic verification failure
        }

        // 3. Wrong password decryption failure
        try {
            CloudBackupService.decryptWithPassword(encryptedBytes, "WrongPassword!")
            fail("Decryption with wrong password should fail tag verification")
        } catch (_: Exception) {
            // Expected
        }
    }

    @Test
    fun testZeroHardcodedApiKeysInSource() {
        // Scan Kotlin source files for raw Gemini API key patterns
        val projectRoot = File(".")
        val srcDir = File(projectRoot, "src/main").takeIf { it.isDirectory } ?: File(projectRoot, "app/src/main")
        assertTrue("The API-key scan must inspect an existing source directory", srcDir.isDirectory)
        val apiKeyPattern = Regex("""AIzaSy[0-9A-Za-z_-]{33}""")

        var violationCount = 0
        srcDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            val matches = apiKeyPattern.findAll(content).toList()
            if (matches.isNotEmpty()) {
                println("API Key leak detected in: ${file.path}")
                violationCount += matches.size
            }
        }

        assertEquals("Zero hardcoded Google API keys must exist in source files", 0, violationCount)
    }
}
