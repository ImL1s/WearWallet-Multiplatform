package com.cbstudio.wearwallet.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wear OS Database 模組 - 完全使用 KMP SQLDelight
 * 
 * 核心原則：
 * 1. 不再使用 Room Database，完全依賴 KMP SQLDelight
 * 2. 直接從 sharedKmp 模組獲取資料庫服務
 * 3. 使用 Koin 依賴注入管理 KMP 資料庫
 * 4. 所有資料庫相關依賴由 sharedKmp 的 coreModule 提供
 */
val databaseModule: Module = module {
    // 空模組 - 所有資料庫服務由 KMP coreModule 提供
    // KMP SQLDelight 實例會自動注入到需要的地方
    
    // 已移除的 Room 相關依賴：
    // - WalletDatabase
    // - WalletDao, TransactionDao, CustomTokenDao, etc.
    // - DatabaseHealthChecker
    // - Room Converters
    
    // 新的 KMP SQLDelight 架構中，以下服務可直接注入：
    // - WalletDatabase (SQLDelight)
    // - 各種 Queries 物件
    // - 資料庫驅動程式
}
