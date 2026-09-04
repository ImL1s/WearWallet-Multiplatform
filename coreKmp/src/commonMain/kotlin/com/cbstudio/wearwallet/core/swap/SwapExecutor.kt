package com.cbstudio.wearwallet.core.swap

import com.cbstudio.wearwallet.core.common.BigInteger
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.tokens.ERC20TokenHandler
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.security.CryptoProvider
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
import com.cbstudio.wearwallet.core.zerox.model.ZeroXQuoteResponse
import com.cbstudio.wearwallet.core.multichain.util.RLPEncoder

/**
 * SwapExecutor - Orchestrates the complete swap execution flow
 */
class SwapExecutor(
    private val rpcClient: EthereumRpcClient,
    private val cryptoProvider: CryptoProvider,
    private val erc20Handler: ERC20TokenHandler,
    private val capabilityGate: CapabilityGate,
    private val platformProvider: PlatformProvider = TestPlatformProvider(),
    private val buildTypeProvider: BuildTypeProvider = TestBuildTypeProvider(),
    private val attestationProvider: BackendAttestationProvider = DefaultBackendAttestationProvider()
) {
    companion object {
        const val NATIVE_TOKEN_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        val MAX_UINT256: BigInteger = BigInteger.parseString(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16
        )
    }

    suspend fun executeEVMSwap(
        quote: ZeroXQuoteResponse,
        privateKey: String,
        chainType: ChainType,
        fromAddress: String,
        sellTokenAddress: String
    ): Result<String> {
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
            return Result.Failure(
                TypedUnsupportedTransactionException("Capability gate check failed in SwapExecutor for $chainType")
            )
        }

        return try {
            val isNativeToken = sellTokenAddress.lowercase() == NATIVE_TOKEN_ADDRESS
            
            if (!isNativeToken && quote.allowanceTarget != null) {
                val currentAllowance = checkAllowance(
                    owner = fromAddress,
                    spender = quote.allowanceTarget,
                    tokenAddress = sellTokenAddress,
                    chainType = chainType
                )
                
                val sellAmount = BigInteger.parseString(quote.sellAmount)
                
                if (currentAllowance < sellAmount) {
                    val approveResult = sendApproval(
                        tokenAddress = sellTokenAddress,
                        spender = quote.allowanceTarget,
                        amount = MAX_UINT256,
                        privateKey = privateKey,
                        fromAddress = fromAddress,
                        chainType = chainType
                    )
                    
                    when (approveResult) {
                        is Result.Success -> {
                            kotlinx.coroutines.delay(15000)
                        }
                        is Result.Failure -> return approveResult
                        is Result.Loading -> {}
                    }
                }
            }
            
            val gasValue = quote.gas ?: quote.estimatedGas 
                ?: throw IllegalStateException("Missing gas estimate in swap quote")
            val gasPriceValue = quote.gasPrice 
                ?: throw IllegalStateException("Missing gasPrice in swap quote")

            val swapTxHash = sendSwapTransaction(
                to = quote.to,
                data = quote.data,
                value = quote.value,
                gas = gasValue,
                gasPrice = gasPriceValue,
                privateKey = privateKey,
                fromAddress = fromAddress,
                chainType = chainType
            )
            
            swapTxHash
            
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun checkAllowance(
        owner: String,
        spender: String,
        tokenAddress: String,
        chainType: ChainType
    ): BigInteger {
        return try {
            val result = rpcClient.getAllowance(
                ownerAddress = owner,
                spenderAddress = spender,
                tokenAddress = tokenAddress,
                chainType = chainType
            )
            
            when (result) {
                is Result.Success -> {
                    val hexValue = result.data.removePrefix("0x")
                    if (hexValue.isEmpty() || hexValue == "0") {
                        BigInteger.ZERO
                    } else {
                        BigInteger.parseString(hexValue, 16)
                    }
                }
                else -> BigInteger.ZERO
            }
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }

    suspend fun sendApproval(
        tokenAddress: String,
        spender: String,
        amount: BigInteger,
        privateKey: String,
        fromAddress: String,
        chainType: ChainType
    ): Result<String> {
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
            return Result.Failure(
                TypedUnsupportedTransactionException("Capability gate check failed in SwapExecutor.sendApproval for $chainType")
            )
        }

        return try {
            val approveData = encodeApproveFunction(spender, amount)
            
            val nonceResult = rpcClient.getNonce(fromAddress, chainType)
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Failure -> throw nonceResult.exception
                is Result.Loading -> throw IllegalStateException("RPC timeout on getNonce")
            }
            
            val gasPriceResult = rpcClient.getGasPrice(chainType)
            val gasPrice = when (gasPriceResult) {
                is Result.Success -> gasPriceResult.data
                is Result.Failure -> throw gasPriceResult.exception
                is Result.Loading -> throw IllegalStateException("RPC timeout on getGasPrice")
            }
            
            val signedTx = buildAndSignTransaction(
                to = tokenAddress,
                value = "0",
                data = approveData,
                gasLimit = ERC20TokenHandler.APPROVE_GAS_LIMIT,
                gasPrice = gasPrice,
                nonce = nonce,
                chainType = chainType,
                privateKey = privateKey
            )
            
            rpcClient.sendRawTransaction(signedTx, chainType)
            
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private suspend fun sendSwapTransaction(
        to: String,
        data: String,
        value: String,
        gas: String,
        gasPrice: String,
        privateKey: String,
        fromAddress: String,
        chainType: ChainType
    ): Result<String> {
        return try {
            val nonceResult = rpcClient.getNonce(fromAddress, chainType)
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Failure -> throw nonceResult.exception
                is Result.Loading -> throw IllegalStateException("RPC timeout on getNonce")
            }
            
            val gasLimit = if (gas.startsWith("0x", ignoreCase = true)) {
                gas.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
                    ?: throw IllegalStateException("Invalid hex gas limit string: $gas")
            } else {
                gas.toLongOrNull(10)
                    ?: throw IllegalStateException("Invalid decimal gas limit string: $gas")
            }
            
            val signedTx = buildAndSignTransaction(
                to = to,
                value = value,
                data = data,
                gasLimit = gasLimit,
                gasPrice = gasPrice,
                nonce = nonce,
                chainType = chainType,
                privateKey = privateKey
            )
            
            rpcClient.sendRawTransaction(signedTx, chainType)
            
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private suspend fun buildAndSignTransaction(
        to: String,
        value: String,
        data: String,
        gasLimit: Long,
        gasPrice: String,
        nonce: Long,
        chainType: ChainType,
        privateKey: String
    ): String {
        val chainId = getChainId(chainType)
        
        val valueBigInt = try {
            if (value.startsWith("0x", ignoreCase = true)) {
                BigInteger.parseString(value.removePrefix("0x").removePrefix("0X"), 16)
            } else {
                BigInteger.parseString(value, 10)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid transaction value: $value", e)
        }
        
        val gasPriceBigInt = try {
            if (gasPrice.startsWith("0x", ignoreCase = true)) {
                BigInteger.parseString(gasPrice.removePrefix("0x").removePrefix("0X"), 16)
            } else {
                BigInteger.parseString(gasPrice, 10)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid gasPrice: $gasPrice", e)
        }
        
        val cleanPk = privateKey.removePrefix("0x").removePrefix("0X")
        require(cleanPk.length == 64) { "Private key must be 64 hex characters" }
        val pkBytes = ByteArray(32) { i -> cleanPk.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

        val signedHex = com.cbstudio.wearwallet.core.multichain.util.EthereumSigner.signLegacyTransaction(
            nonce = com.cbstudio.wearwallet.core.domain.model.quantities.Nonce.fromLong(nonce),
            gasPrice = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromWei(gasPriceBigInt),
            gasLimit = com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit.fromLong(gasLimit),
            toAddress = com.cbstudio.wearwallet.core.domain.model.quantities.EvmAddress.fromHex(to),
            value = com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromWei(valueBigInt),
            data = com.cbstudio.wearwallet.core.domain.model.quantities.Calldata.fromHex(data),
            privateKeyBytes = pkBytes,
            chainId = com.cbstudio.wearwallet.core.domain.model.quantities.ChainId.fromLong(chainId.toLong())
        )
        return if (signedHex.startsWith("0x")) signedHex else "0x$signedHex"
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        if (hex.isEmpty()) return ByteArray(0)
        val cleanHex = if (hex.length % 2 != 0) "0$hex" else hex
        return ByteArray(cleanHex.length / 2) { i ->
            val index = i * 2
            ((cleanHex[index].digitToInt(16) shl 4) + cleanHex[index + 1].digitToInt(16)).toByte()
        }
    }

    private fun encodeApproveFunction(spender: String, amount: BigInteger): String {
        val selector = "095ea7b3"
        val paddedSpender = spender.removePrefix("0x").lowercase().padStart(64, '0')
        val paddedAmount = amount.toString(16).padStart(64, '0')
        return "0x$selector$paddedSpender$paddedAmount"
    }

    private fun getChainId(chainType: ChainType): Int {
        return chainType.getChainId().toInt()
    }
}
