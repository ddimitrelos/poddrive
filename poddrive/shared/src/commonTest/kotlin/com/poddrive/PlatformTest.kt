package com.poddrive

import kotlin.test.Test
import kotlin.test.assertNotNull

class PlatformTest {
    @Test
    fun platformNotNull() {
        assertNotNull(platform())
    }
}
