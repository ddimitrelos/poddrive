package com.poddrive.storage

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform-specific SQLDelight SqlDriver.
 * Android actual: AndroidSqliteDriver (SQLite-backed via Android SQLite API)
 * iOS actual: NativeSqliteDriver (SQLite-backed via Kotlin/Native SQLite)
 *
 * Usage: inject DatabaseDriverFactory into repository implementations.
 * The generated PodDrive database class accepts a SqlDriver from this factory.
 * [Source: architecture.md#Data Architecture, AR3, AR4]
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
