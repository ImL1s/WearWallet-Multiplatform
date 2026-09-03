package com.cbstudio.mobile

import android.app.Application
import com.cbstudio.mobile.BuildConfig
import com.cbstudio.mobile.di.KoinInitializer
import timber.log.Timber

class MobileApplication : Application() {
    companion object {
        init {
            try {
                System.loadLibrary("TrustWalletCore")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "MobileApplication: 載入 TrustWalletCore 失敗")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber FIRST for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
//            Timber.plant(CrashReportingTree())
        }
        
        // Initialize Koin for KMP modules BEFORE any usage
        // This is critical because Bridge classes depend on Koin being initialized
        Timber.d("MobileApplication: 開始初始化 Koin")
        KoinInitializer.getInstance().initialize(this)
        Timber.d("MobileApplication: Koin 初始化完成")
        
        Timber.d("MobileApplication onCreate called")
    }
}