package com.poddrive.api

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256(input: ByteArray): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            val result = CC_SHA256(
                inputPinned.addressOf(0),
                input.size.convert(),
                digestPinned.addressOf(0)
            )
            checkNotNull(result) { "CC_SHA256 returned null — digest computation failed" }
        }
    }
    return digest
}
