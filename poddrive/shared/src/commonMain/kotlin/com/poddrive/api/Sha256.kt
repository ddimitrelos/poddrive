package com.poddrive.api

/**
 * Platform-specific SHA-256 digest.
 * Android actual: java.security.MessageDigest
 * iOS actual: platform.CoreCrypto.CC_SHA256
 */
expect fun sha256(input: ByteArray): ByteArray
