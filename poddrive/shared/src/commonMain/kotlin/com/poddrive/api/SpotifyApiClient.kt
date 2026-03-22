package com.poddrive.api

import io.ktor.client.engine.HttpClientEngine

/**
 * Platform-specific Ktor engine provider.
 * Android actual: CIO
 * iOS actual: Darwin
 */
expect fun createPlatformEngine(): HttpClientEngine
