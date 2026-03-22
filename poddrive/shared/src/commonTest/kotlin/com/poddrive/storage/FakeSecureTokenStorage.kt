package com.poddrive.storage

/**
 * In-memory [SecureTokenStorage] implementation for use in unit tests.
 * Does not use the platform Keychain or EncryptedSharedPreferences.
 */
class FakeSecureTokenStorage : SecureTokenStorage {
    var savedAccessToken: String? = null
        private set
    var savedRefreshToken: String? = null
        private set

    override fun saveTokens(accessToken: String, refreshToken: String) {
        savedAccessToken = accessToken
        savedRefreshToken = refreshToken
    }

    override fun getAccessToken(): String? = savedAccessToken

    override fun getRefreshToken(): String? = savedRefreshToken

    override fun clear() {
        savedAccessToken = null
        savedRefreshToken = null
    }
}
