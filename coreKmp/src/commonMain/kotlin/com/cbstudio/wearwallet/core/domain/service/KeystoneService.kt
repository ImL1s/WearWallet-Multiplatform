package com.cbstudio.wearwallet.core.domain.service

import com.cbstudio.wearwallet.core.domain.model.keystone.*

/**
 * Keystone 硬體錢包服務介面
 * 提供跨平台的 Keystone 錢包整合功能
 */
expect class KeystoneService() {
    
    companion object {
        val DEFAULT_MASTER_FINGERPRINT: String
        val DEFAULT_DERIVATION_PATH: String
        val APP_ORIGIN: String
    }
    
    /**
     * 初始化服務
     */
    suspend fun initialize(): KeystoneResult<Unit>
    
    /**
     * 生成以太坊簽名請求的 UR 數據
     */
    suspend fun generateEthSignRequest(
        unsignedTxHex: String,
        derivationPath: String = DEFAULT_DERIVATION_PATH,
        masterFingerprint: String = DEFAULT_MASTER_FINGERPRINT,
        chainId: Long = 1L,
        requestId: String = generateRequestId(),
        fromAddress: String? = null
    ): KeystoneSignRequest
    
    /**
     * 解析 Keystone 返回的簽名數據
     */
    suspend fun parseSignature(urString: String): KeystoneSignatureResult
    
    /**
     * 解析 Keystone 設備顯示的 HD Key QR 碼
     * 這是連接階段的核心：我們掃描 Keystone 設備上顯示的 QR 碼
     */
    suspend fun parseKeystoneHDKey(urString: String): KeystoneHDKeyResult
    
    /**
     * 從 QR 碼導入錢包
     */
    suspend fun importWalletFromQR(qrData: String): KeystoneResult<KeystoneWallet>
    
    /**
     * 生成簽名請求 QR 碼
     */
    suspend fun generateSignRequestQR(request: KeystoneSignRequest): KeystoneResult<String>
    
    /**
     * 解析簽名響應
     */
    suspend fun parseSignResponse(responseData: String): KeystoneResult<KeystoneSignatureResult>
    
    /**
     * 檢查是否為有效的 Keystone QR 碼
     */
    fun isValidKeystoneQR(qrData: String): Boolean
    
    /**
     * 生成請求 ID
     */
    fun generateRequestId(): String
}