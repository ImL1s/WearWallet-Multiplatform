package com.cbstudio.wearwallet.di

import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import com.cbstudio.wearwallet.defi.DeFiStrategyManager

/**
 * DeFi 相關依賴注入模組
 * 
 * 提供 DeFi 策略管理器和相關服務
 */
val deFiModule = module {
    
    // DeFi 策略管理器
    single { 
        DeFiStrategyManager()
    }
    
    // 注意：其他 DeFi 服務已經遷移到 sharedKmp 模組
    // 透過 KMP 的 Koin 配置直接使用
}