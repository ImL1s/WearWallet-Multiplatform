package com.cbstudio.wearwallet.core.domain.usecase.utxo

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.signer.*
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.Platform
import com.cbstudio.wearwallet.core.security.BuildType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.security.SignerImplementation
import com.cbstudio.wearwallet.core.security.WalletType
import com.cbstudio.wearwallet.core.security.BackendIdentity
import com.cbstudio.wearwallet.core.security.PrivateKeyManager
import com.cbstudio.wearwallet.core.security.PlatformProvider
import com.cbstudio.wearwallet.core.security.BuildTypeProvider
import com.cbstudio.wearwallet.core.security.BackendAttestationProvider
import com.cbstudio.wearwallet.core.security.TestPlatformProvider
import com.cbstudio.wearwallet.core.security.TestBuildTypeProvider
import com.cbstudio.wearwallet.core.security.DefaultBackendAttestationProvider
import com.cbstudio.wearwallet.core.security.RuntimeCapabilityContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 發送 UTXO 交易 UseCase
 * 處理 Bitcoin, Litecoin, Dogecoin, Bitcoin Cash 的交易發送
 */
class SendUTXOTransactionUseCase(
    private val walletRepository: WalletRepository,
    private val cryptoProvider: CryptoProvider,
    private val privateKeyManager: PrivateKeyManager,
    private val utxoApiClient: UTXOApiClient,
    private val bitcoinSigner: BitcoinSigner,
    private val litecoinSigner: LitecoinSigner,
    private val dogecoinSigner: DogecoinSigner,
    private val bitcoinCashSigner: BitcoinCashSigner,
    private val capabilityGate: CapabilityGate,
    private val platformProvider: PlatformProvider = TestPlatformProvider(),
    private val buildTypeProvider: BuildTypeProvider = TestBuildTypeProvider(),
    private val attestationProvider: BackendAttestationProvider = DefaultBackendAttestationProvider()
) {

    /**
     * 執行 UTXO 交易
     */
    suspend operator fun invoke(
        toAddress: String,
        amount: Long,
        chainType: ChainType,
        feeRate: Long,
        password: String
    ): Flow<Result<String>> = flow {
        try {
            emit(Result.Loading())

            // 0. Verification BEFORE any UTXO network request or private key access
            val ctx = ChainExecutionContextRegistry.resolve(chainType)
            val attestation = attestationProvider.getAttestation(ctx)
            val runtimeContext = RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                chainContext = ctx,
                walletType = WalletType.SOFTWARE_MNEMONIC,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
            val unsignedReq = CapabilityRequest.fromRuntime(
                operation = Operation.CREATE_UNSIGNED_TX,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            val signReq = CapabilityRequest.fromRuntime(
                operation = Operation.SOFTWARE_SIGN,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            val bcastReq = CapabilityRequest.fromRuntime(
                operation = Operation.BROADCAST,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(unsignedReq) ||
                !capabilityGate.verifyCapability(signReq) ||
                !capabilityGate.verifyCapability(bcastReq)) {
                emit(Result.Failure(UnsupportedOperationException("UTXO chain $chainType transactions are disabled in production")))
                return@flow
            }

            // 1. 獲取活動錢包
            val walletResult = walletRepository.getActiveWallet()
            if (walletResult !is Result.Success || walletResult.data == null) {
                emit(Result.Failure(Exception("沒有找到活動錢包")))
                return@flow
            }

            val wallet = walletResult.data

            // 2. 獲取 UTXOs
            val utxos = utxoApiClient.getUTXOs(wallet.address, chainType)
            if (utxos.isEmpty()) {
                emit(Result.Failure(Exception("沒有可用的 UTXO")))
                return@flow
            }

            // 3. 選擇 UTXOs 並計算找零
            val (selectedUTXOs, change) = selectUTXOs(utxos, amount, feeRate)

            // 4. 建構未簽名交易
            val unsignedTx = buildUnsignedTransaction(
                wallet.address,
                toAddress,
                amount,
                change,
                selectedUTXOs,
                feeRate
            )

            // 5. 獲取私鑰
            val privateKey = getPrivateKey(wallet, password, chainType)

            // 6. 簽名交易
            val signedTx = signTransaction(unsignedTx, privateKey, chainType)

            if (!signedTx.success) {
                emit(Result.Failure(Exception(signedTx.error ?: "簽名失敗")))
                return@flow
            }

            // 7. 廣播交易
            val txHash = utxoApiClient.broadcastTransaction(signedTx.rawTransaction, chainType)

            emit(Result.Success(txHash))
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }

    private fun selectUTXOs(
        utxos: List<UTXO>,
        targetAmount: Long,
        feeRate: Long
    ): Pair<List<UTXO>, Long> {
        val selected = mutableListOf<UTXO>()
        var accumulated = 0L

        for (utxo in utxos) {
            selected.add(utxo)
            accumulated += utxo.value

            val estimatedFee = estimateFee(selected.size, 2, feeRate)
            if (accumulated >= targetAmount + estimatedFee) {
                val change = accumulated - targetAmount - estimatedFee
                return Pair(selected, change)
            }
        }

        throw Exception("UTXO 餘額不足以支付金額與手續費")
    }

    private fun estimateFee(inputCount: Int, outputCount: Int, feeRate: Long): Long {
        val txSize = inputCount * 148 + outputCount * 34 + 10
        return txSize * feeRate
    }

    private fun buildUnsignedTransaction(
        fromAddress: String,
        toAddress: String,
        amount: Long,
        change: Long,
        utxos: List<UTXO>,
        feeRate: Long
    ): UnsignedTransaction {
        throw UnsupportedOperationException("UTXO transaction construction is disabled in production")
    }

    private suspend fun getPrivateKey(
        wallet: WalletAccount,
        password: String,
        chainType: ChainType
    ): String {
        throw UnsupportedOperationException("UTXO private key derivation is disabled in production")
    }

    private fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: String,
        chainType: ChainType
    ): SignedTransaction {
        throw UnsupportedOperationException("UTXO transaction signing is disabled in production")
    }
}