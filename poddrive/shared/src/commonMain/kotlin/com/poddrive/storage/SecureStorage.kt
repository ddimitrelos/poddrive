package com.poddrive.storage

/**
 * Platform-specific secure credential storage.
 * Android actual: EncryptedSharedPreferences (androidx.security:security-crypto)
 * iOS actual: Keychain (Security framework — SecItemAdd/Copy/Update/Delete)
 *
 * RULES (NFR6, AR4):
 * - Tokens MUST NEVER be stored in SQLDelight or plain SharedPreferences.
 * - saveTokens() is the ONLY way to persist credentials.
 * - Only SpotifyApiClient (Story 1.3) may call saveTokens() / clear().
 * [Source: architecture.md#Authentication & Security]
 */
expect class SecureStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
}
