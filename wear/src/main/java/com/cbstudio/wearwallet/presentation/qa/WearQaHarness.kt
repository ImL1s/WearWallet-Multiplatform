package com.cbstudio.wearwallet.presentation.qa

import com.cbstudio.wearwallet.BuildConfig
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionDirection
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import com.cbstudio.wearwallet.core.domain.model.TransactionType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import com.cbstudio.wearwallet.presentation.util.isEmulatorDevice

/**
 * Debug/emulator QA overlay. Never a production data source and never evidence of
 * signing, broadcast, or mainnet success.
 *
 * Unit tests set [overrideActive]. Wear OS emulator debug builds enable it via
 * [isEmulatorDevice]. Release builds stay off.
 */
internal object WearQaHarness {
    /** JVM tests only. Release builds ignore this via [computeIsActive]. */
    @Volatile
    var overrideActive: Boolean? = null

    fun reset() {
        overrideActive = null
    }

    fun isActive(): Boolean {
        return try {
            computeIsActive(
                debugBuild = BuildConfig.DEBUG,
                emulator = isEmulatorDevice(),
                overrideActive = overrideActive
            )
        } catch (_: Throwable) {
            false
        }
    }

    fun computeIsActive(
        debugBuild: Boolean,
        emulator: Boolean,
        overrideActive: Boolean?
    ): Boolean {
        if (!debugBuild) return false
        overrideActive?.let { return it }
        return emulator
    }
}

/**
 * Local well-known public-address fixtures. Not a project wallet, seed, or funded account.
 */
internal data class HistoryPageMerge(
    val transactions: List<Transaction>,
    val hasMore: Boolean
)

internal object WearQaFixtures {
    const val BANNER_TEXT = "QA 假資料 · 非主網"
    const val RECIPIENT_ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"
    const val QR_EIP681 = "ethereum:0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045@1"
    const val CONTACT_ID = "qa-contact-vitalik"
    const val CONTACT_NAME = "QA Vitalik"
    const val TOKEN_SYMBOL = "ETH"
    const val TOKEN_BALANCE = 1.0
    const val TX_ID = "qa-tx-eth-outgoing"
    const val TX_HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val FROM_ADDRESS = "0x1111111111111111111111111111111111111111"

    val nativeEthToken = TokenTransferManager.TokenInfo(
        chainType = MultiChainType.ETHEREUM,
        contractAddress = "",
        symbol = TOKEN_SYMBOL,
        name = "Ethereum",
        decimals = 18,
        standard = TokenTransferManager.TokenStandard.NATIVE
    )

    val nativeEthBalance = TokenTransferManager.TokenBalance(
        token = nativeEthToken,
        balance = TOKEN_BALANCE,
        rawBalance = "1000000000000000000",
        formattedBalance = "1",
        usdValue = 0.0
    )

    val sampleTransaction = Transaction(
        id = TX_ID,
        hash = TX_HASH,
        walletId = "qa-wallet",
        walletAddress = FROM_ADDRESS,
        from = FROM_ADDRESS,
        to = RECIPIENT_ADDRESS,
        value = "1000000000000000",
        tokenSymbol = TOKEN_SYMBOL,
        tokenDecimals = 18,
        nonce = 0,
        chainType = ChainType.ETHEREUM,
        status = TransactionStatus.CONFIRMED,
        type = TransactionType.TRANSFER,
        direction = TransactionDirection.OUTGOING,
        confirmations = 12
    )

    val sampleContact = AddressContact.create(
        name = CONTACT_NAME,
        address = RECIPIENT_ADDRESS,
        chainType = ChainType.ETHEREUM
    ).copy(id = CONTACT_ID)

    fun overlayTokenBalances(
        networkBalances: List<TokenTransferManager.TokenBalance>,
        harnessActive: Boolean
    ): List<TokenTransferManager.TokenBalance> {
        if (!harnessActive) return networkBalances
        val hasPositiveNative = networkBalances.any {
            it.token.symbol.equals(TOKEN_SYMBOL, ignoreCase = true) && it.balance > 0.0
        }
        return if (hasPositiveNative) networkBalances else listOf(nativeEthBalance) + networkBalances
    }

    fun overlayTransactions(
        networkTransactions: List<Transaction>,
        harnessActive: Boolean
    ): List<Transaction> {
        if (!harnessActive) return networkTransactions
        if (networkTransactions.any { it.hash.equals(TX_HASH, ignoreCase = true) || it.id == TX_ID }) {
            return networkTransactions
        }
        return listOf(sampleTransaction) + networkTransactions
    }

    fun overlayContacts(
        networkContacts: List<AddressContact>,
        chain: ChainType,
        harnessActive: Boolean
    ): List<AddressContact> {
        if (!harnessActive || chain != ChainType.ETHEREUM) return networkContacts
        if (networkContacts.any { it.address.equals(RECIPIENT_ADDRESS, ignoreCase = true) }) {
            return networkContacts
        }
        return listOf(sampleContact) + networkContacts
    }

    fun findTransaction(
        transactionId: String,
        networkTransactions: List<Transaction>,
        harnessActive: Boolean
    ): Transaction? {
        val pool = overlayTransactions(networkTransactions, harnessActive)
        val needle = transactionId.trim()
        if (needle.isEmpty()) return null
        return pool.firstOrNull {
            it.hash.equals(needle, ignoreCase = true) || it.id.equals(needle, ignoreCase = true)
        }
    }

    fun acceptSimulatedQrScan(payload: String, harnessActive: Boolean): String? {
        if (!harnessActive) return null
        val trimmed = payload.trim()
        return trimmed.ifEmpty { null }
    }

    @Suppress("UNUSED_PARAMETER")
    fun retainedLoadError(networkError: String?, overlayNonEmpty: Boolean): String? {
        return networkError
    }

    fun mergeHistoryPage(
        existing: List<Transaction>,
        networkPage: List<Transaction>,
        refresh: Boolean,
        harnessActive: Boolean
    ): HistoryPageMerge {
        val hasMore = networkPage.size == 20
        val replacePage = refresh || existing.isEmpty()
        if (replacePage) {
            return HistoryPageMerge(
                transactions = overlayTransactions(networkPage, harnessActive),
                hasMore = hasMore
            )
        }
        val incoming = networkPage.filter { candidate ->
            existing.none { existingTx ->
                existingTx.hash.equals(candidate.hash, ignoreCase = true) ||
                    (candidate.id.isNotBlank() && existingTx.id == candidate.id)
            }
        }
        return HistoryPageMerge(
            transactions = existing + incoming,
            hasMore = hasMore
        )
    }
}
