package com.cbstudio.wearwallet.di

import android.content.Context
import com.cbstudio.wearwallet.core.di.getAllCoreModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import timber.log.Timber

/**
 * 初始化 CoreKmp 的 Koin 配置
 */
fun Context.initializeCoreKoin() {
    // 檢查是否已經初始化
    if (GlobalContext.getOrNull() != null) {
        Timber.d("Koin already initialized, skipping core init")
        return
    }
    
    Timber.d("Initializing CoreKmp Koin configuration")
    
    try {
        startKoin {
            androidContext(this@initializeCoreKoin)
            modules(getAllCoreModules())
        }
        
        Timber.d("CoreKmp Koin initialized successfully")
    } catch (e: Exception) {
        Timber.e(e, "Failed to initialize CoreKmp Koin")
        throw e
    }
}