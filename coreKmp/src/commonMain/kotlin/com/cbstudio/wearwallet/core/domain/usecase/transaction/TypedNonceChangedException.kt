package com.cbstudio.wearwallet.core.domain.usecase.transaction

/**
 * Exception thrown when the pending nonce changes before signing.
 */
class TypedNonceChangedException(
    val expectedNonce: Long,
    val actualNonce: Long
) : IllegalStateException("Pending nonce changed from $expectedNonce to $actualNonce before signing")
