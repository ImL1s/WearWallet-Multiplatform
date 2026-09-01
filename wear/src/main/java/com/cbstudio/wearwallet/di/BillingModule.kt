package com.cbstudio.wearwallet.di

import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import android.content.Context
import com.cbstudio.wearwallet.data.service.BillingServiceImpl
import com.cbstudio.wearwallet.services.FirebaseService

/**
 * Billing 服務依賴注入模組
 */
val billingModule = module {
    
    single { 
        BillingServiceImpl(
            androidContext(),
            get<FirebaseService>()
        )
    }
}