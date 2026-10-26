package com.cashflow.app.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Recovery passwords are device-encrypted; Google account credentials are managed by Google. */
class BackupPasswordStore(context: Context) {
    private val preferences = BackupSettingsStore(context).preferences
    private val alias = "duitaing_backup_password_v1"
    fun isConfigured(): Boolean = preferences.contains("protected_password")

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(password: String) {
        require(password.length >= 8) { "Gunakan kata sandi cadangan minimal 8 karakter." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
        check(preferences.edit().putString("protected_password", encoded).commit()) { "Kata sandi cadangan belum dapat disimpan." }
    }
    fun read(): String {
        val bytes = preferences.getString("protected_password", null)?.let { Base64.getDecoder().decode(it) }
            ?: error("Atur kata sandi pemulihan cadangan terlebih dahulu.")
        require(bytes.size > 28) { "Atur ulang kata sandi cadangan pada perangkat ini." }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
                .doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) { error("Kunci perangkat tidak tersedia. Atur ulang kata sandi untuk cadangan baru.") }
    }
}
