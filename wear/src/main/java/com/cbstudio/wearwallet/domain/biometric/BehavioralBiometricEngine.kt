/**
 * 行為生物識別引擎 - MAINTENANCE MODE
 * ULTRATHINK Phase 15 - 批量修復策略
 * 
 * TODO: Complex biometric analysis operations temporarily disabled for maintenance
 * - All biometric functionality disabled  
 * - Keep class structure consistent for future implementation
 * - Focus on core wallet functionality
 */

package com.cbstudio.wearwallet.domain.biometric

import android.content.Context

/**
 * 行為生物識別引擎 - 維護模式
 * 暫時停用所有生物識別分析，確保編譯穩定性
 */
class BehavioralBiometricEngine(
    private val context: Context
) {
    // MAINTENANCE MODE: All biometric analysis disabled
    // Engine will be re-implemented after core architecture stabilization
    
    fun analyzePattern(): Boolean = false
    fun authenticate(): Boolean = false
}