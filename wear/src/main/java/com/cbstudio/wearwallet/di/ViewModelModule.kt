package com.cbstudio.wearwallet.di

import com.cbstudio.wearwallet.presentation.wallet.screens.swap.SwapViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import android.content.ClipboardManager
import android.content.Context
// import com.cbstudio.wearwallet.presentation.keystone.ConnectKeystoneWalletViewModelV2 // 已刪除
import com.cbstudio.wearwallet.presentation.complication.ComplicationUpdateHelper
// import com.cbstudio.wearwallet.presentation.debug.DatabaseDebugViewModel // MAINTENANCE MODE

/**
 * Wear OS ViewModel 模組 - KMP 架構實現
 * 
 * 核心原則：
 * 1. ViewModel 在 wear 模組處理 UI 狀態
 * 2. UseCase 在 coreKmp 模組處理業務邏輯
 * 3. 使用統一 Koin DI 配置
 */
val viewModelModule: Module = module {
    
    // === Android 系統服務 ===
    factory { androidContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    
    // === Wear OS 特定服務 ===
    single { ComplicationUpdateHelper(androidContext()) }

    // === Helpers ===
    single<com.cbstudio.wearwallet.presentation.wallet.utils.QRCodeGenerator> { 
        com.cbstudio.wearwallet.presentation.wallet.utils.AndroidQRCodeGenerator() 
    }
    factory { com.cbstudio.wearwallet.core.multichain.sdk.AddressDerivation() }
    
    // UTXO API Client
    factory { com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient() }

    // === KMP 架構 ViewModels - wear 模組 ===
    // 這些 ViewModel 使用 KoinComponent 注入 coreKmp 的 UseCase
    
    // 地址簿 ViewModel
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.addressbook.AddressBookViewModel() }
    
    // NFT ViewModel  
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.nft.NftViewModelKmp() }
    
    // 價格提醒 ViewModel
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.pricealert.PriceAlertViewModelKmp() }
    
    // 通知管理 ViewModel
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.notification.NotificationViewModelKmp() }
    
    // Keystone 硬體錢包 ViewModel - 整合 coreKmp
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet.ConnectKeystoneWalletViewModelV2(get()) }
    
    // Keystone 發送 ViewModel
    factory { (unsignedTx: String) -> com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.KeystoneSendViewModel(unsignedTx) }
    
    // Bitcoin 錢包 ViewModel - UTXO 鏈支援
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.bitcoin.BitcoinWalletViewModel() }

    // Token 列表 ViewModel
    factory { com.cbstudio.wearwallet.presentation.ui.token.TokenListViewModel() }

    // 新增自訂代幣 ViewModel
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.main.token.AddCustomTokenViewModel(get(), get()) }

    // Receive ViewModel
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.ReceiveViewModel() }
    
    // Send ViewModel
    factory { com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.SendTransactionViewModel() }

    // Swap ViewModel
    viewModel { SwapViewModel() }

    // Wallet ViewModel
    viewModel { com.cbstudio.wearwallet.presentation.wallet.WalletViewModel(get(), get()) }

    // === 現有 ViewModel ===
    // WalletMainViewModel 已經在使用 KMP 架構模式
    // 其他 ViewModel 將逐步遷移到 KMP 架構
}