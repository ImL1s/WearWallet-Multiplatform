package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.multichain.MultiChainType

/**
 * 地址派生介面
 * 從助記詞派生各鏈的地址
 */
expect class AddressDerivation() {
    /**
     * 從助記詞派生指定鏈的地址
     */
    fun deriveAddress(mnemonic: String, chainType: MultiChainType): String
    
    /**
     * 從助記詞派生指定鏈的私鑰
     */
    fun derivePrivateKey(mnemonic: String, chainType: MultiChainType): ByteArray
}