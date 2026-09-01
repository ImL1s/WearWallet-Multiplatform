package com.cbstudio.wearwallet.analytics

import org.koin.dsl.module

/**
 * Koin 模組，提供分析相關的依賴注入
 * 已從 Hilt 遷移至 Koin
 */
val analyticsModule = module {
    
    single<AnalyticsManager> { 
        AnalyticsManager()
    }
    
    single<SubscriptionAnalytics> { 
        SubscriptionAnalytics(get())
    }
    
    single<TransactionAnalytics> { 
        TransactionAnalytics(get())
    }
    
    single<PerformanceMonitor> { 
        PerformanceMonitor(get())
    }
}
