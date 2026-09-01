package com.cbstudio.wearwallet.core.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * 安全的 HTTP 客戶端工廠
 * 整合證書固定功能，防止中間人攻擊
 *
 * 特性：
 * - 證書固定 (Certificate Pinning)
 * - HTTP/2 支援
 * - 自動重試機制
 * - 請求日誌記錄
 */
object SecureHttpClientFactory {

    /**
     * 創建配置了證書固定的安全 HTTP 客戶端
     *
     * @param isMainnet 是否為主網環境
     * @param isDevelopment 是否為開發環境（開發環境不進行證書固定）
     * @param enableLogging 是否啟用請求日誌
     */
    fun createSecureClient(
        isMainnet: Boolean = true,
        isDevelopment: Boolean = false,
        enableLogging: Boolean = true
    ): HttpClient {
        return HttpClient(OkHttp) {
            // ========================================
            // OkHttp 引擎配置
            // ========================================
            engine {
                config {
                    // ✅ 配置證書固定
                    certificatePinner(
                        CertificatePinningConfig.createPinnerForEnvironment(
                            isMainnet = isMainnet,
                            isDevelopment = isDevelopment
                        )
                    )

                    // 超時配置
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)

                    // ✅ 啟用 HTTP/2 提升性能
                    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))

                    // 連接池配置
                    connectionPool(
                        okhttp3.ConnectionPool(
                            maxIdleConnections = 5,
                            keepAliveDuration = 5,
                            timeUnit = TimeUnit.MINUTES
                        )
                    )

                    // 重試配置
                    retryOnConnectionFailure(true)
                }
            }

            // ========================================
            // Ktor 客戶端插件配置
            // ========================================

            // JSON 序列化
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                    coerceInputValues = true
                })
            }

            // 超時配置
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }

            // 請求日誌
            if (enableLogging) {
                install(Logging) {
                    level = LogLevel.INFO
                    logger = object : Logger {
                        override fun log(message: String) {
                            println("[SecureHttpClient] $message")
                        }
                    }
                }
            }

            // 預設請求配置
            defaultRequest {
                // 預設 User-Agent
                headers {
                    append(
                        HttpHeaders.UserAgent,
                        "WearWallet/1.0 (Android; Secure HTTP Client)"
                    )
                    // 預設接受 JSON
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                }
            }

            // HTTP 重定向
            install(HttpRedirect) {
                checkHttpMethod = true
                allowHttpsDowngrade = false
            }
        }
    }

    /**
     * 創建專用於區塊鏈 RPC 的客戶端
     * 針對 JSON-RPC 協議優化
     */
    fun createRpcClient(
        isMainnet: Boolean = true,
        isDevelopment: Boolean = false
    ): HttpClient {
        return createSecureClient(
            isMainnet = isMainnet,
            isDevelopment = isDevelopment,
            enableLogging = true
        )
    }

    /**
     * 創建專用於 REST API 的客戶端
     * 針對區塊瀏覽器 API 優化
     */
    fun createRestClient(
        isMainnet: Boolean = true,
        isDevelopment: Boolean = false
    ): HttpClient {
        return createSecureClient(
            isMainnet = isMainnet,
            isDevelopment = isDevelopment,
            enableLogging = true
        )
    }
}
