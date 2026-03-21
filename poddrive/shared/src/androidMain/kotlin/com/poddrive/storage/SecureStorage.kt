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
    }

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    actual fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    actual fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    actual fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "poddrive_secure_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
