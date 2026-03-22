package com.poddrive

expect fun platform(): String

/** Returns current time as Unix milliseconds. Used in use cases to record sync timestamps. */
expect fun currentTimeMillis(): Long
