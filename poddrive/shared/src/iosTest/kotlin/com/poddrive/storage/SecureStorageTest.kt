package com.poddrive.storage

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * iOS platform tests for SecureStorage (AC8).
 * Exercises the Keychain (Security framework) read/write/delete round-trips.
 * CI: ios-ci.yml on macos-latest with iosSimulatorArm64 target.
 */
class SecureStorageTest {

    private val secureStorage = SecureStorage()

    @BeforeTest
    fun setUp() {
        secureStorage.clear()
    }

    @AfterTest
    fun tearDown() {
        secureStorage.clear()
    }

    @Test
    fun saveAndReadTokensRoundTrip() {
        secureStorage.saveTokens("access_abc", "refresh_xyz")
        assertEquals("access_abc", secureStorage.getAccessToken())
        assertEquals("refresh_xyz", secureStorage.getRefreshToken())
    }

    @Test
    fun clearRemovesAllTokens() {
        secureStorage.saveTokens("access_abc", "refresh_xyz")
        secureStorage.clear()
        assertNull(secureStorage.getAccessToken())
        assertNull(secureStorage.getRefreshToken())
    }

    @Test
    fun overwriteTokensWithNewValues() {
        secureStorage.saveTokens("old_access", "old_refresh")
        secureStorage.saveTokens("new_access", "new_refresh")
        assertEquals("new_access", secureStorage.getAccessToken())
        assertEquals("new_refresh", secureStorage.getRefreshToken())
    }

    @Test
    fun getTokensReturnsNullBeforeAnySave() {
        assertNull(secureStorage.getAccessToken())
        assertNull(secureStorage.getRefreshToken())
    }
}
