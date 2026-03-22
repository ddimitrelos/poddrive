package com.poddrive.api

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    bytes.usePinned { pinned ->
        val result = SecRandomCopyBytes(
            kSecRandomDefault,
            count.convert(),
            pinned.addressOf(0)
        )
        check(result == errSecSuccess) { "SecRandomCopyBytes failed with code $result" }
    }
    return bytes
}
