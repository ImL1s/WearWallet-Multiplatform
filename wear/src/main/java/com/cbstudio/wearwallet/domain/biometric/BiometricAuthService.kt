/**
 * 生物識別認證服務 - MAINTENANCE MODE
 * ULTRATHINK Phase 15 - 批量修復策略
 * 
 * TODO: Complex biometric authentication operations temporarily disabled for maintenance
 * - All biometric functionality disabled  
 * - Keep service structure consistent for future implementation
 * - Focus on core wallet functionality
 */

package com.cbstudio.wearwallet.domain.biometric

import org.koin.core.component.KoinComponent

/**
 * 生物識別認證服務 - 維護模式
 * 暫時停用所有生物識別認證，確保編譯穩定性
 */
class BiometricAuthService : KoinComponent {
    // MAINTENANCE MODE: All biometric authentication disabled
    // Service will be re-implemented after core architecture stabilization
    
    fun authenticate(): Boolean = false
    fun isSupported(): Boolean = false
}