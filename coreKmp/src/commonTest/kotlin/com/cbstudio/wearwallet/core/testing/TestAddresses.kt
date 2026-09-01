package com.cbstudio.wearwallet.core.testing

/**
 * Well-known public Ethereum addresses for integration tests.
 *
 * These are addresses with known, publicly observable balances.
 * Using well-known addresses avoids exposing project-specific
 * wallet addresses in public version control.
 *
 * NOTE: Integration tests using these addresses require network
 * access and may be flaky if balances change or RPC rate-limits occur.
 */
object TestAddresses {
    /**
     * Vitalik Buterin's primary public address.
     * Always has ETH balance > 0 on mainnet.
     */
    const val VITALIK = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"

    /**
     * Ethereum Foundation's address — used as a secondary test address.
     * Has ETH balance > 0 on mainnet.
     */
    const val ETHEREUM_FOUNDATION = "0xde0B295669a9FD93d5F28D9Ec85E40f4cb697BAe"
}
