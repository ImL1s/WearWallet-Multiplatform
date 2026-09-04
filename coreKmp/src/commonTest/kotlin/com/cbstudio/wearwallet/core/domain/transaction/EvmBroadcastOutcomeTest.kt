package com.cbstudio.wearwallet.core.domain.transaction

import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvmBroadcastOutcomeTest {

    @Test
    fun successfulBroadcastHashIsPendingNotConfirmed() {
        val status = EvmBroadcastOutcome.statusForSubmittedHash()
        assertEquals(TransactionStatus.PENDING, status)
        assertFalse(status == TransactionStatus.CONFIRMED)
        assertTrue(status.isPending())
        assertFalse(status.isSuccessful())
        assertFalse(status.isFinal())
    }
}
