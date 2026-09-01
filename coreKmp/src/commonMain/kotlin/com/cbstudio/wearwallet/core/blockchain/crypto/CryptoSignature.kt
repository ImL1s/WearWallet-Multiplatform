package com.cbstudio.wearwallet.core.blockchain.crypto

/**
 * 跨平台的加密簽名介面
 * 使用 expect/actual 模式為不同平台提供真實的簽名實現
 */
expect object CryptoSignature {
    /**
     * 使用 Ed25519 進行簽名（用於 Solana）
     * @param message 要簽名的消息
     * @param privateKeyHex 私鑰的十六進制字符串
     * @return 簽名的 Base58 編碼字符串
     */
    fun signWithEd25519(message: String, privateKeyHex: String): String
    
    /**
     * 使用 ECDSA 進行簽名（用於 Ethereum/Bitcoin）
     * @param message 要簽名的消息
     * @param privateKeyHex 私鑰的十六進制字符串
     * @return 簽名的十六進制字符串
     */
    fun signWithECDSA(message: String, privateKeyHex: String): String
    
    /**
     * 對已哈希的摘要進行簽名 (ECDSA)
     * @param digest 已哈希的消息摘要 (32 bytes)
     * @param privateKeyHex 私鑰
     * @return 簽名 (hex)
     */
    fun signDigest(digest: ByteArray, privateKeyHex: String): String
    
    /**
     * 簽名 Solana 交易
     * @param transaction 未簽名的交易數據
     * @param privateKeyHex 私鑰的十六進制字符串
     * @param recentBlockhash 最近的區塊哈希（可選）
     * @return 簽名後的交易數據
     */
    fun signSolanaTransaction(
        transaction: ByteArray, 
        privateKeyHex: String,
        recentBlockhash: String? = null
    ): ByteArray
    
    /**
     * 簽名 Ethereum 交易
     * @param transaction 未簽名的交易數據
     * @param privateKeyHex 私鑰的十六進制字符串
     * @param chainId 鏈 ID
     * @return 簽名後的交易數據
     */
    fun signEthereumTransaction(
        transaction: ByteArray, 
        privateKeyHex: String,
        chainId: Int
    ): ByteArray
    
    /**
     * 生成交易哈希
     * @param signedTransaction 已簽名的交易
     * @param chainType 區塊鏈類型
     * @return 交易哈希
     */
    fun generateTransactionHash(signedTransaction: ByteArray, chainType: String): String
    
    /**
     * 驗證簽名（字符串消息版本）
     * @param message 原始消息（字符串）
     * @param signature 簽名
     * @param publicKey 公鑰
     * @param curveType 曲線類型（ED25519 或 SECP256K1）
     * @return 簽名是否有效
     */
    fun verifySignature(
        message: String,
        signature: String,
        publicKey: String,
        curveType: String
    ): Boolean

    /**
     * 驗證簽名（原始字節版本）
     * 用於 RFC 8032 標準測試和二進制數據簽名
     * @param messageBytes 原始消息字節
     * @param signature 簽名（十六進制字符串）
     * @param publicKey 公鑰（十六進制字符串）
     * @param curveType 曲線類型（ED25519 或 SECP256K1）
     * @return 簽名是否有效
     */
    fun verifySignatureBytes(
        messageBytes: ByteArray,
        signature: String,
        publicKey: String,
        curveType: String
    ): Boolean

    /**
     * 從私鑰派生公鑰
     * @param privateKeyHex 私鑰（64 個十六進制字符 = 32 字節）
     * @return 公鑰（130 個十六進制字符，非壓縮格式 04||x||y）
     */
    fun derivePublicKeyFromPrivateKey(privateKeyHex: String): String

    /**
     * 從簽名恢復公鑰
     * @param messageHash 消息哈希（64 個十六進制字符 = 32 字節）
     * @param r 簽名的 r 值（64 個十六進制字符 = 32 字節）
     * @param s 簽名的 s 值（64 個十六進制字符 = 32 字節）
     * @param recoveryId Recovery ID (0-3)
     * @return 公鑰（130 個十六進制字符，非壓縮格式）或 null（如果恢復失敗）
     */
    fun recoverPublicKey(
        messageHash: String,
        r: String,
        s: String,
        recoveryId: Int
    ): String?
}