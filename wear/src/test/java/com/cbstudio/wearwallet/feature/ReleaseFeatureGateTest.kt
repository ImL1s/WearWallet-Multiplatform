package com.cbstudio.wearwallet.feature

import com.cbstudio.wearwallet.presentation.navigation.WalletRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseFeatureGateTest {

    private val releaseBlockedRoutes = setOf(
        WalletRoute.WEAR_FI,
        WalletRoute.DEBIT_CARD,
        WalletRoute.AI_ASSISTANT,
        WalletRoute.DEFI_ONE_CLICK,
        WalletRoute.AI_INVESTMENT_ADVISOR,
        WalletRoute.NFC_PAYMENT,
        WalletRoute.WRIST_TRANSFER,
    )

    @Test
    fun `release registeredRoutes omit maintenance experimental and nfc sign routes`() {
        val routes = ReleaseFeatureGate.registeredRoutes(isRelease = true)
        for (route in releaseBlockedRoutes) {
            assertFalse(
                "release must not register $route",
                routes.any { it == route || it.startsWith("$route?") || it.startsWith("$route/") }
            )
            assertFalse(
                "release must deny $route",
                ReleaseFeatureGate.allowsRoute(route, isRelease = true)
            )
        }
    }

    @Test
    fun `debug registeredRoutes still include maintenance screens`() {
        val routes = ReleaseFeatureGate.registeredRoutes(isRelease = false)
        assertTrue(WalletRoute.AI_ASSISTANT in routes)
        assertTrue(WalletRoute.DEBIT_CARD in routes)
        assertTrue(WalletRoute.WRIST_TRANSFER in routes)
    }

    @Test
    fun `matrix covers required capabilities and none are PRODUCTION`() {
        val ids = WearCapability.entries.map { it.id }.toSet()
        val required = setOf(
            "wear_send",
            "wear_receive",
            "wallet_backup_create_import",
            "keystone",
            "swap",
            "wear_fi",
            "nfc",
            "debit_card",
            "ai_assistant",
            "direct_kmp",
            "watchos",
            "mobile_companion",
            "broadcast",
            "mainnet_software_sign",
        )
        assertTrue("missing matrix rows: ${required - ids}", ids.containsAll(required))
        assertTrue(
            "nothing in this tree is PRODUCTION until Task D and device evidence exist",
            WearCapability.entries.none { it.maturity == FeatureMaturity.PRODUCTION }
        )
        assertEquals(FeatureMaturity.BETA, WearCapability.WEAR_SEND.maturity)
        assertEquals(FeatureMaturity.MAINTENANCE, WearCapability.WEAR_FI.maturity)
        assertEquals(FeatureMaturity.MAINTENANCE, WearCapability.NFC.maturity)
        assertEquals(FeatureMaturity.MAINTENANCE, WearCapability.DEBIT_CARD.maturity)
        assertEquals(FeatureMaturity.MAINTENANCE, WearCapability.AI_ASSISTANT.maturity)
        assertEquals(FeatureMaturity.MAINTENANCE, WearCapability.DIRECT_KMP.maturity)
        assertEquals(FeatureMaturity.UNSUPPORTED, WearCapability.BROADCAST.maturity)
        assertEquals(FeatureMaturity.UNSUPPORTED, WearCapability.MAINNET_SOFTWARE_SIGN.maturity)
    }

    @Test
    fun `FEATURE_STATUS md matches WearCapability registry`() {
        val md = locateFeatureStatus().readText()
        for (capability in WearCapability.entries) {
            assertTrue(
                "docs/FEATURE_STATUS.md must list ${capability.id}",
                md.contains(capability.id)
            )
            assertTrue(
                "docs/FEATURE_STATUS.md must state ${capability.maturity} for ${capability.id}",
                md.contains(capability.maturity.name)
            )
        }
        assertFalse(
            "public docs must not claim 完整錢包",
            md.contains("完整錢包")
        )
    }

    companion object {
        fun locateFeatureStatus(): File {
            val candidates = listOf(
                File("../docs/FEATURE_STATUS.md"),
                File("docs/FEATURE_STATUS.md"),
            )
            return candidates.firstOrNull { it.isFile }
                ?: error("docs/FEATURE_STATUS.md not found from ${File(".").canonicalPath}")
        }
    }
}
