package com.poddrive.storage

/**
 * Interface for secure token persistence, enabling testing without platform actuals.
 * [SecureStorage] on each platform implements this interface.
 * [SpotifyAuthManager] depends on this interface (not the concrete expect class).
 */
interface SecureTokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
}
