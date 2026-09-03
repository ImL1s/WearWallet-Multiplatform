package com.cbstudio.wearwallet.core.domain.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.pow

@Serializable
data class Transaction(
    val id: String = "",
    val hash: String,
    val walletId: String = "",
    val walletAddress: String = "",
    val from: String,
    val to: String,
    val value: String,
    val tokenId: String? = null,
    val tokenSymbol: String? = null,
    val tokenDecimals: Int? = null,
    val tokenAddress: String? = null,
    val tokenName: String? = null,
    val gasPrice: String? = null,
    val gasLimit: String? = null,
    val gasUsed: Long? = null,
    val nonce: Long,
    val blockNumber: Long? = null,
    val chainType: ChainType,
    val network: Network = Network.Mainnet,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val type: TransactionType = TransactionType.TRANSFER,
    val direction: TransactionDirection = TransactionDirection.OUTGOING,
    val timestamp: Instant? = null,
    val createdAt: Instant = Clock.System.now(),
    val confirmedAt: Instant? = null,
    val data: String? = null,
    val confirmations: Int = 0,
    val networkFee: String? = null,
    val estimatedConfirmationTime: Int? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun getFormattedAmount(): String {
        return if (tokenDecimals != null) {
            try {
                val decimals = tokenDecimals
                val amount = value.toDouble() / 10.0.pow(decimals.toDouble())
                formatDecimal(amount, 6).trimEnd('0').trimEnd('.')
            } catch (e: Exception) {
                "0"
            }
        } else {
            value
        }
    }

    fun getDisplaySymbol(): String {
        return tokenSymbol ?: chainType.nativeToken
    }

    fun getShortHash(): String {
        return if (hash.length > 10) "${hash.take(6)}...${hash.takeLast(4)}" else hash
    }
}

@Serializable
enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    DROPPED,
    REPLACED,
    CANCELLED,
    SEND;  // 為了相容性加入 SEND 狀態
    
    fun isFinal(): Boolean = this in listOf(CONFIRMED, FAILED, DROPPED, CANCELLED)
    fun isSuccessful(): Boolean = this == CONFIRMED
    fun isPending(): Boolean = this == PENDING
}

/**
 * 交易類型
 */
@Serializable
enum class TransactionType {
    TRANSFER,
    CONTRACT_CALL,
    CONTRACT_CREATION,
    CONTRACT_INTERACTION,
    TOKEN_TRANSFER,
    TOKEN_APPROVAL,
    NFT_TRANSFER,
    SWAP,
    INTERNAL,
    UNKNOWN
}

/**
 * 交易方向
 */
@Serializable
enum class TransactionDirection { 
    INCOMING, 
    OUTGOING, 
    SELF, 
    INTERNAL 
}

/**
 * 格式化小數點
 */
private fun formatDecimal(value: Double, decimals: Int): String {
    // 使用字串操作來格式化，避免平台特定的 format 方法
    val formatted = value.toString()
    val dotIndex = formatted.indexOf('.')
    return if (dotIndex == -1) {
        "$formatted." + "0".repeat(decimals)
    } else {
        val decimalPart = formatted.substring(dotIndex + 1)
        if (decimalPart.length >= decimals) {
            formatted.substring(0, dotIndex + 1 + decimals)
        } else {
            formatted + "0".repeat(decimals - decimalPart.length)
        }
    }
}

@Serializable
data class TransactionRequest(
    val from: String,
    val to: String,
    val value: String,
    val gasPrice: String? = null,
    val gasLimit: String? = null,
    val nonce: Long? = null,
    val data: String? = null,
    val chainType: ChainType,
    val tokenAddress: String? = null,
    val executionContext: com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext? = null
)