package com.cbstudio.wearwallet.core.blockchain.model

import com.cbstudio.wearwallet.core.domain.model.Network

/**
 * 未簽名交易 - 用於 UTXO 鏈
 */
data class UnsignedTransaction(
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val gasPrice: String = "",
    val gasLimit: String = "",
    val nonce: String = "",
    val data: String? = null,
    val chainId: String = "",
    val fee: String = "",
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 已簽名交易 - 用於 UTXO 鏈
 */
data class SignedTransaction(
    val hash: String,
    val rawTransaction: String,
    val success: Boolean = true,
    val error: String? = null
)