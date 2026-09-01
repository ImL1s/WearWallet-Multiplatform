package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import io.github.iml1s.crypto.keccak256

/**
 * iOS 平台的 Ethereum SDK 實現
 * 使用共享的 EthereumSigner 進行真實的 EIP-155 交易簽名
 */
internal actual suspend fun RealEthereumSDK.signTransactionPlatform(
    chainType: MultiChainType,
    nonce: Long,
    gasPrice: String,
    gasLimit: String,
    toAddress: String,
    value: String,
    data: String,
    privateKey: String,
    chainId: Long
): Result<SignedTransaction> {
    return try {
        val cleanPk = privateKey.removePrefix("0x").removePrefix("0X")
        require(cleanPk.length == 64) { "Private key must be 64 hex characters" }
        val pkBytes = ByteArray(32) { i -> cleanPk.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

        // 使用共享的 EthereumSigner 進行 EIP-155 標準簽名
        val signedTxHex = EthereumSigner.signLegacyTransaction(
            nonce = com.cbstudio.wearwallet.core.domain.model.quantities.Nonce.fromLong(nonce),
            gasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromWei(com.ionspin.kotlin.bignum.integer.BigInteger.parseString(gasPrice)),
            gasLimit = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromLong(gasLimit.toLong()),
            toAddress = com.cbstudio.wearwallet.core.domain.model.quantities.EvmAddress.fromHex(toAddress),
            value = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromWei(com.ionspin.kotlin.bignum.integer.BigInteger.parseString(value)),
            data = com.cbstudio.wearwallet.core.domain.model.quantities.Calldata.fromHex(data),
            privateKeyBytes = pkBytes,
            chainId = com.cbstudio.wearwallet.core.domain.model.quantities.ChainId.fromLong(chainId)
        )

        // 計算交易哈希（簽名交易的 Keccak-256）
        val txHashHex = calculateTransactionHash(signedTxHex)

        Result.Success(
            SignedTransaction(
                rawData = signedTxHex,
                signature = signedTxHex,  // 完整的簽名交易包含簽名
                chainType = chainType,
                hash = txHashHex
            )
        )
    } catch (e: Exception) {
        Result.Failure(
            SDKException.TransactionException(
                chainType,
                "簽名失敗: ${e.message}",
                e
            )
        )
    }
}

/**
 * 計算交易哈希
 */
private fun calculateTransactionHash(signedTxHex: String): String {
    // 移除 0x 前綴並轉換為字節陣列
    val cleanHex = signedTxHex.removePrefix("0x")
    val txBytes = hexToByteArray(cleanHex)

    // 計算 Keccak-256
    val hash = txBytes.keccak256()

    // 轉換為十六進制字符串
    return "0x" + hash.toHexString()
}

/**
 * 字節陣列轉十六進制字符串
 */
private fun ByteArray.toHexString(): String {
    return this.joinToString("") { byte ->
        val hex = (byte.toInt() and 0xFF).toString(16)
        if (hex.length == 1) "0$hex" else hex
    }
}

/**
 * 十六進制字符串轉字節陣列
 */
private fun hexToByteArray(hex: String): ByteArray {
    if (hex.isEmpty()) return byteArrayOf()
    return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}