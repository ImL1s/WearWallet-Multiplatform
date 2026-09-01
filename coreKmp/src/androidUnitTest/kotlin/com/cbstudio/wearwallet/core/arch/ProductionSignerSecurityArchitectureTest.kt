package com.cbstudio.wearwallet.core.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture Test: Production Signer Security & Raw-Key Elimination (Milestone 1 / P1-1 & P1-4)
 *
 * Enforces:
 * 1. Zero `EVMTransactionService` or `CryptoService` files or class declarations in production source sets (`wear/src/main`, `coreKmp/src/commonMain`).
 * 2. Zero `EVMTransactionService` or `CryptoService` bean registrations in Wear DI (`WearKoinModule.kt`, `TransactionModule.kt`, `DebitCardModule.kt`).
 * 3. Zero occurrences of `validateAndCleanPrivateKey` helper in production source sets.
 * 4. Zero sign/send/derive/bridge methods accepting raw `privateKey: String` or `privateKeyHex: String` in `wear/src/main`.
 * 5. Zero EVMTransactionService residual files, examples, or documentation in `wear/src/main`.
 */
class ProductionSignerSecurityArchitectureTest {

    private val productionSourceDirectories = listOf(
        "coreKmp/src/commonMain",
        "wear/src/main"
    )

    private val prohibitedProductionClasses = listOf(
        "EVMTransactionService",
        "CryptoService"
    )

    @Test
    fun test_zero_EVMTransactionService_or_CryptoService_in_production_source_sets() {
        val rootDir = findProjectRoot()
        val violations = mutableListOf<String>()

        for (relDir in productionSourceDirectories) {
            val dir = File(rootDir, relDir)
            if (!dir.exists()) continue

            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    for (prohibited in prohibitedProductionClasses) {
                        if (file.name == "$prohibited.kt" || file.name == "$prohibited.java") {
                            violations.add("Prohibited production class file: ${file.relativeTo(rootDir).path}")
                        }
                    }

                    if (file.extension.lowercase() in listOf("kt", "java")) {
                        val lines = file.readLines()
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                                return@forEachIndexed
                            }
                            for (prohibited in prohibitedProductionClasses) {
                                if (trimmed.matches(Regex("""^(?:public\s+|internal\s+|open\s+|final\s+|sealed\s+)*class\s+$prohibited\b.*"""))) {
                                    violations.add("Prohibited class definition '$prohibited' at ${file.relativeTo(rootDir).path}:${index + 1}")
                                }
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: Found EVMTransactionService or CryptoService in production source sets:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun test_zero_EVMTransactionService_or_CryptoService_in_Wear_DI_modules() {
        val rootDir = findProjectRoot()
        val diDir = File(rootDir, "wear/src/main/java/com/cbstudio/wearwallet/di")
        val violations = mutableListOf<String>()

        if (diDir.exists()) {
            diDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() in listOf("kt", "java")) {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        if (trimmed.contains("EVMTransactionService(") || trimmed.contains("CryptoService(") ||
                            trimmed.contains("single { EVMTransactionService") || trimmed.contains("single { CryptoService") ||
                            trimmed.contains("factory { EVMTransactionService") || trimmed.contains("factory { CryptoService")
                        ) {
                            violations.add("Prohibited DI registration in ${file.relativeTo(rootDir).path}:${index + 1} -> $trimmed")
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: Found EVMTransactionService or CryptoService in Wear DI modules:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun test_zero_occurrences_of_validateAndCleanPrivateKey_in_production() {
        val rootDir = findProjectRoot()
        val violations = mutableListOf<String>()

        for (relDir in productionSourceDirectories) {
            val dir = File(rootDir, relDir)
            if (!dir.exists()) continue

            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() in listOf("kt", "java")) {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        if (trimmed.contains("validateAndCleanPrivateKey")) {
                            violations.add("Found validateAndCleanPrivateKey at ${file.relativeTo(rootDir).path}:${index + 1}")
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: validateAndCleanPrivateKey found in production source sets:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun test_zero_privateKey_string_methods_in_wear_production() {
        val rootDir = findProjectRoot()
        val violations = mutableListOf<String>()

        val signSendDerivePattern = Regex("""(?i)\bfun\s+[a-zA-Z0-9_]*(?:sign|send|derive|bridge)[a-zA-Z0-9_]*\s*\([^)]*\b(?:privateKey|privateKeyHex)\s*:\s*String\b[^)]*\)""")
        val singleLineParamPattern = Regex("""\b(?:privateKey|privateKeyHex)\s*:\s*String\b""")

        val wearMainDir = File(rootDir, "wear/src/main")
        if (wearMainDir.exists()) {
            wearMainDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() in listOf("kt", "java")) {
                    // PushProtocolConfig / SetupViewModel manages Push Protocol notification channel setup (not blockchain signing)
                    if (file.name == "PushProtocolConfig.kt" || file.name == "PushProtocolSetupViewModel.kt") {
                        return@forEach
                    }

                    val content = file.readText()
                    if (signSendDerivePattern.containsMatchIn(content)) {
                        violations.add("Found sign/send/derive/bridge method taking privateKey: String in ${file.relativeTo(rootDir).path}")
                    }

                    val lines = file.readLines()
                    var inSignSendDeriveFunc = false
                    var funcName = ""

                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }

                        if (trimmed.contains("fun ") && (trimmed.contains("sign", ignoreCase = true) || trimmed.contains("send", ignoreCase = true) || trimmed.contains("derive", ignoreCase = true) || trimmed.contains("bridge", ignoreCase = true))) {
                            inSignSendDeriveFunc = true
                            funcName = trimmed
                        }

                        if (inSignSendDeriveFunc && singleLineParamPattern.containsMatchIn(trimmed)) {
                            violations.add("Found raw privateKey parameter in function '$funcName' at ${file.relativeTo(rootDir).path}:${index + 1}")
                        }

                        if (inSignSendDeriveFunc && (trimmed.endsWith("{") || trimmed.endsWith("=") || (trimmed.contains(")") && !trimmed.contains("(")))) {
                            inSignSendDeriveFunc = false
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: Found sign/send/derive/bridge methods accepting raw privateKey: String in wear/src/main:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun test_zero_EVMTransactionService_residual_files_in_wear_production() {
        val rootDir = findProjectRoot()
        val violations = mutableListOf<String>()

        val wearMainDir = File(rootDir, "wear/src/main")
        if (wearMainDir.exists()) {
            wearMainDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.name.contains("EVMTransactionService") || file.name.contains("EVM_TRANSACTION_SERVICE"))) {
                    violations.add("Residual EVMTransactionService file found in wear/src/main: ${file.relativeTo(rootDir).path}")
                }
            }
        }

        assertTrue(
            "Architecture Violation: Found residual EVMTransactionService files in wear/src/main:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    private fun findProjectRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".")
        while (current.parentFile != null) {
            if (File(current, "settings.gradle.kts").exists() || File(current, "settings.gradle").exists()) {
                return current
            }
            current = current.parentFile
        }
        return File(System.getProperty("user.dir") ?: ".")
    }
}
