package com.cbstudio.wearwallet.presentation.complication

import android.content.Context

/**
 * NFT Complication Data Provider - Stub Implementation
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */
class NftComplicationDataProvider {
    
    companion object {
        
        /**
         * Request update for all NFT complications
         */
        fun requestUpdateAll(context: Context) {
            // Stub implementation - NFT complications disabled for now
            // TODO: Implement NFT complication functionality when needed
        }
        
        /**
         * Request update for specific complication
         */
        fun requestUpdate(context: Context, complicationId: Int) {
            // Stub implementation
        }
        
        /**
         * Initialize NFT data provider
         */
        fun initialize(context: Context) {
            // Stub implementation
        }
        
        /**
         * Check if NFT complications are enabled
         */
        fun isEnabled(): Boolean = false // Disabled for now
    }
}