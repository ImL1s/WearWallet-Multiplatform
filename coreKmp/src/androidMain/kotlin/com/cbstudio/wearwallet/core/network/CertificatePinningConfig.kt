package com.cbstudio.wearwallet.core.network

import okhttp3.CertificatePinner

/**
 * 證書固定配置
 * 防止中間人攻擊 (MITM)
 *
 * 證書指紋獲取於: 2025-10-28
 * 下次檢查日期: 2026-01-26 (90天)
 *
 * 使用方式:
 * ```
 * val okHttpClient = OkHttpClient.Builder()
 *     .certificatePinner(CertificatePinningConfig.createMainnetPinner())
 *     .build()
 * ```
 */
object CertificatePinningConfig {

    /**
     * 創建主網配置的證書固定器
     * 包含所有主要 RPC 端點和區塊瀏覽器 API
     */
    fun createMainnetPinner(): CertificatePinner {
        return CertificatePinner.Builder()
            // ========================================
            // Infura (Ethereum RPC)
            // ========================================
            .add(
                "mainnet.infura.io",
                "sha256/s8Q0iV/WLy+7tFd8Ypy31l4EKxZPPElw5TmRNGOt9ao="  // 主證書
            )
            // 備份證書 (Let's Encrypt Root CA)
            .add(
                "mainnet.infura.io",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // ISRG Root X1
            )

            // ========================================
            // Alchemy (多鏈 RPC)
            // ========================================
            .add(
                "eth-mainnet.g.alchemy.com",
                "sha256/LlhjzAzcbM4m84KXwrFbep95JQqixlgziHnaQdjygDY="  // 主證書
            )
            .add(
                "polygon-mainnet.g.alchemy.com",
                "sha256/LlhjzAzcbM4m84KXwrFbep95JQqixlgziHnaQdjygDY="  // 主證書
            )
            // Alchemy 備份證書
            .add(
                "*.alchemy.com",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // ISRG Root X1
            )

            // ========================================
            // Ankr (多鏈 RPC)
            // ========================================
            .add(
                "rpc.ankr.com",
                "sha256/66QuhTedxUxXkvtfQqwYGvy42unw2p/secAs/pZcRkM="  // 主證書
            )
            .add(
                "rpc.ankr.com",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            // ========================================
            // BSC (Binance Smart Chain)
            // ========================================
            .add(
                "bsc-dataseed.binance.org",
                "sha256/zEAnZpAGYTCdatry/wqycxcC7UNByBkJ4FteO+YqV4="  // 主證書
            )
            .add(
                "bsc-dataseed1.defibit.io",
                "sha256/LvXo4ihnjHaKBu3Y8Mq8GykiGsczWJeVNLMGTqor2b0="  // 主證書
            )

            // ========================================
            // Polygon
            // ========================================
            .add(
                "polygon-rpc.com",
                "sha256/3Nx4Y1jq1hEpCjQ2zZZSNCa4NxiP9r0VFF5T3qKQgU8="  // 主證書
            )
            .add(
                "polygon-rpc.com",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            // ========================================
            // Arbitrum
            // ========================================
            .add(
                "arb1.arbitrum.io",
                "sha256/jx+oppCXLnE3sUmjc8XUfvKGvjDaTKMNHOBg0jMuncs="  // 主證書
            )
            .add(
                "arb1.arbitrum.io",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            // ========================================
            // Optimism
            // ========================================
            .add(
                "mainnet.optimism.io",
                "sha256/z+XNrO4t8A+VHgvxhMNGqU8I82A2clzJhb7OPQvhUUE="  // 主證書
            )
            .add(
                "mainnet.optimism.io",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            // ========================================
            // Solana
            // ========================================
            .add(
                "api.mainnet-beta.solana.com",
                "sha256/1Gc4qVBbhX0wJ25GNAzm9XFg+Ggp5Uq3BhofkHJl/J0="  // 主證書
            )
            .add(
                "api.mainnet-beta.solana.com",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            // ========================================
            // Avalanche
            // ========================================
            .add(
                "api.avax.network",
                "sha256/UaLH3lX1MeZccNMHOC4ls1g4yGI0FM1bujd+Gm1wU3k="  // 主證書
            )
            .add(
                "api.avax.network",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            // ========================================
            // Base
            // ========================================
            .add(
                "mainnet.base.org",
                "sha256/VBlaP5588Zd9DuIMzAd1Ii5/wAxTpqsXJM8nG0BTHmk="  // 主證書
            )
            .add(
                "mainnet.base.org",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            // ========================================
            // 區塊瀏覽器 API
            // ========================================
            .add(
                "api.etherscan.io",
                "sha256/kjWU9H91qtu39iBXltykNck8+xWT425ShPW+wFF2WTg="  // 主證書
            )
            .add(
                "api.etherscan.io",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            .add(
                "api.bscscan.com",
                "sha256/BoEDiC2JgKpT5cx5P6AxUGLrTx0EiMBmP8YrXCVWAeI="  // 主證書
            )
            .add(
                "api.bscscan.com",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            .add(
                "api.polygonscan.com",
                "sha256/sUfoyW0Lxe6tLtx/S6lAINHJyJH3j0hWxhmW/rXZcZI="  // 主證書
            )
            .add(
                "api.polygonscan.com",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            .build()
    }

    /**
     * 創建測試網配置的證書固定器
     * 包含測試網 RPC 端點
     */
    fun createTestnetPinner(): CertificatePinner {
        return CertificatePinner.Builder()
            // Infura Sepolia
            .add(
                "sepolia.infura.io",
                "sha256/s8Q0iV/WLy+7tFd8Ypy31l4EKxZPPElw5TmRNGOt9ao="  // 主證書
            )
            .add(
                "sepolia.infura.io",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            // Solana Devnet
            .add(
                "api.devnet.solana.com",
                "sha256/QPPepoAK1Ha4P9fh81GcSNhRFKP19HVzQdCej28aJAM="  // 主證書
            )
            .add(
                "api.devnet.solana.com",
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="  // 備份證書
            )

            .build()
    }

    /**
     * 創建開發環境的證書固定器
     * ⚠️ 僅用於開發環境，不進行證書固定
     *
     * 注意：生產環境絕不能使用此配置！
     */
    fun createDevelopmentPinner(): CertificatePinner {
        // 開發環境不進行證書固定，方便使用代理工具調試
        return CertificatePinner.Builder().build()
    }

    /**
     * 根據環境自動選擇證書固定器
     */
    fun createPinnerForEnvironment(
        isMainnet: Boolean = true,
        isDevelopment: Boolean = false
    ): CertificatePinner {
        return when {
            isDevelopment -> createDevelopmentPinner()
            isMainnet -> createMainnetPinner()
            else -> createTestnetPinner()
        }
    }
}
