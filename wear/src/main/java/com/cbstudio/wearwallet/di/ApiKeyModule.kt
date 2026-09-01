package com.cbstudio.wearwallet.di

import org.koin.dsl.module
import org.koin.core.qualifier.named
import com.cbstudio.wearwallet.config.ApiKeyManager
import com.cbstudio.wearwallet.services.FirebaseService
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

/**
 * API 密鑰依賴注入模組
 * 提供各種 API 密鑰的注入
 */
val apiKeyModule = module {
    
    single { 
        ApiKeyManager(get(), get<FirebaseService>(), get<FirebaseRemoteConfig>())
    }
    
    single<String>(named("infuraProjectId")) { 
        get<ApiKeyManager>().getInfuraProjectId()
    }
    
    single<String>(named("infuraApiKey")) { // 為了向後兼容
        get<ApiKeyManager>().getInfuraProjectId()
    }
    
    single<String>(named("etherscanApiKey")) { 
        get<ApiKeyManager>().getEtherscanApiKey()
    }
    
    single<String>(named("moralisApiKey")) { 
        get<ApiKeyManager>().getMoralisApiKey()
    }
    
    single<String?>(named("coingeckoApiKey")) {
        get<ApiKeyManager>().getCoinGeckoApiKey()
    }
    
    single<String>(named("binanceSmartChainApiKey")) {
        get<ApiKeyManager>().getBscScanApiKey()
    }
    
    single<String>(named("polygonApiKey")) {
        get<ApiKeyManager>().getPolygonScanApiKey()
    }
    
    single<String>(named("avalancheApiKey")) {
        get<ApiKeyManager>().getSnowtraceApiKey()
    }
    
    single<String>(named("fantomApiKey")) {
        get<ApiKeyManager>().getFtmScanApiKey()
    }
    
    single<String>(named("celoApiKey")) {
        get<ApiKeyManager>().getCeloScanApiKey()
    }
    
    single<String>(named("harmonyApiKey")) {
        get<ApiKeyManager>().getHarmonyScanApiKey()
    }
    
    single<String>(named("cronosApiKey")) {
        get<ApiKeyManager>().getCronoscanApiKey()
    }
    
    single<String>(named("klaytnApiKey")) {
        get<ApiKeyManager>().getKlaytnScopeApiKey()
    }
    
    single<String>(named("moonbeamApiKey")) {
        get<ApiKeyManager>().getMoonbeamApiKey()
    }
    
    single<String>(named("moonriverApiKey")) {
        get<ApiKeyManager>().getMoonriverApiKey()
    }
    
    single<String>(named("gnosisApiKey")) {
        get<ApiKeyManager>().getGnosisScanApiKey()
    }
    
    single<String>(named("optimismApiKey")) {
        get<ApiKeyManager>().getOptimisticEtherscanApiKey()
    }
    
    single<String>(named("arbitrumApiKey")) {
        get<ApiKeyManager>().getArbiscanApiKey()
    }
    
    single<String>(named("bittorrentApiKey")) {
        get<ApiKeyManager>().getBttcScanApiKey()
    }
}