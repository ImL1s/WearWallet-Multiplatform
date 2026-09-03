package com.cbstudio.wearwallet.core.security

/**
 * Keystore 管理器介面
 * 負責處理密鑰推導和安全存儲
 */
expect class KeystoreManager() {
    /**
     * 從助記詞推導私鑰
     * 
     * @param mnemonic 助記詞
     * @param derivationPath HD 推導路徑
     * @return 私鑰（hex 格式）
     */
    suspend fun derivePrivateKey(mnemonic: String, derivationPath: String): String
    
    /**
     * 生成新的助記詞
     * 
     * @param strength 強度（12/15/18/21/24 個單詞）
     * @return 助記詞
     */
    suspend fun generateMnemonic(strength: Int = 12): String
    
    /**
     * 驗證助記詞
     * 
     * @param mnemonic 助記詞
     * @return 是否有效
     */
    suspend fun validateMnemonic(mnemonic: String): Boolean
    
    /**
     * 從私鑰獲取公鑰
     * 
     * @param privateKey 私鑰（hex 格式）
     * @return 公鑰（hex 格式）
     */
    suspend fun getPublicKey(privateKey: String): String
    
    /**
     * 從公鑰獲取地址
     * 
     * @param publicKey 公鑰（hex 格式）
     * @param coinType 幣種類型
     * @return 地址
     */
    suspend fun getAddress(publicKey: String, coinType: Int): String
}