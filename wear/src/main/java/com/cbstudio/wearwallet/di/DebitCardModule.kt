package com.cbstudio.wearwallet.di

import org.koin.dsl.module
import org.koin.core.module.Module
import com.cbstudio.wearwallet.domain.service.CryptoToFiatConversionService
/**
 * 加密貨幣借記卡功能的 Hilt 模組
 * 提供借記卡相關的服務和依賴
 */
val debitCardModule = module {
    
    /**
     * 提供加密貨幣到法幣轉換服務
     */
    single { CryptoToFiatConversionService()
      }
    
    // CryptoService, NfcTransferManager, CryptoDebitCardService 都已經通過 constructor 自動提供
}
