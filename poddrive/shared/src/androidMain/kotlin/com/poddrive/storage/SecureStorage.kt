package com.poddrive.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Android implementation of SecureStorage using EncryptedSharedPreferences.
 * Keys are encrypted with AES256-SIV; values with AES256-GCM.
 * Backed by Android Keystore — credentials survive app restart but not device reset.
 * Uses security-crypto 1.0.0 stable API (MasterKeys / AES256_GCM_SPEC).
 * [Source: architecture.md#Authentication & Security, NFR6]
 */
actual class SecureStorage(private val context: Context) {

    private val prefs by lazy {
        try {
            // MasterKeys.getOrCreate is the stable 1.0.0 API; MasterKey.Builder is alpha-only.
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                context,
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
