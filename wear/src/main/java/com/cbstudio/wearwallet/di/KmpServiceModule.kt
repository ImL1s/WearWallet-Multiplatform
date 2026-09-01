package com.cbstudio.wearwallet.di

import org.koin.dsl.module
import org.koin.core.module.Module
/**
 * KMP 服務模組 - 暫時空實現
 * 
 * 注意：該模組當前為空，因為直接 KMP 服務整合需要：
 * 1. 正確的 Ktor 依賴配置
 * 2. HttpClient 平台實現
 * 3. 服務接口適配
 * 
 * 建議：使用現有的適配器模式，通過 RepositoryModule 提供服務
 * 
 * @author ULTRATHINK Phase 11
 * @since 2025-01-17
 */
val kmpServiceModule = module {
    // 暫時移除直接 KMP 服務注入
    // 改用適配器模式通過 RepositoryModule 提供
}
