package com.cbstudio.wearwallet

import android.app.Application
import com.cbstudio.wearwallet.core.network.ApiConfig
import com.cbstudio.wearwallet.core.security.ApiKeyManager
import com.cbstudio.wearwallet.core.security.BuildConfigApiKeyProvider
import com.cbstudio.wearwallet.di.initializeKoin
import com.cbstudio.wearwallet.utils.CrashReportingTree
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import org.koin.core.context.GlobalContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryCoordinator
import com.cbstudio.wearwallet.core.recovery.StartupRecoveryState
import com.cbstudio.wearwallet.core.common.Result as CoreResult
/**
 * ULTRATHINK Phase 22 - TrustWallet Core 統一管理
 * 
 * 改進的 TrustWallet Core 初始化和錯誤處理
 * 保持與 KMP 架構的一致性
 */
class WalletApplication : Application() {
    private val applicationScope = CoroutineScope(Dispatchers.IO)
    private val firebaseEnabled = !BuildConfig.PUBLIC_SNAPSHOT
    
    companion object {
        @Volatile
        private var isTrustWalletCoreLoaded = false
        private var trustWalletCoreException: UnsatisfiedLinkError? = null
        
        init {
            // ULTRATHINK Phase 22: 改進的 TrustWallet Core 載入邏輯
            try {
                System.loadLibrary("TrustWalletCore")
                isTrustWalletCoreLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                // Graceful degradation: Allow app to start without TrustWallet Core
                isTrustWalletCoreLoaded = false
                trustWalletCoreException = e
            } catch (e: Exception) {
                // Any other loading error - handle gracefully
                isTrustWalletCoreLoaded = false
                // Convert to UnsatisfiedLinkError for consistent error handling
                trustWalletCoreException = UnsatisfiedLinkError("TrustWallet Core 初始化失敗: ${e.message}")
            }
        }
        
        /**
         * 檢查 TrustWallet Core 是否可用
         */
        fun isTrustWalletCoreAvailable(): Boolean = isTrustWalletCoreLoaded
        
        /**
         * 獲取 TrustWallet Core 載入錯誤
         */
        fun getTrustWalletCoreError(): UnsatisfiedLinkError? = trustWalletCoreException
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber FIRST for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            if (firebaseEnabled) {
                Firebase.crashlytics.setCrashlyticsCollectionEnabled(false)
            }
        } else {
            Timber.plant(if (firebaseEnabled) CrashReportingTree() else Timber.DebugTree())
            if (firebaseEnabled) {
                Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)
            }
        }

        if (firebaseEnabled) {
            com.cbstudio.wearwallet.firebase.AppCheckInitializer.initialize(this)
        }
        
        // Initialize API Keys
        initializeApiKeys()
        
        // Initialize Koin with KMP architecture
        Timber.d("WalletApplication: 初始化 KMP 架構 Koin 配置")
        try {
            // 初始化 Koin 並連接到 coreKmp
            initializeKoin()
            Timber.d("WalletApplication: Koin 初始化成功並連接到 coreKmp")
            
            Timber.i("WalletApplication: KMP 架構 Koin 配置完成")
        } catch (e: Exception) {
            Timber.e(e, "WalletApplication: KMP 架構 Koin 初始化失敗")
            if (firebaseEnabled) {
                Firebase.crashlytics.recordException(e)
            }
        }
        
        // Set up global exception handler
        setupGlobalExceptionHandler()
        
        // 檢查 TrustWallet Core 狀態
        checkTrustWalletCoreStatus()
        
        // 驗證 KMP 服務可用性
        validateKmpServices()
        
        // 觸發啟動狀態對帳機制
        triggerStartupReconciliation()
        
        Timber.i("WalletApplication: ULTRATHINK Phase 19+ 維護模式架構啟動完成")
    }
    
    /**
     * 檢查 TrustWallet Core 狀態 - ULTRATHINK Phase 22 改進版
     */
    private fun checkTrustWalletCoreStatus() {
        val isAvailable = isTrustWalletCoreAvailable()
        val error = getTrustWalletCoreError()

        if (isAvailable) {
            Timber.i("WalletApplication: TrustWallet Core 原生庫載入成功")
            if (firebaseEnabled) {
                Firebase.crashlytics.log("TrustWallet Core: Available")
                Firebase.crashlytics.setCustomKey("trust_wallet_core_available", true)
                Firebase.crashlytics.setCustomKey("trust_wallet_core_error", "none")
            }
        } else {
            Timber.w("WalletApplication: TrustWallet Core 原生庫不可用 - 維護模式運行")
            Timber.i("WalletApplication: 錢包核心功能將在維護模式下運行")

            if (error != null) {
                Timber.w("WalletApplication: TrustWallet Core 錯誤: ${error.message}")
                if (firebaseEnabled) {
                    Firebase.crashlytics.recordException(error)
                    Firebase.crashlytics.setCustomKey("trust_wallet_core_error", error.message ?: "未知錯誤")
                }
            }

            if (firebaseEnabled) {
                Firebase.crashlytics.log("TrustWallet Core: Unavailable - Running in maintenance mode")
                Firebase.crashlytics.setCustomKey("trust_wallet_core_available", false)
            }
        }
    }
    
    /**
     * 設置全域異常處理器
     */
    private fun setupGlobalExceptionHandler() {
        if (!firebaseEnabled) return
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Firebase.crashlytics.log("FATAL: Uncaught exception on thread ${thread.name}")
                Firebase.crashlytics.recordException(throwable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    /**
     * 初始化 API Keys
     * ✅ API Keys 現在由 BuildKonfig 在編譯時從 local.properties 注入到 coreKmp
     * 這個方法只負責初始化 ApiKeyManager 和驗證金鑰是否配置
     */
    private fun initializeApiKeys() {
        try {
            Timber.d("WalletApplication: API Keys 初始化開始 (由 BuildKonfig 提供)")

            // 初始化 ApiKeyManager（供 coreKmp 模組使用）
            applicationScope.launch {
                try {
                    ApiKeyManager.initialize(BuildConfigApiKeyProvider())
                    Timber.d("WalletApplication: ApiKeyManager 初始化完成")
                } catch (e: Exception) {
                    Timber.e(e, "WalletApplication: ApiKeyManager 初始化失敗")
                }
            }

            // 驗證 API Keys 是否正確載入（檢查是否為空）
            val missingKeys = ApiConfig.validateRequiredKeys()
            if (missingKeys.isEmpty()) {
                Timber.d("WalletApplication: 所有必要的 API Keys 已成功載入")
            } else {
                Timber.w("WalletApplication: 缺少 API Keys: ${missingKeys.joinToString()}")
                Timber.w("WalletApplication: 請檢查 local.properties 是否正確配置")
            }

            // 詳細日誌
            if (ApiConfig.infuraApiKey.isNotEmpty()) {
                Timber.d("WalletApplication: Infura API Key 已載入")
            }
            if (ApiConfig.etherscanApiKey.isNotEmpty()) {
                Timber.d("WalletApplication: Etherscan API Key 已載入")
            }
            if (ApiConfig.moralisApiKey.isNotEmpty()) {
                Timber.d("WalletApplication: Moralis API Key 已載入")
            }

            Timber.d("WalletApplication: API Keys 初始化完成")
        } catch (e: Exception) {
            Timber.e(e, "WalletApplication: API Keys 初始化失敗")
        }
    }
    
    /**
     * 驗證 KMP 服務可用性
     */
    private fun validateKmpServices() {
        applicationScope.launch {
            try {
                Timber.d("WalletApplication: 開始驗證 KMP 服務")
                
                val koin = GlobalContext.get()
                // 臨時停用服務檢查，等待 KMP 完全整合
                val servicesStatus = emptyMap<String, Boolean>()
                
                val availableServices = servicesStatus.filter { it.value }.keys
                val unavailableServices = servicesStatus.filter { !it.value }.keys
                
                Timber.i("WalletApplication: KMP 服務檢查暫停中")
                if (unavailableServices.isNotEmpty()) {
                    Timber.w("WalletApplication: 不可用服務: $unavailableServices")
                }
                
                // 記錄到 Crashlytics 用於監控
                if (firebaseEnabled) {
                    Firebase.crashlytics.log("KMP Services Status: Available=${availableServices.size}, Unavailable=${unavailableServices.size}")
                }

            } catch (e: Exception) {
                Timber.e(e, "WalletApplication: KMP 服務驗證失敗")
                if (firebaseEnabled) {
                    Firebase.crashlytics.recordException(e)
                }
            }
        }
    }
    
    /**
     * 觸發啟動狀態對帳機制 (Startup State Reconciliation)
     * 由 StartupRecoveryCoordinator 協調 Staging 孤兒金鑰與墓碑記錄對帳
     */
    private fun triggerStartupReconciliation() {
        applicationScope.launch {
            try {
                Timber.i("WalletApplication: 開始由 StartupRecoveryCoordinator 協調啟動對帳")
                val koin = GlobalContext.getOrNull()
                val coordinator = koin?.getOrNull<StartupRecoveryCoordinator>()
                if (coordinator != null) {
                    val state = coordinator.startReconciliation()
                    when (state) {
                        is StartupRecoveryState.Ready -> {
                            Timber.i("WalletApplication: 啟動狀態對帳成功完成 (Ready)")
                            if (firebaseEnabled) {
                                Firebase.crashlytics.log("StartupRecovery: Ready")
                            }
                        }
                        is StartupRecoveryState.Failed -> {
                            Timber.e(state.error, "WalletApplication: 啟動狀態對帳失敗: ${state.message}")
                            if (firebaseEnabled) {
                                Firebase.crashlytics.recordException(state.error)
                                Firebase.crashlytics.log("StartupRecovery: Failed - ${state.message}")
                            }
                        }
                        is StartupRecoveryState.RecoveryRequired -> {
                            Timber.w("WalletApplication: 啟動狀態需要恢復: ${state.reason}")
                            if (firebaseEnabled) {
                                Firebase.crashlytics.log("StartupRecovery: RecoveryRequired - ${state.reason}")
                            }
                        }
                        else -> Unit
                    }
                } else {
                    Timber.w("WalletApplication: Koin 未提供 StartupRecoveryCoordinator，跳過啟動狀態對帳")
                }
            } catch (e: Exception) {
                Timber.e(e, "WalletApplication: 啟動狀態對帳發生未處理異常")
                if (firebaseEnabled) {
                    Firebase.crashlytics.recordException(e)
                }
            }
        }
    }
    
    /**
     * 檢查特定服務是否可用
     */
    private inline fun <reified T : Any> checkService(koin: org.koin.core.Koin): Boolean {
        return try {
            koin.getOrNull<T>() != null
        } catch (e: Exception) {
            Timber.w("WalletApplication: 服務 ${T::class.simpleName} 不可用: ${e.message}")
            false
        }
    }
}
