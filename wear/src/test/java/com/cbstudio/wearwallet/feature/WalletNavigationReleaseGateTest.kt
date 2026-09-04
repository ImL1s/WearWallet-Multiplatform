package com.cbstudio.wearwallet.feature

import com.cbstudio.wearwallet.di.directKmpModule
import com.cbstudio.wearwallet.di.getAllWearModules
import com.cbstudio.wearwallet.presentation.navigation.WalletRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release navigation must not register maintenance/demo/experimental toy routes.
 *
 * Production registration is [ReleaseFeatureGate.allowsRoute] / [ReleaseFeatureGate.registeredRoutes],
 * not a parallel `if (!isRelease)` boolean. Source is scanned so an unconditional
 * `composable(WalletRoute.*)` still fails even if the enum stays honest.
 */
class WalletNavigationReleaseGateTest {

    @Test
    fun `WalletNavigation gates live maintenance routes with ReleaseFeatureGate allowsRoute`() {
        val source = stripComments(locateWalletNavigation().readText())
        assertFalse(
            "WalletNavigation must not use a parallel !isRelease boolean for route registration",
            Regex("""if\s*\(\s*!isRelease\s*\)""").containsMatchIn(source),
        )
        for (name in LIVE_GATED_ROUTE_CONSTS) {
            assertTrue(
                "WalletNavigation.kt must wrap WalletRoute.$name with ReleaseFeatureGate.allowsRoute",
                Regex(
                    """ReleaseFeatureGate\.allowsRoute\s*\(\s*WalletRoute\.$name\b"""
                ).containsMatchIn(source),
            )
        }
    }

    @Test
    fun `release WalletNavigation does not register gated experimental routes`() {
        val source = stripComments(locateWalletNavigation().readText())
        for (name in FORBIDDEN_ROUTE_CONSTS) {
            val stripped = stripAllowsRouteBlockFor(source, name)
            val pattern = Regex("""composable\s*\(\s*(?:route\s*=\s*)?WalletRoute\.$name\b""")
            assertFalse(
                "release navigation must not register WalletRoute.$name unconditionally",
                pattern.containsMatchIn(stripped),
            )
        }
    }

    @Test
    fun `release registeredRoutes omit gated experimental routes`() {
        val routes = ReleaseFeatureGate.registeredRoutes(isRelease = true)
        for (route in FORBIDDEN_ROUTES) {
            assertFalse(
                "ReleaseFeatureGate.registeredRoutes(isRelease=true) must not include $route",
                routes.any { it == route || it.startsWith("$route?") || it.startsWith("$route/") },
            )
            assertFalse(
                "ReleaseFeatureGate.allowsRoute must deny $route in release",
                ReleaseFeatureGate.allowsRoute(route, isRelease = true),
            )
        }
    }

    @Test
    fun `directKmpModule is not loaded in release Wear Koin getAllWearModules`() {
        val modules = getAllWearModules()
        assertTrue(modules.isNotEmpty())
        assertFalse(
            "empty DirectKmpModule must not be loaded in release Wear Koin",
            modules.any { it === directKmpModule }
        )
    }

    companion object {
        val FORBIDDEN_ROUTE_CONSTS = listOf(
            "WEAR_FI",
            "DEBIT_CARD",
            "AI_ASSISTANT",
            "DEFI_ONE_CLICK",
            "AI_INVESTMENT_ADVISOR",
            "NFC_PAYMENT",
            "WRIST_TRANSFER",
        )

        val LIVE_GATED_ROUTE_CONSTS = listOf(
            "DEBIT_CARD",
            "AI_ASSISTANT",
            "WRIST_TRANSFER",
        )

        val FORBIDDEN_ROUTES = listOf(
            WalletRoute.WEAR_FI,
            WalletRoute.DEBIT_CARD,
            WalletRoute.AI_ASSISTANT,
            WalletRoute.DEFI_ONE_CLICK,
            WalletRoute.AI_INVESTMENT_ADVISOR,
            WalletRoute.NFC_PAYMENT,
            WalletRoute.WRIST_TRANSFER,
        )

        fun locateWalletNavigation(): File {
            val candidates = listOf(
                File("src/main/java/com/cbstudio/wearwallet/presentation/navigation/WalletNavigation.kt"),
                File("wear/src/main/java/com/cbstudio/wearwallet/presentation/navigation/WalletNavigation.kt"),
            )
            return candidates.firstOrNull { it.isFile }
                ?: error("WalletNavigation.kt not found from ${File(".").canonicalPath}")
        }

        fun stripComments(source: String): String {
            val noBlock = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            return noBlock.replace(Regex("//.*?$", RegexOption.MULTILINE), "")
        }

        fun stripAllowsRouteBlockFor(source: String, routeConst: String): String {
            val needle = Regex(
                """if\s*\(\s*ReleaseFeatureGate\.allowsRoute\s*\(\s*WalletRoute\.$routeConst\b[^)]*\)\s*\)\s*\{"""
            )
            val sb = StringBuilder(source)
            while (true) {
                val match = needle.find(sb)
                    ?: break
                val openBrace = match.range.last
                val close = matchingCloseBrace(sb, openBrace)
                sb.delete(match.range.first, close + 1)
            }
            return sb.toString()
        }

        private fun matchingCloseBrace(text: CharSequence, openIndex: Int): Int {
            var depth = 0
            for (i in openIndex until text.length) {
                when (text[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            error("unbalanced braces in WalletNavigation.kt allowsRoute block")
        }
    }
}
