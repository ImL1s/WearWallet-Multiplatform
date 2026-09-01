package com.cbstudio.wearwallet.di

import android.content.Context
import com.cbstudio.wearwallet.core.di.getAllCoreModules
import com.cbstudio.wearwallet.core.domain.usecase.token.ScanTokensUseCase
import com.cbstudio.wearwallet.core.domain.usecase.token.GetUserTokensUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.AddAddressContactUseCase
import com.cbstudio.wearwallet.presentation.wallet.screens.main.WalletMainViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.SendTransactionViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.ReceiveViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.token.TokenSelectorViewModel
import com.cbstudio.wearwallet.presentation.screens.ai.AIAssistantViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.history.TransactionHistoryViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.chain.ChainSelectorViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.WalletManagementViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.chain.ChainSelectorViewModel as SettingsChainSelectorViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.ShowMnemonicViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet.ConnectKeystoneWalletViewModelV2
import com.cbstudio.wearwallet.presentation.qrscanner.QrScannerViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.addressbook.AddContactViewModel
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.SwapViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber
import com.cbstudio.wearwallet.core.di.androidNetworkModule

/**
 * Wear OS Koin 模組 - 連接到 coreKmp
 * 
 * 提供所有必要的 ViewModels 和 UseCases
 */
val wearModule = module {
    // ViewModels - 自動從 coreKmp 獲取依賴
    viewModel { WalletMainViewModel() }
    // SendTransactionViewModel moved to ViewModelModule
    // ReceiveViewModel moved to ViewModelModule
    viewModel { TokenSelectorViewModel() }
    viewModel { AIAssistantViewModel(get()) }
    viewModel { TransactionHistoryViewModel() }
    viewModel { ChainSelectorViewModel() }
    viewModel { SettingsChainSelectorViewModel() }
    viewModel { WalletManagementViewModel() }
    viewModel { ShowMnemonicViewModel() }
    // ConnectKeystoneWalletViewModelV2 moved to ViewModelModule
    viewModel { QrScannerViewModel(get()) }
    viewModel { AddContactViewModel() }
    // SwapViewModel moved to ViewModelModule
    
    // 額外的 UseCases
    single { ScanTokensUseCase(get()) }
    single { GetUserTokensUseCase(get()) }
    single { GetTransactionHistoryUseCase(get()) }
    single { AddAddressContactUseCase(get()) }
}

/**
 * 初始化 Koin - 連接到 coreKmp
 */
fun Context.initializeKoin() {
    // 檢查是否已經初始化
    if (GlobalContext.getOrNull() != null) {
        Timber.d("Koin already initialized, skipping init")
        return
    }
    
    Timber.d("Initializing Koin with coreKmp modules")
    
    try {
        startKoin {
            androidContext(this@initializeKoin)
            // 載入 coreKmp 模組 (包含在 getAllWearModules 中) + wear 特定模組 + Explicit network module
            modules(wearModule + getAllWearModules() + androidNetworkModule)
        }

        Timber.d("Koin initialized successfully with coreKmp and wear modules")
    } catch (e: Exception) {
        Timber.e(e, "Failed to initialize Koin")
        throw e
    }
}

/**
 * 為了向後相容，保留舊的函數名稱
 */
fun Context.initializeMinimalKoin() = initializeKoin()