package com.cbstudio.wearwallet.core.multichain.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * TRON Protocol Protobuf 定義
 *
 * 由於 KMP 缺乏完整的 Protobuf 支援,我們使用 JSON 結構來表示 Protobuf 消息
 * 這些結構可以通過 TronGrid API 的 JSON-RPC 介面進行序列化和反序列化
 *
 * 參考: https://github.com/tronprotocol/protocol
 */

/**
 * TRON 交易結構
 *
 * Protobuf 定義:
 * ```protobuf
 * message Transaction {
 *   message raw {
 *     repeated Contract contract = 1;
 *     int64 timestamp = 2;
 *     int64 expiration = 3;
 *     bytes ref_block_bytes = 4;
 *     bytes ref_block_hash = 5;
 *     int64 fee_limit = 6;
 *   }
 *   raw raw_data = 1;
 *   repeated bytes signature = 2;
 * }
 * ```
 */
@Serializable
data class TronTransaction(
    val visible: Boolean = false,
    val txID: String = "",
    val raw_data: RawData,
    val raw_data_hex: String = "",
    val signature: List<String> = emptyList()
) {
    @Serializable
    data class RawData(
        val contract: List<Contract>,
        val ref_block_bytes: String,
        val ref_block_hash: String,
        val expiration: Long,
        val timestamp: Long,
        val fee_limit: Long = 0
    )

    @Serializable
    data class Contract(
        val parameter: Parameter,
        val type: String
    )

    @Serializable
    data class Parameter(
        val value: JsonObject,
        val type_url: String
    )
}

/**
 * TRON 轉帳合約 (TransferContract)
 *
 * Protobuf 定義:
 * ```protobuf
 * message TransferContract {
 *   bytes owner_address = 1;
 *   bytes to_address = 2;
 *   int64 amount = 3;
 * }
 * ```
 */
@Serializable
data class TransferContract(
    val owner_address: String,  // Base58Check 或 Hex 格式
    val to_address: String,     // Base58Check 或 Hex 格式
    val amount: Long            // SUN 單位 (1 TRX = 1,000,000 SUN)
)

/**
 * TRON 智能合約觸發 (TriggerSmartContract)
 * 用於 TRC20 代幣轉帳等智能合約調用
 *
 * Protobuf 定義:
 * ```protobuf
 * message TriggerSmartContract {
 *   bytes owner_address = 1;
 *   bytes contract_address = 2;
 *   int64 call_value = 3;
 *   bytes data = 4;
 *   int64 call_token_value = 5;
 *   int64 token_id = 6;
 * }
 * ```
 */
@Serializable
data class TriggerSmartContract(
    val owner_address: String,      // 調用者地址
    val contract_address: String,   // 合約地址
    val call_value: Long = 0,       // TRX 數量 (SUN)
    val data: String,               // 編碼後的合約方法和參數
    val call_token_value: Long = 0, // TRC10 代幣數量
    val token_id: Long = 0          // TRC10 代幣 ID
)

/**
 * TRON 交易結果
 */
@Serializable
data class TronTransactionResult(
    val result: Boolean,
    val code: String? = null,
    val message: String? = null,
    val txid: String? = null
)

/**
 * TRON 地址工具類
 */
object TronAddress {
    /**
     * Base58Check 地址轉 Hex
     * TRON mainnet 地址以 'T' 開頭 (0x41)
     * TRON testnet 地址以 'T' 開頭 (0xa0 Shasta)
     */
    fun base58ToHex(base58Address: String): String {
        // 簡化實現：假設 API 會自動處理
        // 實際應使用 Base58Check 解碼算法
        return "41${base58Address.substring(1)}"
    }

    /**
     * Hex 地址轉 Base58Check
     */
    fun hexToBase58(hexAddress: String): String {
        // 簡化實現：假設 API 會自動處理
        // 實際應使用 Base58Check 編碼算法
        val cleanHex = hexAddress.removePrefix("0x").removePrefix("41")
        return "T$cleanHex"
    }

    /**
     * 驗證 TRON 地址格式
     */
    fun isValidAddress(address: String): Boolean {
        // TRON 地址規則:
        // - Base58Check 格式: T 開頭，34 個字符
        // - Hex 格式: 41 開頭，42 個字符
        return when {
            address.startsWith("T") -> address.length == 34
            address.startsWith("41") -> address.length == 42
            address.startsWith("0x41") -> address.length == 44
            else -> false
        }
    }
}

/**
 * TRC20 代幣轉帳編碼器
 */
object TRC20Encoder {
    /**
     * 編碼 TRC20 transfer(address,uint256) 方法調用
     *
     * @param toAddress 接收者地址 (Base58Check 或 Hex)
     * @param amount 轉帳金額 (最小單位)
     * @return 編碼後的十六進制字符串
     */
    fun encodeTransfer(toAddress: String, amount: String): String {
        // transfer(address,uint256) 的方法 ID
        val methodId = "a9059cbb"

        // 將地址轉換為 32 字節 (去除 0x41 前綴，填充到 64 字符)
        val cleanAddress = toAddress
            .removePrefix("0x")
            .removePrefix("41")
            .removePrefix("T")
        val paddedAddress = cleanAddress.padStart(64, '0')

        // 將金額轉換為 32 字節十六進制
        val amountLong = amount.toLongOrNull() ?: throw IllegalArgumentException("Invalid amount: $amount")
        val amountHex = amountLong.toString(16).padStart(64, '0')

        return methodId + paddedAddress + amountHex
    }

    /**
     * 編碼 balanceOf(address) 方法調用
     */
    fun encodeBalanceOf(address: String): String {
        val methodId = "70a08231"
        val cleanAddress = address
            .removePrefix("0x")
            .removePrefix("41")
            .removePrefix("T")
        val paddedAddress = cleanAddress.padStart(64, '0')

        return methodId + paddedAddress
    }

    /**
     * 解碼 uint256 返回值
     */
    fun decodeUint256(hexData: String): String {
        val cleanHex = hexData.removePrefix("0x")
        // 使用 Kotlin/Native 兼容的方式處理大數字
        return try {
            cleanHex.toLong(16).toString()
        } catch (e: Exception) {
            // 如果數字太大，返回原始十六進制
            cleanHex
        }
    }
}

/**
 * BigInteger 的簡單實現（KMP 支援）
 */
fun String.toBigIntegerOrNull(): kotlinx.serialization.json.JsonElement? {
    return try {
        val longValue = this.toLongOrNull() ?: return null
        JsonPrimitive(longValue)
    } catch (e: Exception) {
        null
    }
}
