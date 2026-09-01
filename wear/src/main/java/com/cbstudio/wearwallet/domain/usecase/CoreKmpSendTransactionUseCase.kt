package com.cbstudio.wearwallet.domain.usecase

import com.cbstudio.wearwallet.bridge.CoreKmpBridge
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.shared.utils.Logger
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
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal

/**
 * 使用 CoreKmp 發送交易的 UseCase
 */
class CoreKmpSendTransactionUseCase(
    private val coreKmpBridge: CoreKmpBridge,
    private val capabilityGate: CapabilityGate,
    private val platformProvider: PlatformProvider = TestPlatformProvider(),
    private val buildTypeProvider: BuildTypeProvider = TestBuildTypeProvider(),
    private val attestationProvider: BackendAttestationProvider = DefaultBackendAttestationProvider()
) {
    
    companion object {
        private const val TAG = "CoreKmpSendTransactionUseCase"
    }

    private fun mapMultiChainTypeToChainType(multiChainType: MultiChainType): com.cbstudio.wearwallet.core.domain.model.ChainType {
        return try {
            com.cbstudio.wearwallet.core.domain.model.ChainType.valueOf(multiChainType.name)
        } catch (e: Exception) {
            throw TypedUnsupportedTransactionException(
                "Unknown chain type '${multiChainType.name}' cannot be mapped. Fail-closed: refusing to default to any chain."
            )
        }
    }
    
    suspend operator fun invoke(
        fromAddress: String,
        toAddress: String,
        amount: String,
        chainType: MultiChainType,
        tokenAddress: String? = null
    ): Flow<Result<String>> = flow {
        emit(Result.Loading())
        
        try {
            val chain = try {
                mapMultiChainTypeToChainType(chainType)
            } catch (e: Exception) {
                emit(Result.Failure(e))
                return@flow
            }

            val ctx = ChainExecutionContextRegistry.resolve(chain)
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
                emit(Result.Failure(
                    TypedUnsupportedTransactionException(
                        "Production capability gate fail-closed: ${chain.displayName} sending is disabled"
                    )
                ))
                return@flow
            }

            Logger.d(TAG, "開始創建交易: $chainType, $amount")
            
            // 1. 創建交易
            val createResult = coreKmpBridge.createTransaction(
                chainType = chainType,
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount,
                tokenAddress = tokenAddress
            )
            
            when (createResult) {
                is Result.Success -> {
                    val unsignedTx = createResult.data
                    Logger.d(TAG, "交易創建成功: ${unsignedTx.rawData}")
                    
                    // 2. 簽名並發送交易
                    val sendResult = coreKmpBridge.signAndSendTransaction(
                        chainType = chainType,
                        unsignedTx = unsignedTx
                    )
                    
                    when (sendResult) {
                        is Result.Success -> {
                            val txHash = sendResult.data
                            Logger.d(TAG, "交易發送成功: $txHash")
                            emit(Result.Success(txHash))
                        }
                        is Result.Failure -> {
                            Logger.e(TAG, "交易簽名發送失敗", sendResult.exception)
                            emit(Result.Failure(sendResult.exception))
                        }
                        else -> {
                            emit(Result.Failure(Exception("Unknown send result")))
                        }
                    }
                }
                is Result.Failure -> {
                    Logger.e(TAG, "交易創建失敗", createResult.exception)
                    emit(Result.Failure(createResult.exception))
                }
                else -> {
                    emit(Result.Failure(Exception("Unknown creation result")))
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "發送交易異常", e)
            emit(Result.Failure(e))
        }
    }
    
    /**
     * 估算 Gas 手續費 (fail-closed, no "0" or "21000" fallbacks)
     */
    suspend fun estimateGas(
        fromAddress: String,
        toAddress: String,
        amount: String,
        chainType: MultiChainType,
        tokenAddress: String? = null
    ): Result<GasEstimate> {
        return try {
            val createResult = coreKmpBridge.createTransaction(
                chainType = chainType,
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount,
                tokenAddress = tokenAddress
            )
            
            when (createResult) {
                is Result.Success -> {
                    val tx = createResult.data
                    val gPrice = tx.gasPrice ?: return Result.Failure(IllegalStateException("Gas price is missing in transaction estimate"))
                    val gLimit = tx.gasLimit ?: return Result.Failure(IllegalStateException("Gas limit is missing in transaction estimate"))
                    Result.Success(
                        GasEstimate(
                            gasPrice = gPrice,
                            gasLimit = gLimit,
                            totalFee = tx.fee
                        )
                    )
                }
                is Result.Failure -> {
                    Logger.e(TAG, "Gas 估算失敗", createResult.exception)
                    Result.Failure(createResult.exception)
                }
                else -> Result.Failure(Exception("Unknown gas estimation result"))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Gas 估算異常", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 檢查餘額是否足夠 (uses BigDecimal parsing, fail-closed)
     */
    suspend fun checkSufficientBalance(
        address: String,
        amount: String,
        chainType: MultiChainType
    ): Result<Boolean> {
        return try {
            val balanceResult = coreKmpBridge.getBalance(chainType, address)
            
            when (balanceResult) {
                is Result.Success -> {
                    val balance = balanceResult.data
                    val balanceBd = try { BigDecimal(balance.amount) } catch (e: Exception) {
                        return Result.Failure(IllegalArgumentException("Invalid balance amount: '${balance.amount}'"))
                    }
                    val sendBd = try { BigDecimal(amount) } catch (e: Exception) {
                        return Result.Failure(IllegalArgumentException("Invalid send amount: '$amount'"))
                    }
                    
                    Result.Success(balanceBd >= sendBd)
                }
                is Result.Failure -> {
                    Logger.e(TAG, "檢查餘額失敗", balanceResult.exception)
                    Result.Failure(balanceResult.exception)
                }
                else -> Result.Failure(Exception("Unknown balance result"))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "檢查餘額異常", e)
            Result.Failure(e)
        }
    }
}

/**
 * Gas 估算數據類
 */
data class GasEstimate(
    val gasPrice: String,
    val gasLimit: String,
    val totalFee: String
)