package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.Platform
import com.cbstudio.wearwallet.core.security.BuildType
import com.cbstudio.wearwallet.core.security.WalletType
import com.cbstudio.wearwallet.core.security.BackendIdentity
import com.cbstudio.wearwallet.core.security.Network
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationRequiredException
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import com.cbstudio.wearwallet.core.security.SecureKeyManager
import com.cbstudio.wearwallet.core.security.toHexString
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.security.SignerImplementation
import com.cbstudio.wearwallet.core.security.PlatformProvider
import com.cbstudio.wearwallet.core.security.BuildTypeProvider
import com.cbstudio.wearwallet.core.security.BackendAttestationProvider
import com.cbstudio.wearwallet.core.security.TestPlatformProvider
import com.cbstudio.wearwallet.core.security.TestBuildTypeProvider
import com.cbstudio.wearwallet.core.security.DefaultBackendAttestationProvider
import com.cbstudio.wearwallet.core.security.RuntimeCapabilityContext
import com.cbstudio.wearwallet.core.multichain.util.EthereumSigner
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.cbstudio.wearwallet.core.domain.model.keystone.KeystoneTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString

/**
 * 發送交易 Use Case
 * Refactored in M3 to sign transactions via SecureKeyManager without exporting private keys to domain/UI.
 */
class SendTransactionUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val cryptoProvider: CryptoProvider,
    private val secureStorage: SecureStorage,
    private val capabilityGate: CapabilityGate,
    private val secureKeyManager: SecureKeyManager,
    private val platformProvider: PlatformProvider = TestPlatformProvider(),
    private val buildTypeProvider: BuildTypeProvider = TestBuildTypeProvider(),
    private val attestationProvider: BackendAttestationProvider = DefaultBackendAttestationProvider()
) {
    /**
     * Primary entry point: accepts domain-owned [ConfirmedEvmTransactionIntent] and optional [AuthenticationContext].
     * Never exports raw private keys to domain/UI.
     */
    suspend operator fun invoke(
        intent: ConfirmedEvmTransactionIntent,
        authContext: AuthenticationContext? = null
    ): Flow<Result<String>> = flow {
        try {
            // 1. Pre-signing intent validation (includes canonical ChainExecutionContext validation)
            validateIntentPreSigning(intent)
            val chainContext = intent.executionContext
            val chainType = chainContext.chain
            val chainId = ChainId(chainContext.chainId)

            // Validate auth handle if provided
            authContext?.authHandle?.let { handle ->
                if (handle.isInvalidated) {
                    throw AuthenticationRequiredException("Auth handle for key '${intent.keyAlias}' is invalidated")
                }
                if (handle.isExpired()) {
                    throw AuthenticationRequiredException("Auth handle for key '${intent.keyAlias}' has expired")
                }
                if (handle.keyId.isNotEmpty() && handle.keyId != intent.keyAlias) {
                    throw AuthenticationRequiredException("Cross-key handle rejected: expected keyId '${intent.keyAlias}' but got '${handle.keyId}'")
                }
                if (handle.operation != AuthOperation.SIGN) {
                    throw AuthenticationRequiredException("Auth handle operation '${handle.operation}' is not SIGN")
                }
                if (handle.walletId.isNotEmpty() && intent.walletId.isNotEmpty() && handle.walletId != intent.walletId) {
                    throw AuthenticationRequiredException("Cross-wallet handle rejected: expected walletId '${intent.walletId}' but got '${handle.walletId}'")
                }
                if (handle.intentFingerprint.isNotEmpty() &&
                    !handle.intentFingerprint.equals(intent.signingDigestHex, ignoreCase = true) &&
                    !handle.intentFingerprint.equals(intent.canonicalFingerprint, ignoreCase = true)
                ) {
                    throw AuthenticationRequiredException("Intent fingerprint mismatch in auth handle: expected '${intent.signingDigestHex}' but got '${handle.intentFingerprint}'")
                }
            }

            // Envelope gate (Supports LEGACY and EIP-1559)
            when (intent.envelopeType) {
                EvmEnvelope.LEGACY,
                EvmEnvelope.EIP1559 -> {}
                else -> throw TypedUnsupportedTransactionException("Unsupported envelope type: ${intent.envelopeType}")
            }

            // 2. Capability Gate check (Operation-aware SOFTWARE_SIGN)
            val attestation = attestationProvider.getAttestation(chainContext)
            val signRuntimeContext = RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                chainContext = chainContext,
                walletType = WalletType.SOFTWARE_MNEMONIC,
                envelopeType = intent.envelopeType,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
            val signCapabilityRequest = CapabilityRequest.fromRuntime(
                operation = Operation.SOFTWARE_SIGN,
                runtimeContext = signRuntimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(signCapabilityRequest)) {
                throw TypedUnsupportedTransactionException("Production capability gate fail-closed: ${intent.chain} software sending is disabled")
            }

            // 3. Nonce re-check before signing (Fail-closed on RPC failure or mismatch)
            val currentNonce = try {
                transactionRepository.getNonce(intent.sender.value, chainContext)
            } catch (e: Exception) {
                throw TypedNonceChangedException(intent.nonce.toLong(), -1L)
            }
            if (currentNonce != intent.nonce.toLong()) {
                throw TypedNonceChangedException(intent.nonce.toLong(), currentNonce)
            }

            // 4. Build transaction parameters & compute canonical unsigned transaction digest
            val isTokenTransfer = intent.tokenContract != null
            val txTo = if (isTokenTransfer) intent.tokenContract!!.value else intent.recipient.value
            val txValue = if (isTokenTransfer) BigInteger.ZERO else intent.nativeValue.value
            val txData = intent.calldata

            val rawTxDigest: ByteArray = when (intent.envelopeType) {
                EvmEnvelope.LEGACY -> {
                    EthereumSigner.computeLegacyTransactionDigest(
                        nonce = intent.nonce,
                        gasPrice = intent.gasPrice,
                        gasLimit = intent.gasLimit,
                        toAddress = EvmAddress.fromString(txTo),
                        value = Wei.fromWei(txValue),
                        data = txData,
                        chainId = chainId
                    )
                }
                EvmEnvelope.EIP1559 -> {
                    EthereumSigner.computeEip1559TransactionDigest(
                        chainId = chainId,
                        nonce = intent.nonce,
                        maxPriorityFeePerGas = intent.gasPrice,
                        maxFeePerGas = intent.gasPrice,
                        gasLimit = intent.gasLimit,
                        toAddress = EvmAddress.fromString(txTo),
                        value = Wei.fromWei(txValue),
                        data = txData
                    )
                }
                else -> throw TypedUnsupportedTransactionException("Unsupported envelope type: ${intent.envelopeType}")
            }

            // 5. Sign transaction digest inside SecureKeyManager (No private key exported)
            require(rawTxDigest.toHexString().equals(intent.signingDigestHex, ignoreCase = true)) {
                "Computed txDigest (${rawTxDigest.toHexString()}) mismatch with intent.signingDigestHex (${intent.signingDigestHex})"
            }

            val signResult = secureKeyManager.signWithKey(
                keyId = intent.keyAlias,
                data = rawTxDigest,
                authContext = authContext,
                expectedWalletId = intent.walletId
            )

            val signatureBytes = when (signResult) {
                is Result.Success -> signResult.data
                is Result.Failure -> throw signResult.exception
                is Result.Loading -> throw IllegalStateException("Signing in progress")
            }

            // 6. Reconstruct signed raw transaction with recovery signature
            val signedTx = when (intent.envelopeType) {
                EvmEnvelope.LEGACY -> {
                    EthereumSigner.reconstructSignedLegacyTransaction(
                        nonce = intent.nonce,
                        gasPrice = intent.gasPrice,
                        gasLimit = intent.gasLimit,
                        toAddress = EvmAddress.fromString(txTo),
                        value = Wei.fromWei(txValue),
                        data = txData,
                        chainId = chainId,
                        signatureBytes = signatureBytes
                    )
                }
                EvmEnvelope.EIP1559 -> {
                    EthereumSigner.reconstructSignedEip1559Transaction(
                        chainId = chainId,
                        nonce = intent.nonce,
                        maxPriorityFeePerGas = intent.gasPrice,
                        maxFeePerGas = intent.gasPrice,
                        gasLimit = intent.gasLimit,
                        toAddress = EvmAddress.fromString(txTo),
                        value = Wei.fromWei(txValue),
                        data = txData,
                        signatureBytes = signatureBytes
                    )
                }
                else -> throw TypedUnsupportedTransactionException("Unsupported envelope type: ${intent.envelopeType}")
            }

            // 7. Post-signing verification: recover sender address and assert 1-by-1 intent match
            EthereumSigner.verifySignedTransactionMatchesIntent(
                signedTxHex = signedTx,
                intent = intent
            )

            // 8. Broadcast capability gate check
            val broadcastRuntimeContext = RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                chainContext = chainContext,
                walletType = WalletType.SOFTWARE_MNEMONIC,
                envelopeType = intent.envelopeType,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
            val broadcastRequest = CapabilityRequest.fromRuntime(
                operation = Operation.BROADCAST,
                runtimeContext = broadcastRuntimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(broadcastRequest)) {
                throw TypedUnsupportedTransactionException("Capability gate check failed for Operation.BROADCAST on $chainType")
            }

            // 9. Send transaction to network
            val txHash = transactionRepository.sendTransaction(
                signedTransaction = signedTx,
                context = chainContext
            )

            emit(Result.Success(txHash))

        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }

    /**
     * Enforce strict pre-signing validation on [ConfirmedEvmTransactionIntent].
     */
    private fun validateIntentPreSigning(intent: ConfirmedEvmTransactionIntent) {
        require(intent.walletId.isNotBlank()) { "walletId in intent must not be blank" }
        require(intent.keyAlias.isNotBlank()) { "keyAlias in intent must not be blank" }
        require(intent.sender.value.matches(Regex("^0x[0-9a-fA-F]{40}$"))) { "Invalid sender address in intent: ${intent.sender.value}" }
        require(intent.recipient.value.matches(Regex("^0x[0-9a-fA-F]{40}$"))) { "Invalid recipient address in intent: ${intent.recipient.value}" }
        require(intent.humanAmount.isNotBlank()) { "humanAmount in intent must not be blank" }

        val canonicalContext = ChainExecutionContextRegistry.resolve(
            intent.executionContext.multiChainType,
            intent.executionContext.networkType
        )
        require(intent.executionContext == canonicalContext) {
            "Pre-signing validation failed: executionContext is not canonical ($canonicalContext vs ${intent.executionContext})"
        }
        require(intent.chain == intent.executionContext.multiChainType) {
            "Pre-signing validation failed: intent.chain ${intent.chain} mismatch with executionContext.multiChainType ${intent.executionContext.multiChainType}"
        }

        val computedFingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
            walletId = intent.walletId,
            keyAlias = intent.keyAlias,
            sender = intent.sender,
            chain = intent.chain,
            executionContext = intent.executionContext,
            envelopeType = intent.envelopeType,
            recipient = intent.recipient,
            tokenContract = intent.tokenContract,
            tokenSymbol = intent.tokenSymbol,
            tokenDecimals = intent.tokenDecimals,
            humanAmount = intent.humanAmount,
            baseUnitAmount = intent.baseUnitAmount,
            nativeValue = intent.nativeValue,
            calldata = intent.calldata,
            nonce = intent.nonce,
            gasPrice = intent.gasPrice,
            gasLimit = intent.gasLimit,
            fee = intent.fee
        )
        require(intent.canonicalFingerprint == computedFingerprint) {
            "Pre-signing intent validation failed: canonicalFingerprint mismatch"
        }

        val expectedFee = intent.gasPrice.value * BigInteger.fromLong(intent.gasLimit.value)
        require(intent.fee.value == expectedFee) {
            "Pre-signing intent validation failed: fee ${intent.fee.value} does not match gasPrice * gasLimit ($expectedFee)"
        }

        if (intent.tokenContract != null) {
            require(intent.tokenDecimals != null && intent.tokenDecimals in 0..77) {
                "Pre-signing intent validation failed: tokenDecimals missing or invalid"
            }
            val cleanRecipient = intent.recipient.value.removePrefix("0x").lowercase().padStart(64, '0')
            val cleanAmount = intent.baseUnitAmount.value.toString(16).lowercase().padStart(64, '0')
            val expectedCalldataHex = "0xa9059cbb$cleanRecipient$cleanAmount"
            require(intent.calldata.toHex().lowercase() == expectedCalldataHex.lowercase()) {
                "Pre-signing intent validation failed: ERC-20 calldata mismatch. Expected $expectedCalldataHex, got ${intent.calldata.toHex()}"
            }
        }
    }

    /**
     * Legacy entry point: constructs a [ConfirmedEvmTransactionIntent] from string parameters and delegates.
     */
    suspend operator fun invoke(
        toAddress: String,
        amount: String,
        tokenAddress: String? = null,
        tokenDecimals: Int? = null,
        gasPrice: String? = null,
        gasLimit: String? = null
    ): Flow<Result<String>> = flow {
        try {
            val walletResult = walletRepository.getActiveWallet()
            val wallet = when (walletResult) {
                is Result.Success -> walletResult.data ?: throw Exception("No active wallet found")
                is Result.Failure -> throw walletResult.exception
                is Result.Loading -> throw Exception("Wallet loading")
            }

            if (wallet.isHardwareWallet) {
                emit(Result.Failure(Exception("Hardware wallet signing not yet implemented")))
                return@flow
            }

            val resolvedContext = ChainExecutionContextRegistry.resolve(wallet.chainType)
            val legacyAttestation = attestationProvider.getAttestation(resolvedContext)
            val legacyRuntimeContext = RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                chainContext = resolvedContext,
                walletType = WalletType.SOFTWARE_MNEMONIC,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
            val legacyReq = CapabilityRequest.fromRuntime(
                operation = Operation.SOFTWARE_SIGN,
                runtimeContext = legacyRuntimeContext,
                attestation = legacyAttestation
            )
            if (!capabilityGate.verifyCapability(legacyReq)) {
                throw TypedUnsupportedTransactionException("Production capability gate fail-closed: ${wallet.chainType} software sending is disabled")
            }

            val isTokenTransfer = !tokenAddress.isNullOrBlank()
            if (isTokenTransfer && tokenDecimals == null) {
                throw IllegalArgumentException(
                    "tokenDecimals is required when tokenAddress is specified. " +
                    "Do not default to 18 — USDC/USDT use 6 decimals."
                )
            }
            val decimals = tokenDecimals ?: 18
            val baseUnitAmount = BaseUnitAmount.fromDecimalString(amount, decimals)

            val recipientAddr = EvmAddress.fromString(toAddress)
            val senderAddr = EvmAddress.fromString(wallet.address)
            val tokenContractAddr = if (isTokenTransfer) EvmAddress.fromString(tokenAddress!!) else null

            val nativeVal = if (isTokenTransfer) Wei.ZERO else Wei.fromWei(baseUnitAmount.value)

            val calldataVal = if (isTokenTransfer) {
                val cleanRecipient = toAddress.removePrefix("0x").lowercase().padStart(64, '0')
                val cleanAmount = baseUnitAmount.value.toString(16).lowercase().padStart(64, '0')
                Calldata.fromHex("0xa9059cbb$cleanRecipient$cleanAmount")
            } else {
                Calldata.EMPTY
            }

            val (txToAddress, txValueHex, txDataHex) = if (isTokenTransfer) {
                Triple(tokenAddress!!, "0x0", calldataVal.toHex())
            } else {
                Triple(toAddress, "0x" + baseUnitAmount.value.toString(16), "")
            }

            val request = TransactionRequest(
                from = wallet.address,
                to = txToAddress,
                value = txValueHex,
                gasPrice = gasPrice,
                gasLimit = gasLimit,
                chainType = wallet.chainType,
                tokenAddress = tokenAddress,
                data = txDataHex
            )

            val finalGasLimitStr = gasLimit ?: try {
                transactionRepository.estimateGas(request)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to estimate gas: ${e.message}", e)
            }
            val gasLimitObj = GasLimit.fromDecimalString(finalGasLimitStr)

            val finalGasPriceStr = gasPrice ?: try {
                transactionRepository.getGasPrice(wallet.chainType)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to retrieve gas price: ${e.message}", e)
            }
            val weiGasPrice = if (finalGasPriceStr.startsWith("0x") || finalGasPriceStr.startsWith("0X")) {
                Wei.fromWeiHex(finalGasPriceStr)
            } else {
                Wei.fromWeiDecimal(finalGasPriceStr)
            }

            val nonceValue = try {
                transactionRepository.getNonce(wallet.address, wallet.chainType)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to retrieve nonce: ${e.message}", e)
            }
            val nonceObj = Nonce.fromLong(nonceValue)

            val feeWei = Wei.fromWei(weiGasPrice.value * BigInteger.fromLong(gasLimitObj.toLong()))
            val executionContext = ChainExecutionContextRegistry.resolve(wallet.chainType)
            val multiChain = executionContext.multiChainType

            val keyAlias = wallet.keyAlias?.takeIf { it.isNotBlank() } ?: wallet.id
            val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                walletId = wallet.id,
                keyAlias = keyAlias,
                sender = senderAddr,
                chain = multiChain,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipientAddr,
                tokenContract = tokenContractAddr,
                tokenSymbol = if (isTokenTransfer) "TOKEN" else null,
                tokenDecimals = tokenDecimals,
                humanAmount = amount,
                baseUnitAmount = baseUnitAmount,
                nativeValue = nativeVal,
                calldata = calldataVal,
                nonce = nonceObj,
                gasPrice = weiGasPrice,
                gasLimit = gasLimitObj,
                fee = feeWei
            )

            val intent = ConfirmedEvmTransactionIntent(
                walletId = wallet.id,
                keyAlias = keyAlias,
                sender = senderAddr,
                chain = multiChain,
                executionContext = executionContext,
                envelopeType = EvmEnvelope.LEGACY,
                recipient = recipientAddr,
                tokenContract = tokenContractAddr,
                tokenSymbol = if (isTokenTransfer) "TOKEN" else null,
                tokenDecimals = tokenDecimals,
                humanAmount = amount,
                baseUnitAmount = baseUnitAmount,
                nativeValue = nativeVal,
                calldata = calldataVal,
                nonce = nonceObj,
                gasPrice = weiGasPrice,
                gasLimit = gasLimitObj,
                fee = feeWei,
                canonicalFingerprint = fingerprint
            )

            invoke(intent).collect { emit(it) }
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }

    /**
     * 建立未簽名交易 (用於硬體錢包)
     */
    suspend fun createUnsignedTransaction(
        toAddress: String,
        amount: String,
        tokenAddress: String? = null,
        tokenDecimals: Int? = null,
        gasPrice: String? = null,
        gasLimit: String? = null
    ): Result<String> {
        return try {
            val walletResult = walletRepository.getActiveWallet()
            val wallet = when (walletResult) {
                is Result.Success -> walletResult.data ?: throw Exception("No active wallet found")
                is Result.Failure -> throw walletResult.exception
                is Result.Loading -> throw Exception("Wallet loading")
            }

            val resolvedCtx = ChainExecutionContextRegistry.resolve(wallet.chainType)
            val signerImpl = when (wallet.walletType) {
                com.cbstudio.wearwallet.core.domain.model.WalletType.KEYSTONE,
                com.cbstudio.wearwallet.core.domain.model.WalletType.KEYSTONE_COLD -> SignerImplementation.KEYSTONE_HARDWARE
                com.cbstudio.wearwallet.core.domain.model.WalletType.LEDGER,
                com.cbstudio.wearwallet.core.domain.model.WalletType.TREZOR -> SignerImplementation.NATIVE_HARDWARE
                else -> SignerImplementation.SOFTWARE_LOCAL
            }
            val capWalletType = when (wallet.walletType) {
                com.cbstudio.wearwallet.core.domain.model.WalletType.KEYSTONE,
                com.cbstudio.wearwallet.core.domain.model.WalletType.KEYSTONE_COLD -> com.cbstudio.wearwallet.core.security.WalletType.KEYSTONE_XPUB
                com.cbstudio.wearwallet.core.domain.model.WalletType.LEDGER,
                com.cbstudio.wearwallet.core.domain.model.WalletType.TREZOR -> com.cbstudio.wearwallet.core.security.WalletType.HARDWARE_BLE
                com.cbstudio.wearwallet.core.domain.model.WalletType.HOT_WALLET,
                com.cbstudio.wearwallet.core.domain.model.WalletType.MNEMONIC -> com.cbstudio.wearwallet.core.security.WalletType.SOFTWARE_MNEMONIC
                com.cbstudio.wearwallet.core.domain.model.WalletType.PRIVATE_KEY -> com.cbstudio.wearwallet.core.security.WalletType.SOFTWARE_PRIVATE_KEY
                com.cbstudio.wearwallet.core.domain.model.WalletType.WATCH_ONLY -> com.cbstudio.wearwallet.core.security.WalletType.READ_ONLY
                com.cbstudio.wearwallet.core.domain.model.WalletType.MULTI_SIG -> com.cbstudio.wearwallet.core.security.WalletType.UNSUPPORTED
            }
            val unsignedAttestation = attestationProvider.getAttestation(resolvedCtx)
            val unsignedRuntimeContext = RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                chainContext = resolvedCtx,
                walletType = capWalletType,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = signerImpl
            )
            val unsignedReq = CapabilityRequest.fromRuntime(
                operation = Operation.CREATE_UNSIGNED_TX,
                runtimeContext = unsignedRuntimeContext,
                attestation = unsignedAttestation
            )
            if (!capabilityGate.verifyCapability(unsignedReq)) {
                return Result.Failure(TypedUnsupportedTransactionException("Capability gate check failed for hardware wallet unsigned transaction creation"))
            }

            val isTokenTransfer = !tokenAddress.isNullOrBlank()
            if (isTokenTransfer && tokenDecimals == null) {
                throw IllegalArgumentException(
                    "tokenDecimals is required when tokenAddress is specified."
                )
            }
            val decimals = tokenDecimals ?: 18
            val baseUnitAmount = BaseUnitAmount.fromDecimalString(amount, decimals)
            val parsedAmountBaseUnits = baseUnitAmount.value

            val (txToAddress, txValueHex, txDataHex) = if (isTokenTransfer) {
                val cleanToken = tokenAddress!!.removePrefix("0x")
                val cleanRecipient = toAddress.removePrefix("0x")
                if (!cleanToken.matches(Regex("^[0-9a-fA-F]{40}$")) || !cleanRecipient.matches(Regex("^[0-9a-fA-F]{40}$"))) {
                    throw IllegalArgumentException("Invalid ERC-20 token address or recipient address")
                }
                val paddedRecipient = cleanRecipient.padStart(64, '0')
                val paddedAmount = parsedAmountBaseUnits.toString(16).padStart(64, '0')
                val erc20Data = "0xa9059cbb$paddedRecipient$paddedAmount"
                Triple(tokenAddress, "0x0", erc20Data)
            } else {
                Triple(toAddress, "0x" + parsedAmountBaseUnits.toString(16), "")
            }

            val finalGasLimitStr = gasLimit ?: try {
                transactionRepository.estimateGas(
                    TransactionRequest(
                        from = wallet.address,
                        to = txToAddress,
                        value = txValueHex,
                        gasPrice = gasPrice,
                        chainType = wallet.chainType,
                        tokenAddress = tokenAddress,
                        data = txDataHex
                    )
                )
            } catch (e: Exception) {
                throw IllegalStateException("Failed to estimate gas: ${e.message}", e)
            }

            val nonceValue = try {
                transactionRepository.getNonce(wallet.address, wallet.chainType)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to retrieve nonce: ${e.message}", e)
            }

            val request = TransactionRequest(
                from = wallet.address,
                to = txToAddress,
                value = txValueHex,
                gasPrice = gasPrice,
                gasLimit = finalGasLimitStr,
                nonce = nonceValue,
                chainType = wallet.chainType,
                tokenAddress = tokenAddress,
                data = txDataHex
            )

            val jsonParamsStr = transactionRepository.buildTransaction(request)
            val jsonElement = Json.parseToJsonElement(jsonParamsStr).jsonObject
            val nonce = jsonElement["nonce"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing nonce")
            val parsedGasPrice = jsonElement["gasPrice"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing gasPrice")
            val parsedGasLimit = jsonElement["gasLimit"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing gasLimit")
            val chainId = jsonElement["chainId"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing chainId")

            val keystoneTx = KeystoneTransaction(
                to = request.to,
                value = request.value,
                data = request.data,
                gasPrice = parsedGasPrice,
                gasLimit = parsedGasLimit,
                nonce = nonce,
                chainId = chainId
            )

            val serialized = Json.encodeToString(KeystoneTransaction.serializer(), keystoneTx)
            Result.Success(serialized)

        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}