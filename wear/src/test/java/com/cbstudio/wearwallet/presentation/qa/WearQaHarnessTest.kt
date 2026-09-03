package com.cbstudio.wearwallet.presentation.qa

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.TransactionDirection
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import com.cbstudio.wearwallet.core.security.BackendIdentity
import com.cbstudio.wearwallet.core.security.BuildType
import com.cbstudio.wearwallet.core.security.CapabilityDecision
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.Network
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.Platform
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import com.cbstudio.wearwallet.core.security.SignerImplementation
import com.cbstudio.wearwallet.core.security.WalletType
import com.cbstudio.wearwallet.presentation.navigation.decodeTransactionDetailId
import com.cbstudio.wearwallet.presentation.navigation.extractAddressFromQrPayload
import com.cbstudio.wearwallet.presentation.navigation.sendAddressRoute
import com.cbstudio.wearwallet.presentation.navigation.sendTokenRoute
import com.cbstudio.wearwallet.presentation.navigation.tokenSendIsNative
import com.cbstudio.wearwallet.presentation.navigation.transactionDetailRoute
import com.cbstudio.wearwallet.presentation.shouldExposeTestTagsAsResourceId
import com.cbstudio.wearwallet.presentation.util.DEBUG_EMULATOR_AUTH_TTL_MS
import com.cbstudio.wearwallet.presentation.util.DebugEmulatorAuth
import com.cbstudio.wearwallet.presentation.util.isEmulatorSignals
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.amountStepTokenSymbol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM harness for Wear flows that cannot be proven by an empty demo wallet
 * or a phone-camera QR relay. Overlay data is local QA only — not mainnet proof.
 */
class WearQaHarnessTest {

    @After
    fun tearDown() {
        WearQaHarness.reset()
    }

    @Test
    fun `harness is off by default in unit tests`() {
        assertFalse(WearQaHarness.isActive())
    }

    @Test
    fun `forced harness injects a positive ETH balance for token-row send`() {
        WearQaHarness.overrideActive = true
        val overlaid = WearQaFixtures.overlayTokenBalances(emptyList(), WearQaHarness.isActive())
        val eth = overlaid.single { it.token.symbol == WearQaFixtures.TOKEN_SYMBOL }
        assertTrue(eth.balance > 0.0)
        assertTrue(tokenSendIsNative(eth.token.contractAddress))
    }

    @Test
    fun `harness does not invent token rows when inactive`() {
        val overlaid = WearQaFixtures.overlayTokenBalances(emptyList(), harnessActive = false)
        assertTrue(overlaid.isEmpty())
    }

    @Test
    fun `forced harness injects a transaction that detail lookup can resolve`() {
        WearQaHarness.overrideActive = true
        val txs = WearQaFixtures.overlayTransactions(emptyList(), WearQaHarness.isActive())
        assertEquals(1, txs.size)
        val encoded = transactionDetailRoute(txs.first().hash)
        val encodedId = encoded.removePrefix("transaction_detail/")
        val decoded = decodeTransactionDetailId(encodedId)
        val found = WearQaFixtures.findTransaction(decoded, emptyList(), WearQaHarness.isActive())
        assertNotNull(found)
        assertEquals(WearQaFixtures.TX_HASH, found!!.hash)
        assertEquals(TransactionDirection.OUTGOING, found.direction)
    }

    @Test
    fun `forced harness injects a contact that send route can prefill`() {
        WearQaHarness.overrideActive = true
        val contacts = WearQaFixtures.overlayContacts(
            emptyList(),
            ChainType.ETHEREUM,
            WearQaHarness.isActive()
        )
        val contact = contacts.single()
        assertEquals(WearQaFixtures.CONTACT_ID, contact.id)
        assertEquals(WearQaFixtures.RECIPIENT_ADDRESS, contact.address)
        val route = sendAddressRoute(contact.address)
        assertTrue(route.contains("address="))
        assertEquals(
            WearQaFixtures.RECIPIENT_ADDRESS,
            java.net.URLDecoder.decode(route.substringAfter("address="), "UTF-8")
        )
    }

    @Test
    fun `QR fixture without a phone camera yields a sendable address`() {
        assertEquals(
            WearQaFixtures.RECIPIENT_ADDRESS,
            extractAddressFromQrPayload(WearQaFixtures.QR_EIP681)
        )
    }

    @Test
    fun `token send route encodes contract metadata`() {
        val route = sendTokenRoute(
            tokenAddress = "erc20-usdt",
            tokenSymbol = "USDT",
            tokenDecimals = 6,
            tokenName = "Tether USD"
        )
        assertTrue(route.contains("tokenSymbol=USDT"))
        assertTrue(route.contains("tokenDecimals=6"))
        assertFalse(tokenSendIsNative("erc20-usdt"))
    }

    @Test
    fun `amount step shows selected token symbol not a hardcoded null ETH`() {
        val usdt = Token(
            address = "erc20-usdt",
            name = "Tether USD",
            symbol = "USDT",
            decimals = 6,
            chainType = ChainType.ETHEREUM
        )
        assertEquals("USDT", amountStepTokenSymbol(usdt))
        assertNull(amountStepTokenSymbol(null))
    }

    @Test
    fun `sign handle is not issued off the debug emulator`() {
        assertFalse(DebugEmulatorAuth.canUse())
        assertNull(
            DebugEmulatorAuth.issueSignHandle(
                keyId = "ww_key_mock",
                walletId = "wallet-1",
                intentFingerprint = "aa"
            )
        )
    }

    @Test
    fun `emulator debug gate allows software sign and denies broadcast`() {
        val gate = AllowDevCapabilityGate(allowBroadcast = false)
        val sign = emulatorRequest(Operation.SOFTWARE_SIGN)
        val broadcast = emulatorRequest(Operation.BROADCAST)
        assertTrue(gate.checkCapability(sign) is CapabilityDecision.Allowed)
        val denied = gate.checkCapability(broadcast)
        assertTrue(denied is CapabilityDecision.Denied)
        assertTrue((denied as CapabilityDecision.Denied).reason.contains("BROADCAST"))
    }

    @Test
    fun `release mainnet software send is still denied so this is not a mainnet pass`() {
        val release = ReleaseProductionCapabilityGate()
        val sign = emulatorRequest(Operation.SOFTWARE_SIGN)
        val broadcast = emulatorRequest(Operation.BROADCAST)
        assertTrue(release.checkCapability(sign) is CapabilityDecision.Denied)
        assertTrue(release.checkCapability(broadcast) is CapabilityDecision.Denied)
    }

    @Test
    fun `overlay keeps a real positive balance ahead of the fixture`() {
        WearQaHarness.overrideActive = true
        val live = TokenTransferManager.TokenBalance(
            token = WearQaFixtures.nativeEthToken,
            balance = 42.0,
            rawBalance = "42000000000000000000",
            formattedBalance = "42",
            usdValue = 1.0
        )
        val overlaid = WearQaFixtures.overlayTokenBalances(listOf(live), true)
        assertEquals(1, overlaid.size)
        assertEquals(42.0, overlaid.first().balance, 0.0)
    }

    @Test
    fun `release build ignores overrideActive so Play APK cannot enable overlay`() {
        assertFalse(
            WearQaHarness.computeIsActive(
                debugBuild = false,
                emulator = true,
                overrideActive = true
            )
        )
        assertFalse(
            WearQaHarness.computeIsActive(
                debugBuild = false,
                emulator = false,
                overrideActive = true
            )
        )
    }

    @Test
    fun `debug emulator is active without an override`() {
        assertTrue(
            WearQaHarness.computeIsActive(
                debugBuild = true,
                emulator = true,
                overrideActive = null
            )
        )
    }

    @Test
    fun `debug override false disables overlay even on emulator`() {
        assertFalse(
            WearQaHarness.computeIsActive(
                debugBuild = true,
                emulator = true,
                overrideActive = false
            )
        )
    }

    @Test
    fun `debug override true enables overlay off emulator for JVM tests`() {
        assertTrue(
            WearQaHarness.computeIsActive(
                debugBuild = true,
                emulator = false,
                overrideActive = true
            )
        )
    }

    @Test
    fun `generic-only fingerprint is not treated as emulator`() {
        assertFalse(
            isEmulatorSignals(
                fingerprint = "generic/aosp_cf_arm64_only/aosp_cf_arm64_only:15/AP3A/",
                model = "Pixel Watch",
                manufacturer = "Google",
                hardware = "qcom"
            )
        )
    }

    @Test
    fun `ranchu or sdk_gwear signals are emulator`() {
        assertTrue(
            isEmulatorSignals(
                fingerprint = "google/sdk_gwear_arm64/generic_arm64:15/",
                model = "sdk_gwear_arm64",
                manufacturer = "Google",
                hardware = "ranchu"
            )
        )
        assertTrue(
            isEmulatorSignals(
                fingerprint = "generic/google/google:9/PPR1.180610.011/emulator:user/release-keys",
                model = "Android SDK built for arm64",
                manufacturer = "unknown",
                hardware = "goldfish"
            )
        )
    }

    @Test
    fun `simulated scan is refused when harness is inactive`() {
        assertNull(
            WearQaFixtures.acceptSimulatedQrScan(
                WearQaFixtures.QR_EIP681,
                harnessActive = false
            )
        )
    }

    @Test
    fun `simulated scan returns payload when harness is active`() {
        assertEquals(
            WearQaFixtures.QR_EIP681,
            WearQaFixtures.acceptSimulatedQrScan(
                WearQaFixtures.QR_EIP681,
                harnessActive = true
            )
        )
        assertNull(WearQaFixtures.acceptSimulatedQrScan("   ", harnessActive = true))
    }

    @Test
    fun `overlay does not clear a network error`() {
        val networkError = "載入交易記錄失敗: timeout"
        val fallback = WearQaFixtures.overlayTransactions(emptyList(), harnessActive = true)
        assertTrue(fallback.isNotEmpty())
        assertEquals(
            networkError,
            WearQaFixtures.retainedLoadError(
                networkError = networkError,
                overlayNonEmpty = fallback.isNotEmpty()
            )
        )
    }

    @Test
    fun `history refresh uses network page size for hasMore not overlay size`() {
        val network = List(19) { index ->
            WearQaFixtures.sampleTransaction.copy(
                id = "net-$index",
                hash = "0x${index.toString().padStart(64, 'b')}"
            )
        }
        val merged = WearQaFixtures.mergeHistoryPage(
            existing = emptyList(),
            networkPage = network,
            refresh = true,
            harnessActive = true
        )
        assertEquals(20, merged.transactions.size)
        assertFalse(merged.hasMore)
        assertEquals(WearQaFixtures.TX_ID, merged.transactions.first().id)
    }

    @Test
    fun `history loadMore keeps new rows that share a blank id`() {
        val firstPage = List(20) { index ->
            WearQaFixtures.sampleTransaction.copy(
                id = "",
                hash = "0x${index.toString().padStart(64, 'b')}"
            )
        }
        val first = WearQaFixtures.mergeHistoryPage(
            existing = emptyList(),
            networkPage = firstPage,
            refresh = true,
            harnessActive = false
        )
        assertEquals(20, first.transactions.size)
        assertTrue(first.hasMore)
        val extra = WearQaFixtures.sampleTransaction.copy(
            id = "",
            hash = "0x${"d".repeat(64)}"
        )
        val second = WearQaFixtures.mergeHistoryPage(
            existing = first.transactions,
            networkPage = listOf(extra),
            refresh = false,
            harnessActive = false
        )
        assertEquals(21, second.transactions.size)
        assertFalse(second.hasMore)
    }

    @Test
    fun `history first page does not overlay when harness inactive`() {
        val merged = WearQaFixtures.mergeHistoryPage(
            existing = emptyList(),
            networkPage = emptyList(),
            refresh = false,
            harnessActive = false
        )
        assertTrue(merged.transactions.isEmpty())
        assertFalse(merged.hasMore)
    }

    @Test
    fun `history first page overlays even when refresh is false`() {
        val merged = WearQaFixtures.mergeHistoryPage(
            existing = emptyList(),
            networkPage = emptyList(),
            refresh = false,
            harnessActive = true
        )
        assertEquals(1, merged.transactions.size)
        assertEquals(WearQaFixtures.TX_ID, merged.transactions.first().id)
        assertFalse(merged.hasMore)
    }

    @Test
    fun `history loadMore does not duplicate the fixture row`() {
        val first = WearQaFixtures.mergeHistoryPage(
            existing = emptyList(),
            networkPage = emptyList(),
            refresh = true,
            harnessActive = true
        )
        val more = List(20) { index ->
            WearQaFixtures.sampleTransaction.copy(
                id = "page-$index",
                hash = "0x${index.toString().padStart(64, 'c')}"
            )
        }
        val second = WearQaFixtures.mergeHistoryPage(
            existing = first.transactions,
            networkPage = more,
            refresh = false,
            harnessActive = true
        )
        assertEquals(1, second.transactions.count { it.id == WearQaFixtures.TX_ID })
        assertTrue(second.hasMore)
        assertEquals(21, second.transactions.size)
    }

    @Test
    fun `transaction detail decode is idempotent`() {
        val encoded = java.net.URLEncoder.encode(WearQaFixtures.TX_HASH, "UTF-8")
        val once = decodeTransactionDetailId(encoded)
        val twice = decodeTransactionDetailId(once)
        assertEquals(WearQaFixtures.TX_HASH, once)
        assertEquals(WearQaFixtures.TX_HASH, twice)
    }

    @Test
    fun `emulator sign handle ttl matches send biometric window`() {
        assertEquals(10_000L, DEBUG_EMULATOR_AUTH_TTL_MS)
    }

    @Test
    fun `test tags as resource ids are debug-only`() {
        assertTrue(shouldExposeTestTagsAsResourceId(debugBuild = true))
        assertFalse(shouldExposeTestTagsAsResourceId(debugBuild = false))
    }

    @Test
    fun `qa banner copy is explicit that overlay is not mainnet`() {
        assertTrue(WearQaFixtures.BANNER_TEXT.contains("QA"))
        assertTrue(WearQaFixtures.BANNER_TEXT.contains("非主網"))
    }

    private fun emulatorRequest(operation: Operation): CapabilityRequest {
        return CapabilityRequest.createForTesting(
            operation = operation,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.DEBUG,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.SOFTWARE_MNEMONIC,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
    }
}
