package com.poddrive.api

actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    java.security.SecureRandom().nextBytes(bytes)
    return bytes
}
