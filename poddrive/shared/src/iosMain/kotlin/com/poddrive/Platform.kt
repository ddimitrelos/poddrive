package com.poddrive

import platform.Foundation.NSDate

actual fun platform(): String = "iOS"

actual fun currentTimeMillis(): Long =
    (NSDate.date().timeIntervalSince1970 * 1000).toLong()
