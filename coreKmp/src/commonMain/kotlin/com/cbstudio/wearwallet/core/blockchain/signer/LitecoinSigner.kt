package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction

/**
 * Litecoin 交易簽名器 (Common)
 * 使用 expect/actual 模式實現跨平台
 */
expect class LitecoinSigner() {
    /**
     * 簽名 Litecoin 交易
     * @param unsignedTx 未簽名的交易
     * @param privateKey 私鑰字節數組
     * @return 簽名後的交易
     */
    suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction
}