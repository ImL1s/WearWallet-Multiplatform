package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import kotlinx.datetime.Clock

/**
 * Strict enum types for multidimensional security context evaluation (Directive R7 & R3).
 */
enum class Platform {
    WEAR_OS,
    ANDROID_PHONE,
    IOS,
    WATCH_OS,
    DESKTOP,
    UNKNOWN;

    companion object {
        fun fromString(str: String?): Platform = when (str?.lowercase()?.trim()) {
            "wearos", "wear_os", "android_wear" -> WEAR_OS
            "android", "android_phone", "phone" -> ANDROID_PHONE
            "ios" -> IOS
            "watchos", "watch_os" -> WATCH_OS
            "desktop" -> DESKTOP
            else -> UNKNOWN
        }
    }
}

enum class BuildType {
    RELEASE,
    DEBUG,
    TEST,
    UNKNOWN;

    companion object {
        fun fromString(str: String?): BuildType = when (str?.lowercase()?.trim()) {
            "release" -> RELEASE
            "debug" -> DEBUG
            "test" -> TEST
            else -> UNKNOWN
        }
    }
}

enum class Network {
    MAINNET,
    TESTNET,
    DEVNET,
    LOCAL,
    UNKNOWN;

    companion object {
        fun fromString(str: String?): Network = when (str?.lowercase()?.trim()) {
            "mainnet" -> MAINNET
            "testnet" -> TESTNET
            "devnet" -> DEVNET
            "local" -> LOCAL
            else -> UNKNOWN
        }
    }
}

enum class SignerImplementation {
    SOFTWARE_LOCAL,
    KEYSTONE_HARDWARE,
    NATIVE_HARDWARE,
    UNSUPPORTED;

    companion object {
        fun fromString(str: String?): SignerImplementation = when (str?.lowercase()?.trim()) {
            "software", "software_local" -> SOFTWARE_LOCAL
            "keystone", "keystone_hardware" -> KEYSTONE_HARDWARE
            "hardware", "native_hardware" -> NATIVE_HARDWARE
            else -> UNSUPPORTED
        }
    }
}

enum class WalletType {
    SOFTWARE_MNEMONIC,
    SOFTWARE_PRIVATE_KEY,
    KEYSTONE_XPUB,
    HARDWARE_BLE,
    READ_ONLY,
    UNSUPPORTED;

    companion object {
        fun fromString(str: String?): WalletType = when (str?.lowercase()?.trim()) {
            "software_mnemonic", "mnemonic" -> SOFTWARE_MNEMONIC
            "software_private_key", "private_key" -> SOFTWARE_PRIVATE_KEY
            "keystone", "keystone_xpub" -> KEYSTONE_XPUB
            "hardware_ble", "ble" -> HARDWARE_BLE
            "read_only" -> READ_ONLY
            else -> UNSUPPORTED
        }
    }
}

enum class BackendIdentity {
    PRODUCTION_V1,
    STAGING,
    MOCK,
    UNSUPPORTED;

    companion object {
        fun fromString(str: String?): BackendIdentity = when (str?.lowercase()?.trim()) {
            "production", "production_v1", "v1" -> PRODUCTION_V1
            "staging" -> STAGING
            "mock" -> MOCK
            else -> UNSUPPORTED
        }
    }
}

/**
 * Requirement R7, R3 & R17-P1-2: Typed CapabilityRequest data model representing security evaluation context.
 * Refactored to use strict sealed/enum types: Platform, BuildType, Network, EnvelopeType, SignerImplementation, WalletType, BackendIdentity.
 * Primary constructor is internal to prevent caller forgery.
 * Construct only via CapabilityRequest.fromRuntime(operation, runtimeContext, attestation).
 */
data class CapabilityRequest internal constructor(
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
) {

    fun to12Tuple(): CapabilityTuple = CapabilityTuple(
        operation = operation,
        chain = chain,
        network = network,
        platform = platform,
        buildType = buildType,
        envelopeType = envelopeType,
        signerImplementation = signerImplementation,
        walletType = walletType,
        backendIdentity = backendIdentity,
        backendAvailable = backendAvailable,
        backendVersion = backendVersion,
        smokeVectorVerified = smokeVectorVerified
    )

    companion object {
        /**
         * Canonical factory deriving all 12 tuple fields from verified RuntimeContext & BackendAttestation.
         * Fails closed: if attestation is invalid/expired/corrupted, backendAvailable and smokeVectorVerified
         * are set to false, and backendIdentity defaults to UNSUPPORTED, triggering immediate Gate Denied.
         */
        fun fromRuntime(
            operation: Operation,
            runtimeContext: RuntimeCapabilityContext,
            attestation: BackendAttestation,
            currentTimeMs: Long = Clock.System.now().toEpochMilliseconds(),
            expectedSmokeVectorHash: String = BackendAttestation.CANONICAL_SMOKE_VECTOR_HASH,
            expectedBackendVersion: String = BackendAttestation.CANONICAL_BACKEND_VERSION
        ): CapabilityRequest {
            val isAttestationValid = attestation.isValid(
                currentTimeMs = currentTimeMs,
                expectedSmokeVectorHash = expectedSmokeVectorHash,
                expectedVersion = expectedBackendVersion
            )

            val backendAvailable = isAttestationValid && attestation.availability
            val smokeVectorVerified = isAttestationValid && (attestation.smokeVectorHash == expectedSmokeVectorHash)

            return CapabilityRequest(
                operation = operation,
                chain = runtimeContext.chainContext.multiChainType,
                network = runtimeContext.chainContext.capabilityNetwork,
                platform = runtimeContext.platform,
                buildType = runtimeContext.buildType,
                envelopeType = runtimeContext.envelopeType,
                signerImplementation = runtimeContext.signerImplementation,
                walletType = runtimeContext.walletType,
                backendIdentity = if (isAttestationValid) attestation.backendIdentity else BackendIdentity.UNSUPPORTED,
                backendAvailable = backendAvailable,
                backendVersion = attestation.backendVersion,
                smokeVectorVerified = smokeVectorVerified
            )
        }

        /**
         * Test factory method for perturbation and unit testing across module boundaries.
         */
        fun createForTesting(
            operation: Operation = Operation.IMPORT_XPUB,
            chain: MultiChainType = MultiChainType.ETHEREUM,
            network: Network = Network.MAINNET,
            platform: Platform = Platform.WEAR_OS,
            buildType: BuildType = BuildType.RELEASE,
            envelopeType: EvmEnvelope = EvmEnvelope.LEGACY,
            signerImplementation: SignerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType: WalletType = WalletType.KEYSTONE_XPUB,
            backendIdentity: BackendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable: Boolean = true,
            backendVersion: String = "1.0.0",
            smokeVectorVerified: Boolean = true
        ): CapabilityRequest = CapabilityRequest(
            operation = operation,
            chain = chain,
            network = network,
            platform = platform,
            buildType = buildType,
            envelopeType = envelopeType,
            signerImplementation = signerImplementation,
            walletType = walletType,
            backendIdentity = backendIdentity,
            backendAvailable = backendAvailable,
            backendVersion = backendVersion,
            smokeVectorVerified = smokeVectorVerified
        )
    }
}
