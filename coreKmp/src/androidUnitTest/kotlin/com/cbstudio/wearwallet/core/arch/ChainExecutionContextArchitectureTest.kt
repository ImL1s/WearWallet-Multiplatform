package com.cbstudio.wearwallet.core.arch

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.reflect.KVisibility
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

/**
 * Architectural test ensuring ChainExecutionContext is a canonical identity with an internal constructor (P2).
 */
class ChainExecutionContextArchitectureTest {

    @Test
    fun test_ChainExecutionContext_constructor_is_internal_and_has_no_defaults() {
        val primaryConstructor = ChainExecutionContext::class.primaryConstructor
        assertTrue("ChainExecutionContext must have a primary constructor", primaryConstructor != null)

        assertEquals(
            "ChainExecutionContext constructor must be INTERNAL to prevent arbitrary external construction",
            KVisibility.INTERNAL,
            primaryConstructor!!.visibility
        )

        val optionalParams = primaryConstructor.valueParameters.filter { it.isOptional }
        assertTrue(
            "ChainExecutionContext constructor must have no default parameters: $optionalParams",
            optionalParams.isEmpty()
        )
    }

    @Test
    fun test_ChainExecutionContextRegistry_fails_closed_on_unsupported_or_unknown_lookup() {
        assertThrows(TypedUnsupportedTransactionException::class.java) {
            ChainExecutionContextRegistry.resolve(MultiChainType.SOLANA, NetworkType.MAINNET)
        }
    }
}
