package com.cbstudio.mobile.di

import android.content.Context
import com.cbstudio.wearwallet.core.di.getCoreModules
import com.cbstudio.wearwallet.core.di.getPlatformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.androidx.viewmodel.dsl.viewModel
// NFT imports
import com.cbstudio.mobile.presentation.nft.NftFavoritesViewModel
import com.cbstudio.mobile.presentation.nft.NftSearchViewModel
import com.cbstudio.wearwallet.core.domain.usecase.nft.NftFavoritesManager
import com.cbstudio.mobile.ui.nftwatchface.NftWatchFaceConfigurationViewModel
import com.cbstudio.mobile.ui.addressbook.*

/**
 * Mobile Koin 初始化器
 * 
 * 負責在 Hilt 應用中初始化 Koin，以便使用 KMP 的依賴注入
 * 這樣可以讓 Hilt 和 Koin 共存
 */
class KoinInitializer {
    
    companion object {
        @Volatile
        private var INSTANCE: KoinInitializer? = null
        
        fun getInstance(): KoinInitializer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KoinInitializer().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 初始化 Koin
     * 
     * @param context Android Context
     */
    fun initialize(context: Context) {
        // 檢查是否已經初始化，避免重複初始化
        if (GlobalContext.getOrNull() != null) {
            timber.log.Timber.d("KoinInitializer: Koin 已經初始化，跳過")
            return
        }
        
        timber.log.Timber.d("KoinInitializer: 開始初始化 Koin")
        
        try {
            startKoin {
                // Android Context
                androidContext(context)
                
                // 載入所有模組
                modules(
                    getCoreModules() +          // KMP 核心業務模組（包含所有必要的模組）
                    listOf(getPlatformModule()) + // Android 平台特定模組
                    listOf(mobileSpecificModule)  // Mobile 特定模組
                )
            }
            
            timber.log.Timber.d("KoinInitializer: Koin 初始化成功，模組數量: ${getCoreModules().size + 2}")
            
            // 驗證關鍵的依賴是否可以獲取
            try {
                val testRepo = GlobalContext.get().get<com.cbstudio.wearwallet.core.domain.repository.WalletRepository>()
                timber.log.Timber.d("KoinInitializer: 成功獲取 WalletRepository 實例: ${testRepo.hashCode()}")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "KoinInitializer: 無法獲取 WalletRepository，Koin 配置可能有問題")
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "KoinInitializer: Koin 初始化失敗")
            throw e
        }
    }
    
    /**
     * Mobile 特定的 Koin 模組
     */
    private val mobileSpecificModule = module {
        // NFT 相關
        single { NftFavoritesManager() }
        viewModel { NftFavoritesViewModel(get()) }
        viewModel { NftSearchViewModel(get(), get()) }
        
        // NFT WatchFace
        viewModel { NftWatchFaceConfigurationViewModel(get(), get()) }
        
        // AddressBook ViewModels
        viewModel { AddressBookViewModel(get()) }
        viewModel { AddContactViewModel(get(), get()) }
        viewModel { EditContactViewModel(get(), get(), get()) }
        viewModel { ContactDetailViewModel(get(), get(), get()) }
    }
}