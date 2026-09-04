package com.cbstudio.wearwallet.core.domain.usecase.wallet

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.platform.SecureStorage
import com.cbstudio.wearwallet.core.utils.PerformanceMonitor.withPerformanceMonitoring
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.utils.Logger

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
import com.cbstudio.wearwallet.core.security.EphemeralMnemonicHolder
import com.cbstudio.wearwallet.core.security.PlatformProvider
import com.cbstudio.wearwallet.core.security.BuildTypeProvider
import com.cbstudio.wearwallet.core.security.BackendAttestationProvider
import com.cbstudio.wearwallet.core.security.TestPlatformProvider
import com.cbstudio.wearwallet.core.security.TestBuildTypeProvider
import com.cbstudio.wearwallet.core.security.DefaultBackendAttestationProvider
import com.cbstudio.wearwallet.core.security.RuntimeCapabilityContext
import com.cbstudio.wearwallet.core.security.ScopedPassword
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

/**
 * 創建錢包 Use Case
 */
class CreateWalletUseCase(
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

    suspend operator fun invoke(
        name: String,
        password: CharArray,
        chainType: ChainType = ChainType.ETHEREUM,
        mnemonic: CharArray? = null,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Flow<Result<WalletAccount>> = flow {
        val pwCopy = password.copyOf()
        var mnemChars = mnemonic?.copyOf()
        try {
            // 生產環境 Capability Gate 檢查 (在生成 entropy 前使用 Operation-aware API)
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
            val createReq = CapabilityRequest.fromRuntime(
                operation = Operation.CREATE_WALLET,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(createReq)) {
                emit(Result.Failure(TypedUnsupportedTransactionException("Production capability gate fail-closed: wallet creation for $chainType is disabled")))
                return@flow
            }

            // 驗證錢包名稱
            if (name.isBlank()) {
                emit(Result.Failure(Exception("Wallet name cannot be empty")))
                return@flow
            }
            
            // 生成或使用提供的助記詞
            Logger.d("CreateWalletUseCase", "準備生成助記詞...")
            val finalMnemonic = if (mnemChars == null) {
                cryptoProvider.generateMnemonic().use { it.copyOf() }
            } else {
                mnemChars.copyOf()
            }
            mnemChars = finalMnemonic
            
            // 驗證助記詞
            if (!cryptoProvider.validateMnemonic(finalMnemonic)) {
                emit(Result.Failure(Exception("Invalid mnemonic phrase")))
                return@flow
            }
            
            // 使用新的 WalletRepository 介面創建錢包
            Logger.d("CreateWalletUseCase", "正在調用 walletRepository.createWallet...")
            val result = walletRepository.createWallet(
                name = name,
                mnemonic = finalMnemonic,
                password = pwCopy,
                chainType = chainType,
                authContext = authContext
            )
            Logger.d("CreateWalletUseCase", "walletRepository.createWallet 返回: ${result::class.simpleName}")
            
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
        } catch (e: Exception) {
            emit(Result.Failure(e))
        } finally {
            pwCopy.fill('\u0000')
            mnemChars?.fill('\u0000')
        }
    }.withPerformanceMonitoring(
        useCaseName = "CreateWalletUseCase",
        metadata = mapOf(
            "chainType" to chainType.name,
            "hasMnemonic" to (mnemonic != null)
        )
    )

    /**
     * 創建錢包並在短暫記憶體中返回生成的助記詞供首次備份顯示 (M3 Ephemeral Creation Result)
     * 避免後續呼叫危險的 exportMnemonic API。
     */
    suspend fun createWithMnemonic(
        name: String,
        password: CharArray,
        chainType: ChainType = ChainType.ETHEREUM,
        mnemonic: CharArray? = null,
        authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext
    ): Flow<Result<CreatedWallet>> = flow {
        val pwCopy = password.copyOf()
        var mnemChars = mnemonic?.copyOf()
        try {
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
            val createReq = CapabilityRequest.fromRuntime(
                operation = Operation.CREATE_WALLET,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(createReq)) {
                emit(Result.Failure(TypedUnsupportedTransactionException("Production capability gate fail-closed: wallet creation for $chainType is disabled")))
                return@flow
            }

            if (name.isBlank()) {
                emit(Result.Failure(Exception("Wallet name cannot be empty")))
                return@flow
            }

            val finalMnemonic = if (mnemChars == null) {
                cryptoProvider.generateMnemonic().use { it.copyOf() }
            } else {
                mnemChars.copyOf()
            }
            mnemChars = finalMnemonic

            if (!cryptoProvider.validateMnemonic(finalMnemonic)) {
                emit(Result.Failure(Exception("Invalid mnemonic phrase")))
                return@flow
            }

            val holder = EphemeralMnemonicHolder.fromMnemonicChars(finalMnemonic)

            val result = walletRepository.createWallet(
                name = name,
                mnemonic = finalMnemonic,
                password = pwCopy,
                chainType = chainType,
                authContext = authContext
            )

            when (result) {
                is Result.Success -> {
                    val walletsResult = walletRepository.getAllWallets()
                    if (walletsResult is Result.Success && walletsResult.data.size == 1) {
                        walletRepository.setActiveWallet(result.data.id)
                    }
                    emit(Result.Success(CreatedWallet(result.data, holder)))
                }
                is Result.Failure -> {
                    holder.clear()
                    emit(Result.Failure(result.exception))
                }
                is Result.Loading -> emit(Result.Loading())
            }
        } catch (e: Exception) {
            emit(Result.Failure(e))
        } finally {
            pwCopy.fill('\u0000')
            mnemChars?.fill('\u0000')
        }
    }.withPerformanceMonitoring(
        useCaseName = "CreateWalletUseCase.createWithMnemonic",
        metadata = mapOf(
            "chainType" to chainType.name,
            "hasMnemonic" to (mnemonic != null)
        )
    )

    data class CreatedWallet(
        val wallet: WalletAccount,
        val mnemonicHolder: EphemeralMnemonicHolder
    )
    
    private fun generateWalletId(): String {
        return "wallet_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}"
    }
}