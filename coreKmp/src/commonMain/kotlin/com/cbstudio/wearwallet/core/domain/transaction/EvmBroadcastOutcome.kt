package com.cbstudio.wearwallet.core.domain.transaction

import com.cbstudio.wearwallet.core.domain.model.TransactionStatus

/**
 * A submitted tx hash is not chain confirmation. Replaced/Dropped remain
 * unsupported in the feature matrix.
 */
object EvmBroadcastOutcome {
    fun statusForSubmittedHash(): TransactionStatus = TransactionStatus.PENDING
}
