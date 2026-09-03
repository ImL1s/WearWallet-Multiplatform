package com.cbstudio.wearwallet.core.multichain.crypto

import com.cbstudio.wearwallet.core.common.Result

/**
 * TRON 交易簽名器介面 (Expect/Actual 模式)
 * 使用 ECDSA secp256k1 算法對 TRON 交易進行簽名
 */
expect class TronSigner() {
    /**
     * 對 TRON 交易的原始數據進行簽名
     *
     * @param rawDataHex 交易原始數據的十六進制字符串
     * @param privateKey 私鑰字節數組（32 字節）
     * @return 簽名結果（65 字節：r(32) + s(32) + v(1)）
     */
    suspend fun signTransaction(
        rawDataHex: String,
        privateKey: ByteArray
    ): Result<ByteArray>
}