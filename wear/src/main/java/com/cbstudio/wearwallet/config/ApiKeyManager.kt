package com.cbstudio.wearwallet.config

import android.content.Context
import com.cbstudio.wearwallet.BuildConfig
import com.cbstudio.wearwallet.services.FirebaseService
import com.cbstudio.wearwallet.R
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import timber.log.Timber
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton

/**
 * API 密鑰管理器
 * 集中管理所有外部服務的 API 密鑰
 * 
 * 優先級：
 * 1. BuildConfig (用於開發環境)
 * 2. Firebase Remote Config (用於生產環境)
 * 3. 環境變數 (用於 CI/CD)
 * 4. 預設值 (用於測試)
 */

class ApiKeyManager constructor(
    private val context: Context,
    private val firebaseService: FirebaseService,
    private val remoteConfig: FirebaseRemoteConfig
) {
    
    companion object {
        // Remote Config 鍵名
        private const val INFURA_PROJECT_ID_KEY = "infura_project_id"
        private const val ETHERSCAN_API_KEY_KEY = "etherscan_api_key"
        private const val COINGECKO_API_KEY_KEY = "coingecko_api_key"
        private const val MORALIS_API_KEY_KEY = "moralis_api_key"
        private const val CHAINLINK_NODE_URL_KEY = "chainlink_node_url"
        private const val PUSH_PROTOCOL_CHANNEL_KEY = "push_protocol_channel"
        
        // 環境變數名稱
        private const val ENV_INFURA_PROJECT_ID = "INFURA_PROJECT_ID"
        private const val ENV_ETHERSCAN_API_KEY = "ETHERSCAN_API_KEY"
        private const val ENV_COINGECKO_API_KEY = "COINGECKO_API_KEY"
        private const val ENV_MORALIS_API_KEY = "MORALIS_API_KEY"
        
        // 預設測試值
        private const val DEFAULT_TEST_API_KEY = "test_api_key"
    }
    
    /**
     * 獲取 Infura Project ID
     */
    fun getInfuraProjectId(): String {
        return getApiKey(
            keyName = INFURA_PROJECT_ID_KEY,
            envName = ENV_INFURA_PROJECT_ID,
            buildConfigValue = BuildConfig.INFURA_PROJECT_ID.takeIf { 
                it != "YOUR_INFURA_PROJECT_ID" && it.isNotBlank() 
            }
        )
    }
    
    /**
     * 獲取 Etherscan API Key
     */
    fun getEtherscanApiKey(): String {
        return getApiKey(
            keyName = ETHERSCAN_API_KEY_KEY,
            envName = ENV_ETHERSCAN_API_KEY,
            buildConfigValue = BuildConfig.ETHERSCAN_API_KEY.takeIf { 
                it != "YOUR_ETHERSCAN_API_KEY" && it.isNotBlank() 
            }
        )
    }
    
    /**
     * 獲取 CoinGecko API Key
     */
    fun getCoinGeckoApiKey(): String? {
        return getApiKey(
            keyName = COINGECKO_API_KEY_KEY,
            envName = ENV_COINGECKO_API_KEY,
            buildConfigValue = null,
            required = false
        ).takeIf { it.isNotBlank() && it != DEFAULT_TEST_API_KEY }
    }
    
    /**
     * 獲取 Moralis API Key
     */
    fun getMoralisApiKey(): String {
        return getApiKey(
            keyName = MORALIS_API_KEY_KEY,
            envName = ENV_MORALIS_API_KEY,
            buildConfigValue = BuildConfig.MORALIS_API_KEY.takeIf { 
                it != "YOUR_MORALIS_API_KEY" && it.isNotBlank() 
            }
        )
    }
    
    /**
     * 獲取 Push Protocol Channel Address
     */
    fun getPushProtocolChannel(): String? {
        val channel = remoteConfig.getString(PUSH_PROTOCOL_CHANNEL_KEY)
        return channel.takeIf { it.isNotBlank() }
    }
    
    /**
     * 獲取 Chainlink Node URL
     */
    fun getChainlinkNodeUrl(): String? {
        val url = remoteConfig.getString(CHAINLINK_NODE_URL_KEY)
        return url.takeIf { it.isNotBlank() }
    }
    
    /**
     * 獲取其他區塊鏈瀏覽器 API Keys
     */
    fun getBscScanApiKey(): String = getApiKey("bsc_scan_api_key", "BSC_SCAN_API_KEY", required = false)
    fun getPolygonScanApiKey(): String = getApiKey("polygon_scan_api_key", "POLYGON_SCAN_API_KEY", required = false)
    fun getSnowtraceApiKey(): String = getApiKey("snowtrace_api_key", "SNOWTRACE_API_KEY", required = false)
    fun getFtmScanApiKey(): String = getApiKey("ftm_scan_api_key", "FTM_SCAN_API_KEY", required = false)
    fun getCeloScanApiKey(): String = getApiKey("celo_scan_api_key", "CELO_SCAN_API_KEY", required = false)
    fun getHarmonyScanApiKey(): String = getApiKey("harmony_scan_api_key", "HARMONY_SCAN_API_KEY", required = false)
    fun getCronoscanApiKey(): String = getApiKey("cronoscan_api_key", "CRONOSCAN_API_KEY", required = false)
    fun getKlaytnScopeApiKey(): String = getApiKey("klaytn_scope_api_key", "KLAYTN_SCOPE_API_KEY", required = false)
    fun getMoonbeamApiKey(): String = getApiKey("moonbeam_api_key", "MOONBEAM_API_KEY", required = false)
    fun getMoonriverApiKey(): String = getApiKey("moonriver_api_key", "MOONRIVER_API_KEY", required = false)
    fun getGnosisScanApiKey(): String = getApiKey("gnosis_scan_api_key", "GNOSIS_SCAN_API_KEY", required = false)
    fun getOptimisticEtherscanApiKey(): String = getApiKey("optimistic_etherscan_api_key", "OPTIMISTIC_ETHERSCAN_API_KEY", required = false)
    fun getArbiscanApiKey(): String = getApiKey("arbiscan_api_key", "ARBISCAN_API_KEY", required = false)
    fun getBttcScanApiKey(): String = getApiKey("bttc_scan_api_key", "BTTC_SCAN_API_KEY", required = false)
    
    /**
     * 通用 API 密鑰獲取方法
     */
    private fun getApiKey(
        keyName: String,
        envName: String,
        buildConfigValue: String? = null,
        required: Boolean = true
    ): String {
        // 1. 檢查 BuildConfig (開發環境)
        buildConfigValue?.let {
            Timber.d("使用 BuildConfig 中的 $keyName")
            return it
        }
        
        // 2. 檢查 Remote Config (生產環境)
        val remoteValue = remoteConfig.getString(keyName)
        if (remoteValue.isNotBlank()) {
            Timber.d("使用 Remote Config 中的 $keyName")
            return remoteValue
        }
        
        // 3. 檢查環境變數 (CI/CD)
        val envValue = System.getenv(envName)
        if (!envValue.isNullOrBlank()) {
            Timber.d("使用環境變數中的 $keyName")
            return envValue
        }
        
        // 4. 使用預設值或拋出異常
        if (required) {
            val message = context.getString(R.string.error_api_key_instructions, keyName, envName)
            val logMessage = context.getString(R.string.error_missing_api_key, keyName)
            
            if (BuildConfig.DEBUG) {
                Timber.w(logMessage)
                Timber.w(message)
                Timber.w("開發環境使用測試密鑰")
                return DEFAULT_TEST_API_KEY
            } else {
                firebaseService.reportCrashlytics(
                    IllegalStateException("Missing API key: $keyName"),
                    logMessage + "\n" + message
                )
                throw IllegalStateException(logMessage + "\n" + message)
            }
        }
        
        return ""
    }
    
    /**
     * 檢查所有必需的 API 密鑰是否已配置
     */
    fun validateApiKeys(): Boolean {
        return try {
            val infura = getInfuraProjectId()
            val etherscan = getEtherscanApiKey()
            val moralis = getMoralisApiKey()
            
            val allValid = listOf(infura, etherscan, moralis).all { 
                it.isNotBlank() && it != DEFAULT_TEST_API_KEY 
            }
            
            if (!allValid) {
                Timber.w("某些 API 密鑰使用測試值，功能可能受限")
            }
            
            true
        } catch (e: Exception) {
            Timber.e(e, "API 密鑰驗證失敗")
            false
        }
    }
    
    /**
     * 記錄 API 密鑰狀態（用於調試）
     */
    fun logApiKeyStatus() {
        if (BuildConfig.DEBUG) {
            Timber.d("""
                API 密鑰狀態：
                - Infura: ${getInfuraProjectId().take(8)}...
                - Etherscan: ${getEtherscanApiKey().take(8)}...
                - CoinGecko: ${getCoinGeckoApiKey()?.take(8) ?: "未設置"}...
                - Moralis: ${getMoralisApiKey().take(8)}...
                - Push Protocol: ${getPushProtocolChannel()?.take(8) ?: "未設置"}...
                - Chainlink: ${getChainlinkNodeUrl()?.take(20) ?: "未設置"}...
            """.trimIndent())
        }
    }
}
