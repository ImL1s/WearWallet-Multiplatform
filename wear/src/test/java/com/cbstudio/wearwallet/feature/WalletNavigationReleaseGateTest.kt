package com.cbstudio.wearwallet.feature

import com.cbstudio.wearwallet.di.directKmpModule
import com.cbstudio.wearwallet.di.getAllWearModules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release navigation must not register maintenance/demo/experimental toy routes.
 * Source is the current [WalletNavigation.kt] graph; debug-only `if (!isRelease)`
 * blocks are stripped so this test models a release binary.
 */
class WalletNavigationReleaseGateTest {

    @Test
    fun `release WalletNavigation does not register gated experimental routes`() {
        val source = stripDebugOnlyBlocks(stripComments(locateWalletNavigation().readText()))
        for (name in FORBIDDEN_ROUTE_CONSTS) {
            val pattern = Regex("""composable\s*\(\s*(?:route\s*=\s*)?WalletRoute\.$name\b""")
            assertFalse(
                "release navigation must not register WalletRoute.$name",
                pattern.containsMatchIn(source)
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

        fun stripDebugOnlyBlocks(source: String): String {
            val needle = Regex("""if\s*\(\s*!isRelease\s*\)\s*\{""")
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
            error("unbalanced braces in WalletNavigation.kt debug-only block")
        }
    }
}
