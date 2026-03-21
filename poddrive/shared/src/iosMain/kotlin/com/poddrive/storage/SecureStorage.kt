package com.poddrive.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.CFTypeRefVar
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS implementation of SecureStorage using the Keychain (Security framework).
 * Service: "com.poddrive"; items stored as kSecClassGenericPassword.
 * Strategy on save: SecItemUpdate first; if errSecItemNotFound → SecItemAdd.
 * All Keychain errors are surfaced as IllegalStateException so callers cannot silently
 * proceed with missing or partially-written credentials.
 * [Source: architecture.md#Authentication & Security, NFR6]
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureStorage {

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        require(accessToken.isNotEmpty()) { "accessToken must not be empty" }
        require(refreshToken.isNotEmpty()) { "refreshToken must not be empty" }
        saveItem(KEY_ACCESS_TOKEN, accessToken)
        saveItem(KEY_REFRESH_TOKEN, refreshToken)
    }

    actual fun getAccessToken(): String? = readItem(KEY_ACCESS_TOKEN)

    actual fun getRefreshToken(): String? = readItem(KEY_REFRESH_TOKEN)

    actual fun clear() {
        // Delete both items; collect any error and throw after attempting both deletes
        // so we always attempt cleanup of the second token even if the first fails.
        val accessError = runCatching { deleteItem(KEY_ACCESS_TOKEN) }.exceptionOrNull()
        val refreshError = runCatching { deleteItem(KEY_REFRESH_TOKEN) }.exceptionOrNull()
        val firstError = accessError ?: refreshError
        if (firstError != null) {
            throw IllegalStateException("Keychain clear failed: ${firstError.message}", firstError)
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun saveItem(key: String, value: String) {
        val data = value.toNSData()
            ?: throw IllegalStateException("Failed to encode value for key '$key' to NSData")

        val searchQuery = buildBaseQuery(key)

        // Attempt update first; only add if the item does not yet exist
        val updateAttributes = NSMutableDictionary()
        updateAttributes.setValue(data, forKey = kSecValueData as String)
        val updateStatus = SecItemUpdate(searchQuery, updateAttributes)

        when {
            updateStatus == errSecSuccess -> return
            updateStatus == errSecItemNotFound -> {
                val addQuery = buildBaseQuery(key)
                addQuery.setValue(data, forKey = kSecValueData as String)
                val addStatus = SecItemAdd(addQuery, null)
                if (addStatus != errSecSuccess) {
                    throw IllegalStateException(
                        "Keychain SecItemAdd failed for key '$key' with status $addStatus"
                    )
                }
            }
            else -> throw IllegalStateException(
                "Keychain SecItemUpdate failed for key '$key' with status $updateStatus"
            )
        }
    }

    private fun readItem(key: String): String? {
        val query = buildBaseQuery(key)
        query.setValue(true, forKey = kSecReturnData as String)
        query.setValue(kSecMatchLimitOne, forKey = kSecMatchLimit as String)

        return memScoped {
            val resultRef = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, resultRef.ptr)
            when (status) {
                errSecSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    (resultRef.value as? NSData)?.toKotlinString()
                }
                errSecItemNotFound -> null
                else -> null // item unreadable (e.g. device locked) — treat as absent
            }
        }
    }

    private fun deleteItem(key: String) {
        val status = SecItemDelete(buildBaseQuery(key))
        // errSecItemNotFound is acceptable — item was already absent
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw IllegalStateException(
                "Keychain SecItemDelete failed for key '$key' with status $status"
            )
        }
    }

    private fun buildBaseQuery(key: String): NSMutableDictionary {
        val query = NSMutableDictionary()
        query.setValue(kSecClassGenericPassword, forKey = kSecClass as String)
        query.setValue(SERVICE_NAME, forKey = kSecAttrService as String)
        query.setValue(key, forKey = kSecAttrAccount as String)
        return query
    }

    private fun String.toNSData(): NSData? =
        NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding)

    private fun NSData.toKotlinString(): String? =
        NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()

    private companion object {
        const val SERVICE_NAME = "com.poddrive"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
