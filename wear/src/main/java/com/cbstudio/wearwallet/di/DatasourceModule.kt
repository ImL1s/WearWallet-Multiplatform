package com.cbstudio.wearwallet.di

import org.koin.dsl.module

/**
 * DataSource 模組已遷移到 sharedKmp
 * 所有數據源由 KMP 模組統一提供
 */
val datasourceModule = module {
    // 空模組 - 所有實現已遷移到 sharedKmp 模組
}

