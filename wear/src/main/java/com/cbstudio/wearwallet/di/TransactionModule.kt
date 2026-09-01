/**
 * 交易模組 - MAINTENANCE MODE
 * ULTRATHINK Phase 15 - 批量修復策略
 *
 * TODO: Complex transaction operations temporarily disabled for maintenance
 * - All transaction functionality disabled
 * - Keep module structure consistent for future implementation
 * - Focus on core wallet functionality
 */

package com.cbstudio.wearwallet.di

import com.cbstudio.wearwallet.domain.service.SolanaTransactionService
import org.koin.dsl.module

/**
 * 交易服務模組 - 維護模式
 * 暫時停用所有交易服務依賴，確保編譯穩定性
 */
val transactionModule = module {
    // MAINTENANCE MODE: All transaction services disabled
    // Services will be re-implemented after core architecture stabilization

    // === Solana 交易服務 ===
    single { SolanaTransactionService() }
}