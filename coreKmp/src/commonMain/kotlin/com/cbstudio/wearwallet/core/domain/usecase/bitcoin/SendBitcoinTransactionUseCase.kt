package com.cbstudio.wearwallet.core.domain.usecase.bitcoin

import com.cbstudio.wearwallet.core.blockchain.adapter.BitcoinPlatformAdapter
import com.cbstudio.wearwallet.core.blockchain.model.FeePriority
import com.cbstudio.wearwallet.core.blockchain.signer.BitcoinSigner
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Network
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.Platform
import com.cbstudio.wearwallet.core.security.BuildType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.security.SignerImplementation
import com.cbstudio.wearwallet.core.security.WalletType
import com.cbstudio.wearwallet.core.security.BackendIdentity
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
 * 發送 Bitcoin 交易 UseCase
 */
class SendBitcoinTransactionUseCase(
    private val walletRepository: WalletRepository,
    private val bitcoinAdapter: BitcoinPlatformAdapter,
    private val bitcoinSigner: BitcoinSigner,
    private val capabilityGate: CapabilityGate,
    private val platformProvider: PlatformProvider = TestPlatformProvider(),
    private val buildTypeProvider: BuildTypeProvider = TestBuildTypeProvider(),
    private val attestationProvider: BackendAttestationProvider = DefaultBackendAttestationProvider()
) {
    /**
     * 發送 Bitcoin 交易
     */
    suspend fun execute(
        toAddress: String,
        amount: Long,
        feePriority: FeePriority = FeePriority.MEDIUM,
        network: Network = Network.BITCOIN_MAINNET
    ): Flow<Result<BitcoinTransactionResult>> = flow {
        emit(Result.Loading())

        try {
            // Fail-closed gate verification BEFORE any network / wallet operation
            val chainType = when (network) {
                Network.BITCOIN_MAINNET -> ChainType.BITCOIN
                Network.BITCOIN_TESTNET -> ChainType.BITCOIN
                else -> ChainType.BITCOIN
            }
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
            if (!capabilityGate.verifyCapability(signReq) ||
                !capabilityGate.verifyCapability(bcastReq)) {
                throw UnsupportedOperationException("Bitcoin transactions are fail-closed and disabled in production")
            }

            // 1. 獲取當前錢包
            val walletResult = walletRepository.getActiveWallet()
            val wallet = when (walletResult) {
                is Result.Success -> walletResult.data
                    ?: throw Exception("No active wallet")
                is Result.Failure -> throw walletResult.exception
                else -> throw Exception("Failed to get wallet")
            }

            // 2. 設置網路
            bitcoinAdapter.currentNetwork = network

            // 3. 驗證地址
            if (!bitcoinAdapter.validateAddress(toAddress)) {
                throw IllegalArgumentException("Invalid Bitcoin address: $toAddress")
            }

            // 4. 檢查餘額
            val balance = bitcoinAdapter.getBalance(wallet.address)
            if (balance < amount) {
                throw InsufficientBalanceException(
                    "Insufficient balance. Available: $balance, Required: $amount"
                )
            }

            // 5. 創建未簽名交易
            val unsignedTx = bitcoinAdapter.createTransaction(
                from = wallet.address,
                to = toAddress,
                amount = amount
            )

            // Bitcoin production signing disabled until native vector verification
            throw UnsupportedOperationException("Bitcoin hardware/software signing implementation pending security verification")
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }

    /**
     * 估算交易手續費
     */
    suspend fun estimateFee(
        toAddress: String,
        amount: Long,
        feePriority: FeePriority = FeePriority.MEDIUM
    ): Result<Long> {
        return try {
            val walletResult = walletRepository.getActiveWallet()
            val wallet = when (walletResult) {
                is Result.Success -> walletResult.data
                    ?: throw Exception("No active wallet")
                is Result.Failure -> throw walletResult.exception
                else -> throw Exception("Failed to get wallet")
            }

            val fee = bitcoinAdapter.estimateFee(
                from = wallet.address,
                to = toAddress,
                amount = amount
            )

            Result.Success(fee)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}

/**
 * Bitcoin 交易結果
 */
data class BitcoinTransactionResult(
    val txHash: String,
    val from: String,
    val to: String,
    val amount: Long,
    val fee: Long,
    val network: Network
)

/**
 * 餘額不足異常
 */
class InsufficientBalanceException(message: String) : Exception(message)