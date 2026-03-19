package com.poddrive.android

import android.app.Application

class PodDriveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Story 1.2+ will initialize dependency injection here
    }
}
