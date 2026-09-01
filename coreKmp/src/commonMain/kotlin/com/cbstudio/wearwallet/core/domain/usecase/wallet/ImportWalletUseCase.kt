package com.cbstudio.wearwallet.core.domain.usecase.wallet

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.utils.PerformanceMonitor.withPerformanceMonitoring
import com.cbstudio.wearwallet.core.utils.RetryPolicy
import com.cbstudio.wearwallet.core.utils.withRetryPolicy
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

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
import com.cbstudio.wearwallet.core.security.ScopedPrivateKey
import com.cbstudio.wearwallet.core.security.PlatformProvider
import com.cbstudio.wearwallet.core.security.BuildTypeProvider
import com.cbstudio.wearwallet.core.security.BackendAttestationProvider
import com.cbstudio.wearwallet.core.security.TestPlatformProvider
import com.cbstudio.wearwallet.core.security.TestBuildTypeProvider
import com.cbstudio.wearwallet.core.security.DefaultBackendAttestationProvider
import com.cbstudio.wearwallet.core.security.RuntimeCapabilityContext
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

/**
 * 導入錢包 Use Case
 */
class ImportWalletUseCase(
    private val walletRepository: WalletRepository,
    private val cryptoProvider: CryptoProvider,
    private val secureStorage: SecureStorage,
    private val capabilityGate: CapabilityGate,
    private val platformProvider: PlatformProvider = TestPlatformProvider(),
    private val buildTypeProvider: BuildTypeProvider = TestBuildTypeProvider(),
    private val attestationProvider: BackendAttestationProvider = DefaultBackendAttestationProvider()
) {
    suspend fun prepareProvisioning(): Result<com.cbstudio.wearwallet.core.security.ProvisioningRequest> {
        return walletRepository.prepareProvisioning()
    }

    /**
     * 通過助記詞導入錢包
     */
    suspend fun importFromMnemonic(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType = ChainType.ETHEREUM,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Flow<Result<WalletAccount>> = flow {
        val pwCopy = password.copyOf()
        val mnemCopy = mnemonic.copyOf()
        try {
            // 生產環境 Capability Gate 檢查 (Operation-aware)
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
            val req = CapabilityRequest.fromRuntime(
                operation = Operation.IMPORT_MNEMONIC,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(req)) {
                emit(Result.Failure(TypedUnsupportedTransactionException("Production capability gate fail-closed: mnemonic wallet import for $chainType is disabled")))
                return@flow
            }

            // 驗證輸入
            if (name.isBlank()) {
                emit(Result.Failure(Exception("Wallet name cannot be empty")))
                return@flow
            }

            // 驗證助記詞
            if (!cryptoProvider.validateMnemonic(mnemCopy)) {
                emit(Result.Failure(Exception("Invalid mnemonic phrase")))
                return@flow
            }

            // 使用背景執行緒進行 CPU 密集型加密運算
            Logger.d("ImportWalletUseCase", "Executing crypto generation on Default dispatcher...")
            val (keyPair, address) = withContext(Dispatchers.Default) {
                val derivationPath = chainType.getDefaultDerivationPath()
                val kp = cryptoProvider.generateKeyPairFromMnemonic(
                    mnemonic = mnemCopy,
                    derivationPath = derivationPath,
                    chainType = chainType
                )
                val addr = cryptoProvider.deriveAddress(kp.publicKey)
                Pair(kp, addr)
            }
            Logger.d("ImportWalletUseCase", "Crypto generation complete. Address: $address")

            // 檢查地址是否已存在（防止重複導入）
            val existingWallets = walletRepository.getAllWallets()
            if (existingWallets is Result.Success) {
                val isDuplicate = existingWallets.data.any { wallet ->
                    wallet.address.equals(address, ignoreCase = true)
                }

                if (isDuplicate) {
                    Logger.w("ImportWallet", "錢包已經存在: $address")
                    emit(Result.Failure(Exception("此錢包已經存在")))
                    return@flow
                }
            }

            Logger.d("ImportWallet", "地址檢查通過: $address. calling repository...")

            // 使用優化版導入方法，傳入預先計算的 KeyPair
            val result = walletRepository.importFromMnemonicWithKeyPair(
                name = name,
                mnemonic = mnemCopy,
                password = pwCopy,
                chainType = chainType,
                keyPair = keyPair,
                address = address,
                authContext = authContext
            )
            when (result) {
                is Result.Success -> {
                    // 設為活動錢包（如果是第一個錢包）
                    val walletsResult = walletRepository.getAllWallets()
                    if (walletsResult is Result.Success && walletsResult.data.size == 1) {
                        walletRepository.setActiveWallet(result.data.id)
                    }
                    emit(Result.Success(result.data))
                }
                is Result.Failure -> emit(Result.Failure(result.exception))
                is Result.Loading -> emit(Result.Loading())
            }
        } finally {
            pwCopy.fill('\u0000')
            mnemCopy.fill('\u0000')
        }
    }.catch { e ->
        emit(Result.Failure(if (e is Exception) e else Exception(e)))
    }
    .withRetryPolicy(RetryPolicy.Exponential(maxAttempts = 3))
    .withPerformanceMonitoring(
        useCaseName = "ImportWalletUseCase",
        metadata = mapOf("chainType" to chainType.name)
    )
    
    /**
     * 通過私鑰導入錢包
     */
    suspend fun importFromPrivateKey(
        name: String,
        privateKey: ScopedPrivateKey,
        password: CharArray,
        chainType: ChainType = ChainType.ETHEREUM,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Flow<Result<WalletAccount>> = flow {
        val pwCopy = password.copyOf()
        try {
            // 生產環境 Capability Gate 檢查 (Operation-aware, 在生成 keyPair / deriveAddress 前)
            val ctx = ChainExecutionContextRegistry.resolve(chainType)
            val attestation = attestationProvider.getAttestation(ctx)
            val runtimeContext = RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                chainContext = ctx,
                walletType = WalletType.SOFTWARE_PRIVATE_KEY,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
            val req = CapabilityRequest.fromRuntime(
                operation = Operation.IMPORT_PRIVATE_KEY,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(req)) {
                emit(Result.Failure(TypedUnsupportedTransactionException("Production capability gate fail-closed: private key wallet import for $chainType is disabled")))
                return@flow
            }

            // 驗證輸入
            if (name.isBlank()) {
                emit(Result.Failure(Exception("Wallet name cannot be empty")))
                return@flow
            }

            val keyPair = privateKey.use { privBytes ->
                cryptoProvider.generateKeyPairFromPrivateKey(privBytes)
            }
            val address = cryptoProvider.deriveAddress(keyPair.publicKey)

            val existingWallets = walletRepository.getAllWallets()
            if (existingWallets is Result.Success) {
                val isDuplicate = existingWallets.data.any { wallet ->
                    wallet.address.equals(address, ignoreCase = true)
                }

                if (isDuplicate) {
                    Logger.w("ImportWallet", "錢包已經存在: $address")
                    emit(Result.Failure(Exception("此錢包已經存在")))
                    return@flow
                }
            }

            Logger.d("ImportWallet", "地址檢查通過: $address")

            // 使用新的 WalletRepository 介面導入錢包
            val result = walletRepository.importFromPrivateKey(
                name = name,
                privateKey = privateKey,
                password = pwCopy,
                chainType = chainType,
                authContext = authContext
            )
            
            when (result) {
                is Result.Success -> {
                    // 設為活動錢包（如果是第一個錢包）
                    val walletsResult = walletRepository.getAllWallets()
                    if (walletsResult is Result.Success && walletsResult.data.size == 1) {
                        walletRepository.setActiveWallet(result.data.id)
                    }
                    emit(Result.Success(result.data))
                }
                is Result.Failure -> emit(Result.Failure(result.exception))
                is Result.Loading -> emit(Result.Loading())
            }
        } finally {
            pwCopy.fill('\u0000')
        }
    }.catch { e ->
        emit(Result.Failure(if (e is Exception) e else Exception(e)))
    }
    .withRetryPolicy(
        policy = RetryPolicy.Fixed(
            maxAttempts = 2,
            delay = 2.seconds
        ),
        onRetry = { attempt, error ->
            Logger.d("ImportWallet", "Retry attempt $attempt for private key import: ${error.message}")
        }
    ).withPerformanceMonitoring(
        useCaseName = "ImportWalletUseCase.importFromPrivateKey",
        metadata = mapOf("chainType" to chainType.name)
    )

    suspend fun importFromPrivateKey(
        name: String,
        privateKey: CharArray,
        password: CharArray,
        chainType: ChainType = ChainType.ETHEREUM,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Flow<Result<WalletAccount>> = flow {
        val scopedKey = try {
            ScopedPrivateKey.fromHex(privateKey)
        } catch (e: Exception) {
            emit(Result.Failure(IllegalArgumentException("Invalid private key format")))
            return@flow
        }
        importFromPrivateKey(
            name = name,
            privateKey = scopedKey,
            password = password,
            chainType = chainType,
            authContext = authContext
        ).collect { emit(it) }
    }
    
    /**
     * 通過 Keystone 硬體錢包導入
     */
    suspend fun importFromKeystone(
        name: String,
        xpub: String,
        derivationPath: String,
        masterFingerprint: String,
        chainType: ChainType = ChainType.ETHEREUM,
        policy: com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy = com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy.STRICT_DEFAULT
    ): Flow<Result<WalletAccount>> = flow {
        val ctx = ChainExecutionContextRegistry.resolve(chainType)
        val attestation = attestationProvider.getAttestation(ctx)
        val runtimeContext = RuntimeCapabilityContext(
            platform = platformProvider.currentPlatform,
            buildType = buildTypeProvider.currentBuildType,
            chainContext = ctx,
            walletType = WalletType.KEYSTONE_XPUB,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.KEYSTONE_HARDWARE
        )
        val req = CapabilityRequest.fromRuntime(
            operation = Operation.IMPORT_XPUB,
            runtimeContext = runtimeContext,
            attestation = attestation
        )
        if (!capabilityGate.verifyCapability(req)) {
            emit(Result.Failure(TypedUnsupportedTransactionException("Production capability gate fail-closed: Keystone wallet import for $chainType is disabled")))
            return@flow
        }

        policy.validate(masterFingerprint = masterFingerprint, xpub = xpub, derivationPath = derivationPath, isTestnet = chainType.isTestnet())

        // 驗證輸入
        if (name.isBlank()) {
            emit(Result.Failure(Exception("Wallet name cannot be empty")))
            return@flow
        }
        
        // 使用新的 WalletRepository 介面導入 Keystone 錢包
        val result = walletRepository.importKeystoneWallet(
            name = name,
            xpub = xpub,
            derivationPath = derivationPath,
            masterFingerprint = masterFingerprint,
            chainType = chainType,
            policy = policy
        )
        
        when (result) {
            is Result.Success -> {
                // 設為活動錢包（如果是第一個錢包）
                val walletsResult = walletRepository.getAllWallets()
                if (walletsResult is Result.Success && walletsResult.data.size == 1) {
                    walletRepository.setActiveWallet(result.data.id)
                }
                emit(Result.Success(result.data))
            }
            is Result.Failure -> emit(Result.Failure(result.exception))
            is Result.Loading -> emit(Result.Loading())
        }
    }.catch { e ->
        emit(Result.Failure(if (e is Exception) e else Exception(e)))
    }
    
    private fun isValidPrivateKey(privateKey: String): Boolean {
        // 私鑰應該是 64 個十六進制字符
        return privateKey.matches(Regex("^[0-9a-fA-F]{64}$"))
    }
}