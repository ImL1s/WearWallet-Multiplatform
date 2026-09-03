package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope

/**
 * 生產環境功能開關與 Signer / Operation 驗證門控
 */
enum class Operation {
    CREATE_WALLET,
    IMPORT_MNEMONIC,
    IMPORT_PRIVATE_KEY,
    IMPORT_XPUB,
    CREATE_UNSIGNED_TX,
    SOFTWARE_SIGN,
    HARDWARE_SIGN_REQUEST,
    BROADCAST
}

/**
 * Capability decision sealed class representing permission resolution outcome.
 */
sealed class CapabilityDecision {
    object Allowed : CapabilityDecision()
    data class Denied(val reason: String) : CapabilityDecision()
}

/**
 * CapabilityTuple represents a concrete 12-dimensional execution context (Requirement R4).
 */
data class CapabilityTuple(
    val operation: Operation,
    val chain: MultiChainType,
    val network: Network,
    val platform: Platform,
    val buildType: BuildType,
    val envelopeType: EvmEnvelope,
    val signerImplementation: SignerImplementation,
    val walletType: WalletType,
    val backendIdentity: BackendIdentity,
    val backendAvailable: Boolean,
    val backendVersion: String,
    val smokeVectorVerified: Boolean
)

/**
 * 生產環境功能開關與 Signer / Operation 驗證門控介面
 */
interface CapabilityGate {
    fun checkCapability(request: CapabilityRequest): CapabilityDecision

    fun verifyCapability(request: CapabilityRequest): Boolean =
        checkCapability(request) is CapabilityDecision.Allowed

    fun isChainSupported(multiChainType: MultiChainType): Boolean

    fun isChainSupported(chainType: ChainType): Boolean {
        val multiChainType = when (chainType) {
            ChainType.SEPOLIA, ChainType.GOERLI -> MultiChainType.ETHEREUM
            ChainType.MUMBAI -> MultiChainType.POLYGON
            else -> try { MultiChainType.valueOf(chainType.name) } catch (e: Exception) { null }
        } ?: return false
        return isChainSupported(multiChainType)
    }

    fun isEvmMainnetSendAllowed(): Boolean
}

private const val ALLOWED_BACKEND_AVAILABLE = true
private const val ALLOWED_SMOKE_VECTOR_VERIFIED = true
private const val ALLOWED_BACKEND_VERSION = "1.0.0"

/**
 * Release 生產環境 Gate 實現 (Directive R7 & R3)
 * Exact 12-tuple allowlist with default-deny policy.
 * Hardware signers are strictly blocked from SOFTWARE_SIGN, IMPORT_PRIVATE_KEY, IMPORT_MNEMONIC, CREATE_WALLET.
 * Default-denies any request containing UNKNOWN or UNSUPPORTED enum fields.
 */
class ReleaseProductionCapabilityGate(
    private val allowEvmMainnetSend: Boolean = false,
    private val allowBroadcast: Boolean = false
) : CapabilityGate {

    private val releaseAllowlistedChains = setOf(
        MultiChainType.ETHEREUM,
        MultiChainType.POLYGON,
        MultiChainType.BSC,
        MultiChainType.ARBITRUM,
        MultiChainType.OPTIMISM,
        MultiChainType.BASE
    )

    private val allowlisted12Tuples: Set<CapabilityTuple> by lazy {
        val tuples = mutableSetOf<CapabilityTuple>()

        val platforms = listOf(
            Platform.WEAR_OS,
            Platform.ANDROID_PHONE,
            Platform.IOS,
            Platform.WATCH_OS,
            Platform.DESKTOP
        )
        val envelopes = listOf(EvmEnvelope.LEGACY, EvmEnvelope.EIP1559)

        // Exact permission definitions: (Signer, Operation, WalletType, allowed on Mainnet even if !allowEvmMainnetSend)
        data class PermissionSpec(
            val signer: SignerImplementation,
            val operation: Operation,
            val walletType: WalletType,
            val allowedOnRestrictedMainnet: Boolean
        )

        val specs = mutableListOf<PermissionSpec>()

        // 1. Software Local operations
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.CREATE_WALLET, WalletType.SOFTWARE_MNEMONIC, allowedOnRestrictedMainnet = false))
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.IMPORT_MNEMONIC, WalletType.SOFTWARE_MNEMONIC, allowedOnRestrictedMainnet = false))
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.IMPORT_PRIVATE_KEY, WalletType.SOFTWARE_PRIVATE_KEY, allowedOnRestrictedMainnet = false))
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.CREATE_UNSIGNED_TX, WalletType.SOFTWARE_MNEMONIC, allowedOnRestrictedMainnet = false))
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.CREATE_UNSIGNED_TX, WalletType.SOFTWARE_PRIVATE_KEY, allowedOnRestrictedMainnet = false))
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.SOFTWARE_SIGN, WalletType.SOFTWARE_MNEMONIC, allowedOnRestrictedMainnet = false))
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.SOFTWARE_SIGN, WalletType.SOFTWARE_PRIVATE_KEY, allowedOnRestrictedMainnet = false))
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.IMPORT_XPUB, WalletType.READ_ONLY, allowedOnRestrictedMainnet = true))
        specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.IMPORT_XPUB, WalletType.KEYSTONE_XPUB, allowedOnRestrictedMainnet = true))

        if (allowBroadcast) {
            specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.BROADCAST, WalletType.SOFTWARE_MNEMONIC, allowedOnRestrictedMainnet = false))
            specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.BROADCAST, WalletType.SOFTWARE_PRIVATE_KEY, allowedOnRestrictedMainnet = false))
            specs.add(PermissionSpec(SignerImplementation.SOFTWARE_LOCAL, Operation.BROADCAST, WalletType.READ_ONLY, allowedOnRestrictedMainnet = false))
        }

        // 2. Hardware Signers (Keystone & Native Hardware) - Strictly NO software sign or key import/creation
        for (hwSigner in listOf(SignerImplementation.KEYSTONE_HARDWARE, SignerImplementation.NATIVE_HARDWARE)) {
            for (wType in listOf(WalletType.KEYSTONE_XPUB, WalletType.HARDWARE_BLE)) {
                specs.add(PermissionSpec(hwSigner, Operation.IMPORT_XPUB, wType, allowedOnRestrictedMainnet = true))
                specs.add(PermissionSpec(hwSigner, Operation.CREATE_UNSIGNED_TX, wType, allowedOnRestrictedMainnet = true))
                specs.add(PermissionSpec(hwSigner, Operation.HARDWARE_SIGN_REQUEST, wType, allowedOnRestrictedMainnet = true))
                if (allowBroadcast) {
                    specs.add(PermissionSpec(hwSigner, Operation.BROADCAST, wType, allowedOnRestrictedMainnet = true))
                }
            }
        }

        for (chain in releaseAllowlistedChains) {
            for (platform in platforms) {
                for (envelope in envelopes) {
                    for (spec in specs) {
                        // TESTNET is always eligible
                        tuples.add(
                            CapabilityTuple(
                                operation = spec.operation,
                                chain = chain,
                                network = Network.TESTNET,
                                platform = platform,
                                buildType = BuildType.RELEASE,
                                envelopeType = envelope,
                                signerImplementation = spec.signer,
                                walletType = spec.walletType,
                                backendIdentity = BackendIdentity.PRODUCTION_V1,
                                backendAvailable = ALLOWED_BACKEND_AVAILABLE,
                                backendVersion = ALLOWED_BACKEND_VERSION,
                                smokeVectorVerified = ALLOWED_SMOKE_VECTOR_VERIFIED
                            )
                        )

                        // MAINNET is eligible if allowed by spec or allowEvmMainnetSend
                        if (allowEvmMainnetSend || spec.allowedOnRestrictedMainnet) {
                            tuples.add(
                                CapabilityTuple(
                                    operation = spec.operation,
                                    chain = chain,
                                    network = Network.MAINNET,
                                    platform = platform,
                                    buildType = BuildType.RELEASE,
                                    envelopeType = envelope,
                                    signerImplementation = spec.signer,
                                    walletType = spec.walletType,
                                    backendIdentity = BackendIdentity.PRODUCTION_V1,
                                    backendAvailable = ALLOWED_BACKEND_AVAILABLE,
                                    backendVersion = ALLOWED_BACKEND_VERSION,
                                    smokeVectorVerified = ALLOWED_SMOKE_VECTOR_VERIFIED
                                )
                            )
                        }
                    }
                }
            }
        }

        tuples
    }

    override fun isChainSupported(multiChainType: MultiChainType): Boolean {
        return multiChainType in releaseAllowlistedChains
    }

    override fun checkCapability(request: CapabilityRequest): CapabilityDecision {
        // 0. Default-deny any request containing UNKNOWN or UNSUPPORTED enum fields (R3)
        if (request.signerImplementation == SignerImplementation.UNSUPPORTED) {
            return CapabilityDecision.Denied("Unsupported or invalid signer implementation")
        }
        if (request.platform == Platform.UNKNOWN ||
            request.buildType == BuildType.UNKNOWN ||
            request.network == Network.UNKNOWN ||
            request.backendIdentity == BackendIdentity.UNSUPPORTED ||
            request.walletType == WalletType.UNSUPPORTED
        ) {
            return CapabilityDecision.Denied("Capability request contains UNKNOWN or UNSUPPORTED enum fields")
        }

        // 1. Prohibit software identity claiming hardware sign operations
        if (request.signerImplementation == SignerImplementation.SOFTWARE_LOCAL &&
            request.operation == Operation.HARDWARE_SIGN_REQUEST
        ) {
            return CapabilityDecision.Denied("SOFTWARE_LOCAL identity cannot request HARDWARE_SIGN_REQUEST")
        }

        // 2. Hardware signer isolation: strictly prohibit hardware signer claiming software signing, mnemonic, private key import, or wallet creation
        if ((request.signerImplementation == SignerImplementation.KEYSTONE_HARDWARE ||
             request.signerImplementation == SignerImplementation.NATIVE_HARDWARE) &&
            request.operation in setOf(
                Operation.SOFTWARE_SIGN,
                Operation.IMPORT_PRIVATE_KEY,
                Operation.IMPORT_MNEMONIC,
                Operation.CREATE_WALLET
            )
        ) {
            return CapabilityDecision.Denied("Hardware signer cannot perform software cryptographic operations: ${request.operation}")
        }

        // 3. Chain allowlist check
        if (request.chain !in releaseAllowlistedChains) {
            return CapabilityDecision.Denied("Chain ${request.chain} is not in release allowlist")
        }

        // 4. Broadcast permission check (NO auto-broadcast on sign)
        if (request.operation == Operation.BROADCAST && !allowBroadcast) {
            return CapabilityDecision.Denied("BROADCAST operation is explicitly disallowed under ReleaseProductionCapabilityGate when allowBroadcast=false")
        }

        // 5. Mainnet software send / sign restriction
        if (request.network == Network.MAINNET &&
            !allowEvmMainnetSend &&
            request.signerImplementation == SignerImplementation.SOFTWARE_LOCAL
        ) {
            if (request.operation in setOf(
                    Operation.CREATE_WALLET,
                    Operation.IMPORT_MNEMONIC,
                    Operation.IMPORT_PRIVATE_KEY,
                    Operation.CREATE_UNSIGNED_TX,
                    Operation.SOFTWARE_SIGN,
                    Operation.BROADCAST
                )
            ) {
                return CapabilityDecision.Denied("Operation ${request.operation} on EVM mainnet is prohibited under ReleaseProductionCapabilityGate when allowEvmMainnetSend=false")
            }
        }

        // 6. Strict 12-tuple allowlist evaluation (R4)
        val tuple = request.to12Tuple()
        if (tuple !in allowlisted12Tuples) {
            return CapabilityDecision.Denied("Capability tuple $tuple is not in strict 12-tuple release allowlist")
        }

        return CapabilityDecision.Allowed
    }

    override fun isEvmMainnetSendAllowed(): Boolean = allowEvmMainnetSend
}

/**
 * 開發測試用 Gate 實現
 */
class AllowDevCapabilityGate(
    private val allowBroadcast: Boolean = true
) : CapabilityGate {

    override fun checkCapability(request: CapabilityRequest): CapabilityDecision {
        if (request.platform == Platform.UNKNOWN ||
            request.buildType == BuildType.UNKNOWN ||
            request.network == Network.UNKNOWN ||
            request.backendIdentity == BackendIdentity.UNSUPPORTED ||
            request.signerImplementation == SignerImplementation.UNSUPPORTED ||
            request.walletType == WalletType.UNSUPPORTED
        ) {
            return CapabilityDecision.Denied("Capability request contains UNKNOWN or UNSUPPORTED enum fields")
        }

        if (request.operation == Operation.BROADCAST && !allowBroadcast) {
            return CapabilityDecision.Denied("BROADCAST operation is disallowed in dev mode")
        }
        return CapabilityDecision.Allowed
    }

    override fun isChainSupported(multiChainType: MultiChainType): Boolean {
        return true
    }

    override fun isEvmMainnetSendAllowed(): Boolean = true
}
