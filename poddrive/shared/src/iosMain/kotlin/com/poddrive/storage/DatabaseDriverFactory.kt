package com.poddrive.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.poddrive.db.PodDrive

/**
 * iOS implementation: creates a NativeSqliteDriver backed by SQLite on Kotlin/Native.
 * The PodDrive.Schema (SQLDelight-generated) handles CREATE TABLE / migrations.
 * Database file name: "poddrive.db"
 * [Source: architecture.md#Data Architecture, AR3]
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(PodDrive.Schema, DB_NAME)

    private companion object {
        const val DB_NAME = "poddrive.db"
    }
}
