package com.poddrive

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun platformReturnsNonEmptyString() {
        assertTrue(platform().isNotEmpty(), "platform() must return a non-empty string")
    }
}
