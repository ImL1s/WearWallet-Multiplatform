package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction

/**
 * Bitcoin 簽名器 - 預期聲明
 * 各平台需要提供實際實現
 */
expect class BitcoinSigner() {
    /**
     * 簽名未簽名的交易
     * @param unsignedTx 未簽名的交易
     * @param privateKey 私鑰
     * @return 已簽名的交易
     */
    suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction
}

/**
 * 已簽名交易 - 通用資料結構
 */
data class SignedTransaction(
    val hash: String,
    val rawTransaction: String,
    val success: Boolean = true,
    val error: String? = null
)

/**
 * 簽名異常
 */
class SigningException(message: String) : Exception(message)