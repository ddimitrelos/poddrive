package com.poddrive

actual fun platform(): String = "Android"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
