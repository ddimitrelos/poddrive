package com.poddrive.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android implementation of SecureStorage using EncryptedSharedPreferences.
 * Keys are encrypted with AES256-SIV; values with AES256-GCM.
 * Backed by Android Keystore — credentials survive app restart but not device reset.
 * [Source: architecture.md#Authentication & Security, NFR6]
 */
actual class SecureStorage(private val context: Context) {

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialise EncryptedSharedPreferences: ${e.message}", e)
        }
    }

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        require(accessToken.isNotEmpty()) { "accessToken must not be empty" }
        require(refreshToken.isNotEmpty()) { "refreshToken must not be empty" }
        val committed = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .commit()
        check(committed) { "EncryptedSharedPreferences commit failed for saveTokens" }
    }

    actual fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    actual fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    actual fun clear() {
        val committed = prefs.edit().clear().commit()
        check(committed) { "EncryptedSharedPreferences commit failed for clear" }
    }

    private companion object {
        const val FILE_NAME = "poddrive_secure_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
