package com.poddrive.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.poddrive.db.PodDrive

/**
 * Android implementation: creates an AndroidSqliteDriver backed by SQLite.
 * The PodDrive.Schema (SQLDelight-generated) handles CREATE TABLE / migrations.
 * Database file name: "poddrive.db"
 * [Source: architecture.md#Data Architecture, AR3]
 */
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(PodDrive.Schema, context, DB_NAME)

    private companion object {
        const val DB_NAME = "poddrive.db"
    }
}
