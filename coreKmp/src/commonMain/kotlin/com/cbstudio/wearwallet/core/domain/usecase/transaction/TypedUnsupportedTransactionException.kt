package com.cbstudio.wearwallet.core.domain.usecase.transaction

/**
 * Exception thrown when a transaction type or chain operation is not supported.
 */
class TypedUnsupportedTransactionException(message: String) : UnsupportedOperationException(message)
