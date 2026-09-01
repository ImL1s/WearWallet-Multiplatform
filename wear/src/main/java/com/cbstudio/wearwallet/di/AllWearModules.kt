package com.cbstudio.wearwallet.di

import org.koin.core.module.Module
import com.cbstudio.wearwallet.core.di.getAllCoreModules

/**
 * 獲取所有 Wear 模組 - KMP 架構實現
 * 
 * 包含基於 KMP 架構的必要模組：
 * - ViewModel 使用 KoinComponent 注入 coreKmp UseCase
 * - 統一 Koin DI 配置，不使用 Hilt
 * - Wear OS 特定服務和組件
 */
fun getAllWearModules(): List<Module> {
    return listOf(
        // KMP 架構 ViewModel 模組
        viewModelModule,

        // Wear OS 特定服務模組（ULTRATHINK Phase 修復）
        wearKoinModule
        
        // Security logic is handled by platformProviderModule in coreKMP
        // securityModule
    ) + getAllCoreModules()
}