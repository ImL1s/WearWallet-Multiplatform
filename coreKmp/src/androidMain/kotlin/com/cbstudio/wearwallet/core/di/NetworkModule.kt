package com.cbstudio.wearwallet.core.di

import com.cbstudio.wearwallet.core.network.CertificatePinningFailureHandler
import com.cbstudio.wearwallet.core.network.SecureHttpClientFactory
import io.ktor.client.*
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 網路模組 - Android 平台實現
 * 提供配置了證書固定的 HTTP 客戶端
 */
val androidNetworkModule = module {

    // ========================================
    // HTTP 客戶端
    // ========================================

    /**
     * 主網環境的安全 HTTP 客戶端
     * 配置了完整的證書固定
     */
    single<HttpClient>(named("mainnet")) {
        SecureHttpClientFactory.createSecureClient(
            isMainnet = true,
            isDevelopment = false,
            enableLogging = true
        )
    }

    /**
     * 測試網環境的安全 HTTP 客戶端
     */
    single<HttpClient>(named("testnet")) {
        SecureHttpClientFactory.createSecureClient(
            isMainnet = false,
            isDevelopment = false,
            enableLogging = true
        )
    }

    /**
     * 開發環境的 HTTP 客戶端
     * ⚠️ 不進行證書固定，僅用於開發調試
     */
    single<HttpClient>(named("development")) {
        SecureHttpClientFactory.createSecureClient(
            isMainnet = true,
            isDevelopment = true,
            enableLogging = true
        )
    }

    /**
     * 預設的 HTTP 客戶端（開發模式，暫時禁用證書固定）
     * TODO: 更新證書指紋後改回 mainnet
     */
    single<HttpClient> {
        get(named("development"))
    }

    // ========================================
    // 專用客戶端
    // ========================================

    /**
     * 區塊鏈 RPC 專用客戶端
     * 針對 JSON-RPC 協議優化
     */
    single<HttpClient>(named("rpc")) {
        SecureHttpClientFactory.createRpcClient(
            isMainnet = true,
            isDevelopment = false
        )
    }

    /**
     * REST API 專用客戶端
     * 針對區塊瀏覽器 API 優化
     */
    single<HttpClient>(named("rest")) {
        SecureHttpClientFactory.createRestClient(
            isMainnet = true,
            isDevelopment = false
        )
    }

    // ========================================
    // 錯誤處理
    // ========================================

    /**
     * 證書固定失敗處理器
     */
    single { CertificatePinningFailureHandler() }
}
