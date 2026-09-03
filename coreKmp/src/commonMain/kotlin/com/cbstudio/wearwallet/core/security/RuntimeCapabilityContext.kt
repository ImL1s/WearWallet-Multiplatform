package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope

/**
 * RuntimeCapabilityContext aggregates validated runtime execution dimensions:
 * platform, build type, wallet record type, envelope type, signer implementation,
 * and canonical ChainExecutionContext.
 */
data class RuntimeCapabilityContext(
    val platform: Platform,
    val buildType: BuildType,
    val chainContext: ChainExecutionContext,
    val walletType: WalletType,
    val envelopeType: EvmEnvelope,
    val signerImplementation: SignerImplementation
) {

    companion object {
        fun create(
            platformProvider: PlatformProvider,
            buildTypeProvider: BuildTypeProvider,
            chainExecutionContext: ChainExecutionContext,
            walletType: WalletType,
            envelopeType: EvmEnvelope = EvmEnvelope.LEGACY,
            signerImplementation: SignerImplementation = SignerImplementation.SOFTWARE_LOCAL
        ): RuntimeCapabilityContext {
            return RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                walletType = walletType,
                envelopeType = envelopeType,
                signerImplementation = signerImplementation,
                chainContext = chainExecutionContext
            )
        }
    }
}
