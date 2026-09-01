package com.cbstudio.wearwallet.di

import org.koin.dsl.module

/**
 * ULTRATHINK Phase 12 - 廢棄的 Remote Config 模組
 * 
 * @deprecated 此模組已被 sharedKmp 架構替代
 * Remote Config 功能現在通過 sharedKmp 的統一服務提供
 */
@Deprecated(
    message = "Remote Config 模組已遷移到 sharedKmp",
    level = DeprecationLevel.WARNING
)
val remoteConfigModule = module {
    // 空模組 - 所有功能已遷移到 sharedKmp
}
