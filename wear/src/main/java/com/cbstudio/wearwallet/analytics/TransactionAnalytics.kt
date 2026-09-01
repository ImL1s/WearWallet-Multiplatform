package com.cbstudio.wearwallet.analytics

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 交易功能專用的分析追蹤
 */
class TransactionAnalytics constructor(
    private val analyticsManager: AnalyticsManager
) {
    
    /**
     * 追蹤交易發送開始
     */
    fun trackTransactionSendStarted(
        chainId: String,
        tokenSymbol: String,
        amount: String,
        transactionType: TransactionType
    ) {
        analyticsManager.logScreenView("transaction_send")
        analyticsManager.log("Transaction send started: $tokenSymbol on chain $chainId")
    }
    
    /**
     * 追蹤交易發送成功
     */
    fun trackTransactionSendSuccess(
        chainId: String,
        tokenSymbol: String,
        amount: String,
        txHash: String,
        gasUsed: String
    ) {
        analyticsManager.logTransactionSent(chainId, tokenSymbol, amount)
        analyticsManager.log("Transaction sent successfully: $txHash")
    }
    
    /**
     * 追蹤交易發送失敗
     */
    fun trackTransactionSendFailure(
        chainId: String,
        tokenSymbol: String,
        amount: String,
        errorCode: String,
        errorMessage: String
    ) {
        analyticsManager.logError(
            AnalyticsManager.Events.TRANSACTION_FAILED,
            errorCode,
            errorMessage
        )
    }
    
    /**
     * 追蹤 QR 碼掃描
     */
    fun trackQRCodeScanned(
        qrType: QRCodeType,
        success: Boolean,
        data: String? = null
    ) {
        val eventParams = mutableMapOf<String, String>()
        eventParams["qr_type"] = qrType.name
        eventParams["success"] = success.toString()
        
        if (success && data != null) {
            analyticsManager.log("QR Code scanned successfully: ${qrType.name}")
        } else {
            analyticsManager.logError(
                "qr_scan_failed",
                "QR_SCAN_ERROR",
                "Failed to scan QR code: ${qrType.name}"
            )
        }
    }
    
    /**
     * 追蹤 QR 碼生成
     */
    fun trackQRCodeGenerated(
        qrType: QRCodeType,
        address: String
    ) {
        analyticsManager.log("QR Code generated: ${qrType.name} for address $address")
    }
    
    /**
     * 追蹤地址簿使用
     */
    fun trackAddressBookUsed(
        action: AddressBookAction,
        contactCount: Int
    ) {
        val eventData = mapOf(
            "action" to action.name,
            "contact_count" to contactCount.toString()
        )
        
        analyticsManager.log("Address book used: ${action.name}")
    }
    
    /**
     * 追蹤 Gas 費用設定變更
     */
    fun trackGasFeeSettingChanged(
        chainId: String,
        gasLevel: String,
        customGasPrice: String?
    ) {
        analyticsManager.log("Gas fee setting changed: $gasLevel on chain $chainId")
    }
    
    /**
     * 追蹤交易歷史查看
     */
    fun trackTransactionHistoryViewed(
        chainId: String,
        walletAddress: String,
        transactionCount: Int
    ) {
        analyticsManager.logScreenView("transaction_history")
        analyticsManager.log("Transaction history viewed: $transactionCount transactions")
    }
    
    /**
     * 追蹤接收畫面使用
     */
    fun trackReceiveScreenUsed(
        chainId: String,
        tokenSymbol: String,
        qrCodeShown: Boolean
    ) {
        analyticsManager.logScreenView("receive_screen")
        if (qrCodeShown) {
            trackQRCodeGenerated(QRCodeType.RECEIVE_ADDRESS, "receive_screen")
        }
    }
    
    /**
     * 追蹤 Keystone 硬體錢包互動
     */
    fun trackKeystoneInteraction(
        action: KeystoneAction,
        success: Boolean,
        errorMessage: String? = null
    ) {
        if (success) {
            analyticsManager.log("Keystone interaction successful: ${action.name}")
        } else {
            analyticsManager.logError(
                AnalyticsManager.Events.KEYSTONE_ERROR,
                "KEYSTONE_${action.name}_ERROR",
                errorMessage ?: "Unknown Keystone error"
            )
        }
    }
    
    /**
     * 追蹤網路切換
     */
    fun trackNetworkSwitched(
        fromChainId: String,
        toChainId: String,
        reason: String
    ) {
        analyticsManager.log("Network switched from $fromChainId to $toChainId: $reason")
        analyticsManager.setUserProperty(
            AnalyticsManager.UserProperties.PREFERRED_CHAIN,
            toChainId
        )
    }
    
    /**
     * 追蹤代幣添加
     */
    fun trackTokenAdded(
        chainId: String,
        tokenSymbol: String,
        tokenAddress: String,
        isCustomToken: Boolean
    ) {
        val eventType = if (isCustomToken) "custom_token_added" else "standard_token_added"
        analyticsManager.log("Token added: $tokenSymbol on chain $chainId")
    }
    
    enum class TransactionType {
        STANDARD_TRANSFER,
        TOKEN_TRANSFER,
        CONTRACT_INTERACTION,
        MULTI_SEND,
        KEYSTONE_SIGNED
    }
    
    enum class QRCodeType {
        RECEIVE_ADDRESS,
        TRANSACTION_DATA,
        WALLET_CONNECT,
        KEYSTONE_SYNC,
        PAYMENT_REQUEST
    }
    
    enum class AddressBookAction {
        CONTACT_ADDED,
        CONTACT_EDITED,
        CONTACT_DELETED,
        CONTACT_SELECTED,
        ADDRESS_COPIED
    }
    
    enum class KeystoneAction {
        WALLET_SYNC,
        TRANSACTION_SIGN,
        QR_CODE_SCAN,
        FIRMWARE_CHECK,
        CONNECTION_TEST
    }
}
