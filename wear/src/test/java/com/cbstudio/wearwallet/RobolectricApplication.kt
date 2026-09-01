package com.cbstudio.wearwallet

import android.app.Application

class RobolectricApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Do nothing. Prevent real App init.
    }
}
