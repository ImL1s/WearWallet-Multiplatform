package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wear send confirmation must show full to-address, chainId, nonce, and
 * contract address (token send). Truncation is allowed for layout only if a
 * full-address surface remains in the confirmation composable.
 */
class SendConfirmationReviewSourceTest {

    @Test
    fun `ModernConfirmationScreen shows full to-address chainId nonce and contract`() {
        val source = locateSendScreen().readText()
        val confirmation = extractFunction(source, "ModernConfirmationScreen")

        assertFalse(
            "confirmation must not store/display recipient as take(6)…takeLast(4) only",
            confirmation.contains("recipientAddress.take(6)") ||
                confirmation.contains("take(6)}...${'$'}{recipientAddress.takeLast(4)"),
        )
        assertTrue(
            "confirmation must render the full to-address",
            confirmation.contains("recipientAddress") &&
                !confirmation.contains("recipientAddress.take("),
        )
        assertTrue(
            "confirmation must show chainId",
            confirmation.contains("chainId", ignoreCase = true),
        )
        assertTrue(
            "confirmation must show nonce",
            confirmation.contains("nonce", ignoreCase = true),
        )
        assertTrue(
            "confirmation must show contract address for token/contract send",
            confirmation.contains("contractAddress", ignoreCase = true) ||
                confirmation.contains("tokenContract", ignoreCase = true),
        )
    }

    @Test
    fun `post-broadcast screen must not claim on-chain confirmation`() {
        val source = locateSendScreen().readText()
        val success = extractFunction(source, "ModernSuccessScreen")
        assertFalse(
            "broadcast hash must not be labeled 交易成功 / 已確認",
            success.contains("交易成功") || success.contains("已確認"),
        )
        assertTrue(
            "broadcast UI must say submitted/pending, not confirmed",
            success.contains("已送出") || success.contains("待確認") || success.contains("廣播"),
        )
    }

    companion object {
        fun locateSendScreen(): File {
            val candidates = listOf(
                File("src/main/java/com/cbstudio/wearwallet/presentation/wallet/screens/main/tx/SendScreen.kt"),
                File("wear/src/main/java/com/cbstudio/wearwallet/presentation/wallet/screens/main/tx/SendScreen.kt"),
            )
            return candidates.firstOrNull { it.isFile }
                ?: error("SendScreen.kt not found from ${File(".").canonicalPath}")
        }

        fun extractFunction(source: String, name: String): String {
            val marker = "fun $name"
            val start = source.indexOf(marker)
            require(start >= 0) { "$name not found in SendScreen.kt" }
            val brace = source.indexOf('{', start)
            var depth = 0
            for (i in brace until source.length) {
                when (source[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return source.substring(start, i + 1)
                    }
                }
            }
            error("unbalanced braces extracting $name")
        }
    }
}
