package com.cbstudio.wearwallet.core.domain.usecase.swap

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.rango.RangoRepository
import com.cbstudio.wearwallet.core.rango.model.RangoSwapResponse
import com.cbstudio.wearwallet.core.rango.model.RangoTokenMeta
import com.cbstudio.wearwallet.core.swap.SwapExecutor
import com.cbstudio.wearwallet.core.zerox.model.ZeroXQuoteResponse

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

class ExecuteSwapUseCase(
    private val rangoRepository: RangoRepository,
    private val swapExecutor: SwapExecutor,
    private val capabilityGate: CapabilityGate,
    private val platformProvider: PlatformProvider = TestPlatformProvider(),
    private val buildTypeProvider: BuildTypeProvider = TestBuildTypeProvider(),
    private val attestationProvider: BackendAttestationProvider = DefaultBackendAttestationProvider()
) {
    suspend operator fun invoke(params: Params): Result<Success> {
        val chainType = ChainType.fromRangoChainName(params.fromToken.blockchain)
            ?: return Result.Failure(Exception("Unsupported chain: ${params.fromToken.blockchain}"))

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
            return Result.Failure(TypedUnsupportedTransactionException("Production capability gate fail-closed: swap execution for ${chainType.displayName} is disabled"))
        }

        // 1. Create Swap Transaction (Get data from Rango)
        val txResult = rangoRepository.createSwapTransaction(
            fromChain = params.fromToken.blockchain,
            fromTokenSymbol = if (params.fromToken.isNative) null else params.fromToken.address,
            toChain = params.toToken.blockchain,
            toTokenSymbol = if (params.toToken.isNative) null else params.toToken.address,
            amount = params.amountInWei,
            fromAddress = params.walletAddress,
            toAddress = params.walletAddress, // Receive at same address
            slippage = params.slippage
        )

        return txResult.fold(
            onSuccess = { swapResponse ->
                val tx = swapResponse.transaction
                
                if (tx == null) {
                    return@fold Result.Failure(Exception(swapResponse.error ?: "No transaction data received from Rango"))
                }

                val toAddress = tx.to
                val txData = tx.data
                val txValue = tx.value

                if (toAddress.isNullOrBlank() || txData.isNullOrBlank() || txValue.isNullOrBlank()) {
                    return@fold Result.Failure(IllegalArgumentException("Rango response is missing mandatory transaction target/data/value fields"))
                }

                // 2. Map to ZeroXQuoteResponse (internal adapter for SwapExecutor)
                val zeroXQuote = ZeroXQuoteResponse(
                    chainId = getChainId(params.fromToken.blockchain),
                    price = "1",
                    grossPrice = null,
                    value = txValue,
                    gasPrice = tx.gasPrice,
                    gas = tx.gasLimit,
                    estimatedGas = tx.gasLimit,
                    protocolFee = null,
                    minimumProtocolFee = null,
                    buyTokenAddress = params.toToken.address ?: "",
                    buyAmount = swapResponse.route?.outputAmount ?: return@fold Result.Failure(IllegalArgumentException("Missing swap route output amount")),
                    grossBuyAmount = null,
                    sellTokenAddress = params.fromToken.address ?: "",
                    sellAmount = params.amountInWei,
                    grossSellAmount = null,
                    allowanceTarget = toAddress,
                    to = toAddress,
                    data = txData,
                    decodedUniqueId = null,
                    guaranteedPrice = null
                )
                
                val targetChainType = ChainType.fromRangoChainName(params.fromToken.blockchain) 
                    ?: return@fold Result.Failure(Exception("Unsupported chain: ${params.fromToken.blockchain}"))

                // 3. Execute Transaction
                val execResult = swapExecutor.executeEVMSwap(
                    quote = zeroXQuote,
                    privateKey = params.privateKey,
                    chainType = targetChainType,
                    fromAddress = params.walletAddress,
                    sellTokenAddress = params.fromToken.address ?: SwapExecutor.NATIVE_TOKEN_ADDRESS
                )

                when (execResult) {
                    is Result.Success -> Result.Success(
                        Success(
                            txHash = execResult.data,
                            requestId = swapResponse.requestId,
                            isCrossChain = params.fromToken.blockchain != params.toToken.blockchain
                        )
                    )
                    is Result.Failure -> Result.Failure(execResult.exception)
                    is Result.Loading -> Result.Loading()
                }
            },
            onFailure = {
                Result.Failure(it as? Exception ?: Exception(it))
            }
        )
    }
    
    private fun getChainId(chain: String): Int {
         return ChainType.fromRangoChainName(chain)?.getChainId()?.toInt()
             ?: throw IllegalArgumentException("Unknown Rango chain ID for '$chain'")
    }

    data class Params(
        val fromToken: RangoTokenMeta,
        val toToken: RangoTokenMeta,
        val amountInWei: String,
        val walletAddress: String,
        val privateKey: String = "",
        val slippage: Double = 1.0
    )

    data class Success(
        val txHash: String,
        val requestId: String?,
        val isCrossChain: Boolean
    )
}
