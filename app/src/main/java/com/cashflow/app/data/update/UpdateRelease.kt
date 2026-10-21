package com.cashflow.app.data.update

import org.json.JSONObject
import java.net.URI

data class UpdateRelease(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val size: Long,
    val sha256: String,
    val apkUrl: String,
    val notes: String
) {
    fun isNewerThan(installedCode: Long) = versionCode > installedCode
    fun supportsAndroid(sdk: Int) = sdk >= minSdk

    companion object {
        const val FEED_URL = "https://github.com/agungrmdniiii/cashflow-android/releases/latest/download/update.json"
        const val MAX_APK_BYTES = 200L * 1024 * 1024

        fun parse(text: String): UpdateRelease {
            require(text.length <= 65536) { "Informasi pembaruan terlalu besar." }
            try {
                val json = JSONObject(text)
                val code = json.getLong("versionCode")
                val name = json.getString("versionName").trim()
                val sdk = json.getInt("minSdk")
                val size = json.getLong("size")
                val hash = json.getString("sha256").lowercase()
                val url = json.getString("apkUrl")
                val notes = json.getString("notes").trim()
                require(code in 1..Int.MAX_VALUE.toLong() && name.length in 1..64 && sdk > 0)
                require(size in 1..MAX_APK_BYTES && hash.matches(Regex("[a-f0-9]{64}")))
                require(notes.length in 1..12000)
                val uri = URI(url)
                require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null)
                require(uri.port == -1 && uri.query == null && uri.fragment == null)
                require(uri.rawPath.matches(Regex("/agungrmdniiii/cashflow-android/releases/download/[A-Za-z0-9._-]+/DuitAing[.]apk")))
                require(uri.path.split('/').none { it == "." || it == ".." })
                return UpdateRelease(code, name, sdk, size, hash, url, notes)
            } catch (e: Exception) {
                throw IllegalArgumentException("Informasi pembaruan tidak valid. Coba lagi nanti.", e)
            }
        }
    }
}
