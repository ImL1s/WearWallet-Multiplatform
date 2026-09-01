package com.cbstudio.wearwallet.core.domain.model.quantities

/**
 * Typed EVM envelope type.
 */
enum class EvmEnvelope(val rawType: Int) {
    LEGACY(0x00),
    EIP2930(0x01),
    EIP1559(0x02);

    companion object {
        fun fromInt(type: Int): EvmEnvelope = when (type) {
            0 -> LEGACY
            1 -> EIP2930
            2 -> EIP1559
            else -> throw IllegalArgumentException("Unsupported EVM envelope type: $type")
        }
    }
}
