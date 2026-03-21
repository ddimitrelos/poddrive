package com.poddrive.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.runner.RunWith

/**
 * Instrumented tests for Android SecureStorage (AC8).
 * Must run on a real device or emulator — EncryptedSharedPreferences requires the Android Keystore.
 * CI: android-ci.yml on ubuntu-latest with an emulator task.
 */
@RunWith(AndroidJUnit4::class)
class SecureStorageInstrumentedTest {

    private lateinit var secureStorage: SecureStorage

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        secureStorage = SecureStorage(context)
        secureStorage.clear()
    }

    @After
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
