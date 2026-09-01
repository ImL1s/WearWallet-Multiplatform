package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.cbstudio.wearwallet.core.blockchain.adapter.AddressType

/**
 * Dogecoin 簽名器
 * 預期由各平台實現具體簽名邏輯
 */
expect class DogecoinSigner() {
    /**
     * 簽名 Dogecoin 交易
     */
    suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction
    
    /**
     * 生成 Dogecoin 地址
     */
    suspend fun generateAddress(
        publicKey: ByteArray,
        addressType: AddressType = AddressType.LEGACY
    ): String
    
    /**
     * 驗證 Dogecoin 地址格式
     */
    suspend fun validateAddress(address: String): Boolean
    
    /**
     * 估算交易手續費
     */
    suspend fun estimateFee(
        utxos: List<UTXO>,
        outputCount: Int,
        feeRate: Long
    ): Long
}