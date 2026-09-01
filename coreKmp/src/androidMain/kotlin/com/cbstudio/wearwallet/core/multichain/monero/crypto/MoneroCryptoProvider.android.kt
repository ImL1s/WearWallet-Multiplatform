package com.cbstudio.wearwallet.core.multichain.monero.crypto

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException
import com.ionspin.kotlin.bignum.decimal.BigDecimal

actual fun getMoneroCryptoProvider(): MoneroCryptoProvider = AndroidMoneroCryptoProvider

object AndroidMoneroCryptoProvider : MoneroCryptoProvider {
    
    private const val TAG = "AndroidMoneroCrypto"

    override suspend fun deriveKeysFromMnemonic(
        mnemonic: String,
        password: String
    ): Result<MoneroKeys> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun generateAddress(
        publicSpendKey: ByteArray,
        publicViewKey: ByteArray,
        network: MoneroNetwork
    ): Result<String> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun scanForUTXOs(
        viewKey: String,
        address: String,
        fromHeight: Long,
        toHeight: Long?
    ): Result<List<MoneroUTXO>> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun createTransaction(
        inputs: List<MoneroUTXO>,
        outputs: List<TransactionOutput>,
        changeAddress: String,
        feeAmount: BigDecimal
    ): Result<SerializedTransaction> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun signTransaction(
        transaction: Any,
        privateKeys: List<ByteArray>
    ): Result<Any> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun broadcastTransaction(
        signedTransaction: Any
    ): Result<String> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun generateKeyImage(
        privateKey: ByteArray,
        publicKey: ByteArray
    ): Result<ByteArray> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun createMLSAGSignature(
        message: ByteArray,
        privateKeys: List<ByteArray>,
        publicKeys: List<List<ByteArray>>,
        realIndex: Int,
        keyImages: List<ByteArray>?
    ): Result<MLSAGSignature> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun verifyMLSAGSignature(
        message: ByteArray,
        signature: MLSAGSignature,
        publicKeys: List<List<ByteArray>>
    ): Result<Boolean> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun createPedersenCommitment(
        amount: BigDecimal,
        mask: ByteArray
    ): Result<ByteArray> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun createBulletproof(
        amounts: List<BigDecimal>,
        masks: List<ByteArray>
    ): Result<Bulletproof> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun ed25519ScalarMultBase(scalar: ByteArray): Result<ByteArray> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun ed25519ScalarMult(scalar: ByteArray, point: ByteArray): Result<ByteArray> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun ed25519PointAdd(p1: ByteArray, p2: ByteArray): Result<ByteArray> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun keccak256(data: ByteArray): Result<ByteArray> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun sha256(data: ByteArray): Result<ByteArray> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun base58Encode(data: ByteArray): Result<String> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    override suspend fun base58Decode(encoded: String): Result<ByteArray> {
        return Result.Failure(
            TypedUnsupportedTransactionException("Monero operation is unsupported in release")
        )
    }

    fun cleanup() {
        // No-op in release
    }
}