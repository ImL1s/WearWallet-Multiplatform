package com.cbstudio.wearwallet.core.blockchain.signer

import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction

actual class DogecoinSigner {
    actual suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction {
        return SignedTransaction(
            hash = "",
            rawTransaction = "",
            success = false,
            error = "iOS DogecoinSigner is disabled: pending verified native WalletCore UTXO implementation"
        )
    }
}