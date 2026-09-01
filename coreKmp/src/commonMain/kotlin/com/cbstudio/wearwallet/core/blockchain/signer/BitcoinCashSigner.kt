package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction

/**
 * Bitcoin Cash 交易簽名器 (Common)
 * 使用 expect/actual 模式實現跨平台
 * Bitcoin Cash 特殊要求：
 * 1. 使用 SIGHASH_FORKID (0x40) 標誌
 * 2. 支援 CashAddr 地址格式
 * 3. 支援 32MB 區塊
 */
expect class BitcoinCashSigner() {
    /**
     * 簽名 Bitcoin Cash 交易
     * @param unsignedTx 未簽名的交易
     * @param privateKey 私鑰字節數組
     * @return 簽名後的交易
     */
    suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction
    
    /**
     * 使用助記詞簽名交易
     * @param unsignedTx 未簽名的交易
     * @param mnemonic 助記詞
     * @param passphrase 密碼短語（可選）
     * @param derivationPath 派生路徑（預設 BCH 路徑）
     * @return 簽名後的交易
     */
    suspend fun signWithMnemonic(
        unsignedTx: UnsignedTransaction,
        mnemonic: String,
        passphrase: String = "",
        derivationPath: String = "m/44'/145'/0'/0/0"
    ): SignedTransaction
}