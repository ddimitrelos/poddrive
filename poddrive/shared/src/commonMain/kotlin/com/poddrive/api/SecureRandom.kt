package com.poddrive.api

/**
 * Platform-specific cryptographically secure random bytes.
 * Android actual: java.security.SecureRandom
 * iOS actual: Security.SecRandomCopyBytes
 *
 * Used by [SpotifyAuthManager.generateCodeVerifier] to ensure RFC 7636 §4.1 entropy.
 */
expect fun secureRandomBytes(count: Int): ByteArray
