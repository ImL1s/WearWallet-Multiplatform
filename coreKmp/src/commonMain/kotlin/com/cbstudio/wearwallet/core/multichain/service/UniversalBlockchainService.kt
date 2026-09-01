package com.cbstudio.wearwallet.core.multichain.service

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.TransferRequest
import com.cbstudio.wearwallet.core.multichain.model.MultiChainTransaction
import com.cbstudio.wearwallet.core.multichain.model.ValidationResult

/**
 * 統一的區塊鏈服務介面
 * 提供所有區塊鏈操作的標準化介面
 */
interface UniversalBlockchainService {
    
    /**
     * 支援的區塊鏈類型
     */
    val supportedChainType: MultiChainType
    
    /**
     * 生成新錢包地址
     * @param publicKey 公鑰（十六進制字串）
     * @return 區塊鏈地址
     */
    suspend fun generateAddress(publicKey: String): String
    
    /**
     * 驗證地址格式
     * @param address 要驗證的地址
     * @return 驗證結果
     */
    suspend fun validateAddress(address: String): ValidationResult
    
    /**
     * 查詢餘額
     * @param address 錢包地址
     * @return 餘額（字串格式，避免精度問題）
     */
    suspend fun getBalance(address: String): String
    
    /**
     * 查詢交易記錄
     * @param address 錢包地址
     * @param limit 限制數量（預設20）
     * @return 交易記錄列表
     */
    suspend fun getTransactionHistory(
        address: String,
        limit: Int = 20
    ): List<MultiChainTransaction>
    
    /**
     * 估算手續費
     * @param request 轉帳請求
     * @return 估算的手續費
     */
    suspend fun estimateFee(request: TransferRequest): String
    
    /**
     * 建立未簽名交易
     * @param request 轉帳請求
     * @return 未簽名交易資料（十六進制字串）
     */
    suspend fun createUnsignedTransaction(request: TransferRequest): String
    
    /**
     * 簽名交易
     * @param unsignedTx 未簽名交易
     * @param privateKey 私鑰（十六進制字串）
     * @return 已簽名交易資料（十六進制字串）
     */
    suspend fun signTransaction(unsignedTx: String, privateKey: String): String
    
    /**
     * 廣播交易
     * @param signedTx 已簽名交易
     * @return 交易哈希
     */
    suspend fun broadcastTransaction(signedTx: String): String
    
    /**
     * 查詢交易狀態
     * @param txHash 交易哈希
     * @return 交易詳情
     */
    suspend fun getTransaction(txHash: String): MultiChainTransaction?
    
    /**
     * 檢查服務是否可用
     * @return 服務可用性
     */
    suspend fun isServiceAvailable(): Boolean
    
    /**
     * 取得目前的區塊高度
     * @return 區塊高度
     */
    suspend fun getCurrentBlockHeight(): Long
}

/**
 * 可選功能介面 - 代幣操作
 * 支援 ERC-20、TRC-20、SPL 等代幣標準
 */
interface TokenService {
    /**
     * 查詢代幣餘額
     */
    suspend fun getTokenBalance(address: String, tokenAddress: String): String
    
    /**
     * 轉移代幣
     */
    suspend fun transferToken(
        request: TransferRequest,
        tokenAddress: String
    ): String
}

/**
 * 可選功能介面 - 智能合約操作
 * 適用於支援智能合約的區塊鏈（如 Ethereum、TRON、Solana）
 */
interface SmartContractService {
    /**
     * 調用智能合約
     */
    suspend fun callContract(
        contractAddress: String,
        methodName: String,
        parameters: List<Any>
    ): Any
    
    /**
     * 估算合約調用 Gas
     */
    suspend fun estimateContractGas(
        contractAddress: String,
        methodName: String,
        parameters: List<Any>
    ): String
}

/**
 * 可選功能介面 - 多重簽名錢包
 * 適用於支援多重簽名的區塊鏈
 */
interface MultiSigService {
    /**
     * 創建多重簽名錢包
     */
    suspend fun createMultiSigWallet(
        owners: List<String>,
        requiredSignatures: Int
    ): String
    
    /**
     * 提交多重簽名交易
     */
    suspend fun submitMultiSigTransaction(
        walletAddress: String,
        transaction: TransferRequest
    ): String
    
    /**
     * 簽署多重簽名交易
     */
    suspend fun signMultiSigTransaction(
        transactionId: String,
        privateKey: String
    ): String
}