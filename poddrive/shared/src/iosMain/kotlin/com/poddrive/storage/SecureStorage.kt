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
 * [Source: architecture.md#Authentication & Security, NFR6]
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureStorage {

    actual fun saveTokens(accessToken: String, refreshToken: String) {
        saveItem(KEY_ACCESS_TOKEN, accessToken)
        saveItem(KEY_REFRESH_TOKEN, refreshToken)
    }

    actual fun getAccessToken(): String? = readItem(KEY_ACCESS_TOKEN)

    actual fun getRefreshToken(): String? = readItem(KEY_REFRESH_TOKEN)

    actual fun clear() {
        deleteItem(KEY_ACCESS_TOKEN)
        deleteItem(KEY_REFRESH_TOKEN)
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun saveItem(key: String, value: String) {
        val data = value.toNSData() ?: return
        val searchQuery = buildBaseQuery(key)

        // Attempt update; if item doesn't exist yet, add it
        val updateAttributes = NSMutableDictionary()
        updateAttributes.setValue(data, forKey = kSecValueData as String)
        val updateStatus = SecItemUpdate(searchQuery, updateAttributes)

        if (updateStatus == errSecItemNotFound) {
            val addQuery = buildBaseQuery(key)
            addQuery.setValue(data, forKey = kSecValueData as String)
            SecItemAdd(addQuery, null)
        }
    }

    private fun readItem(key: String): String? {
        val query = buildBaseQuery(key)
        query.setValue(true, forKey = kSecReturnData as String)
        query.setValue(kSecMatchLimitOne, forKey = kSecMatchLimit as String)

        return memScoped {
            val resultRef = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, resultRef.ptr)
            if (status == errSecSuccess) {
                @Suppress("UNCHECKED_CAST")
                (resultRef.value as? NSData)?.toKotlinString()
            } else {
                null
            }
        }
    }

    private fun deleteItem(key: String) {
        SecItemDelete(buildBaseQuery(key))
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
