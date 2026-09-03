package com.cbstudio.wearwallet.core.arch

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Architecture Test: Production Source Authenticator Isolation & Hardening
 *
 * Enforces:
 * 1. TestPlatformAuthenticator / TestAuthenticator NEVER exist in production source sets
 *    (coreKmp/src/commonMain, androidMain, iosMain, watchosMain, wear/src/main).
 * 2. E2ETestHelper backdoor does NOT exist in production sources.
 * 3. Zero references to TestPlatformAuthenticator symbols in production sources.
 * 4. ProofTokenVerifier.sign, AuthHandleRegistry.register, and AuthHandleRegistry.clearForTesting
 *    are internal, never public production APIs.
 */
class ProductionSourceAuthenticatorArchitectureTest {

    private val prohibitedClassNames = listOf(
        "TestPlatformAuthenticator",
        "TestAuthenticator"
    )

    private val productionSourceDirectories = listOf(
        "coreKmp/src/commonMain",
        "coreKmp/src/androidMain",
        "coreKmp/src/iosMain",
        "coreKmp/src/watchosMain",
        "wear/src/main"
    )

    private val sourceExtensions = listOf("kt", "java", "swift", "m", "h", "c", "cpp")

    @Test
    fun test_no_test_authenticator_files_or_symbols_in_production_source_sets() {
        val rootDir = findProjectRoot()
        val violations = mutableListOf<String>()

        for (relDir in productionSourceDirectories) {
            val dir = File(rootDir, relDir)
            if (!dir.exists()) continue

            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    // Check 1: File name rule
                    for (prohibited in prohibitedClassNames) {
                        if (file.name.contains(prohibited, ignoreCase = true)) {
                            violations.add("Prohibited test file in production: ${file.relativeTo(rootDir).path}")
                        }
                    }

                    // Check 2: File content symbol rule
                    if (sourceExtensions.contains(file.extension.lowercase())) {
                        val lines = file.readLines()
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            // Skip pure comment lines
                            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                                return@forEachIndexed
                            }
                            for (prohibited in prohibitedClassNames) {
                                if (line.contains(prohibited)) {
                                    violations.add(
                                        "Prohibited symbol '$prohibited' referenced at ${file.relativeTo(rootDir).path}:${index + 1}\n   --> $trimmed"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: Found test authenticator symbols in production source sets:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun test_e2e_test_helper_not_in_production_wear_main() {
        val rootDir = findProjectRoot()
        val wearMainDir = File(rootDir, "wear/src/main")
        val violations = mutableListOf<String>()

        wearMainDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.contains("E2ETestHelper", ignoreCase = true)) {
                violations.add("E2ETestHelper must not exist in production wear/src/main: ${file.relativeTo(rootDir).path}")
            }
        }

        assertTrue(
            "Architecture Violation: E2ETestHelper found in wear/src/main:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun test_ProofTokenVerifier_sign_and_AuthHandleRegistry_register_are_internal() {
        val rootDir = findProjectRoot()
        val verifierFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/ProofTokenVerifier.kt")
        if (verifierFile.exists()) {
            val content = verifierFile.readText()
            val publicSignPattern = Regex("""(?m)^\s*fun\s+sign\s*\(""")
            val publicExplicitPattern = Regex("""(?m)^\s*public\s+fun\s+sign\s*\(""")
            assertFalse(
                "ProofTokenVerifier.sign MUST be internal or private, not public",
                publicSignPattern.containsMatchIn(content) || publicExplicitPattern.containsMatchIn(content)
            )
        }

        val registryFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/AuthHandleRegistry.kt")
        if (registryFile.exists()) {
            val content = registryFile.readText()
            val publicRegisterPattern = Regex("""(?m)^\s*fun\s+register\s*\(""")
            val publicClearPattern = Regex("""(?m)^\s*fun\s+clearForTesting\s*\(""")
            assertFalse(
                "AuthHandleRegistry.register MUST be internal, not public",
                publicRegisterPattern.containsMatchIn(content)
            )
            assertFalse(
                "AuthHandleRegistry.clearForTesting MUST be internal, not public",
                publicClearPattern.containsMatchIn(content)
            )
        }
    }

    @Test
    fun test_DeletionAuthorizationGrant_and_registries_are_internal() {
        val rootDir = findProjectRoot()
        val grantFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/DeletionAuthorizationGrant.kt")
        if (grantFile.exists()) {
            val content = grantFile.readText()
            assertTrue(
                "DeletionAuthorizationGrant MUST have internal constructor",
                content.contains("class DeletionAuthorizationGrant internal constructor")
            )
            assertFalse(
                "DeletionAuthorizationGrant MUST NOT be a data class (to prevent public copy() bypass)",
                content.contains("data class DeletionAuthorizationGrant")
            )
        }

        val grantRegistryFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/DeletionGrantRegistry.kt")
        if (grantRegistryFile.exists()) {
            val content = grantRegistryFile.readText()
            val publicRegisterPattern = Regex("""(?m)^\s*(public\s+)?fun\s+register\s*\(""")
            val publicClearPattern = Regex("""(?m)^\s*(public\s+)?fun\s+clearForTesting\s*\(""")
            assertFalse(
                "DeletionGrantRegistry.register MUST be internal, not public",
                publicRegisterPattern.containsMatchIn(content)
            )
            assertFalse(
                "DeletionGrantRegistry.clearForTesting MUST be internal, not public",
                publicClearPattern.containsMatchIn(content)
            )
        }

        val grantVerifierFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/DeletionGrantVerifier.kt")
        if (grantVerifierFile.exists()) {
            val content = grantVerifierFile.readText()
            val publicSignPattern = Regex("""(?m)^\s*(public\s+)?fun\s+sign\s*\(""")
            assertFalse(
                "DeletionGrantVerifier.sign MUST be internal, not public",
                publicSignPattern.containsMatchIn(content)
            )
        }

        val grantServiceFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/DeletionAuthorizationService.kt")
        if (grantServiceFile.exists()) {
            val content = grantServiceFile.readText()
            val publicIssuePattern = Regex("""(?m)^\s*(public\s+)?fun\s+issueDeletionGrant\s*\(""")
            val publicUnauthPattern = Regex("""(?m)^\s*(public\s+)?fun\s+issueUnauthenticatedGrant\s*\(""")
            assertFalse(
                "DeletionAuthorizationService.issueDeletionGrant MUST be internal, not public",
                publicIssuePattern.containsMatchIn(content)
            )
            assertFalse(
                "DeletionAuthorizationService.issueUnauthenticatedGrant MUST be internal, not public",
                publicUnauthPattern.containsMatchIn(content)
            )
        }
    }

    @Test
    fun test_apple_platform_authenticators_ban_external_caller_assertion_methods() {
        val rootDir = findProjectRoot()
        val appleDirs = listOf(
            File(rootDir, "coreKmp/src/iosMain"),
            File(rootDir, "coreKmp/src/watchosMain")
        )

        val bannedSymbols = listOf(
            "issueHandleFromCallback",
            "isPolicyEvaluated",
            "fun issueHandle("
        )

        val violations = mutableListOf<String>()

        for (dir in appleDirs) {
            if (!dir.exists()) continue
            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "kt") {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        for (banned in bannedSymbols) {
                            if (line.contains(banned)) {
                                violations.add(
                                    "Banned caller-assertion symbol '$banned' found in ${file.relativeTo(rootDir).path}:${index + 1}\n   --> $trimmed"
                                )
                            }
                        }
                        // Check for caller success boolean in function signatures
                        if (trimmed.contains("fun ") && trimmed.contains("success: Boolean")) {
                            violations.add(
                                "Prohibited caller success boolean parameter found in ${file.relativeTo(rootDir).path}:${index + 1}\n   --> $trimmed"
                            )
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: Found banned caller-assertion methods in Apple sources:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun test_RecoveryGrant_and_registries_are_internal() {
        val rootDir = findProjectRoot()
        val grantFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/RecoveryGrant.kt")
        if (grantFile.exists()) {
            val content = grantFile.readText()
            assertTrue(
                "RecoveryGrant MUST have internal constructor",
                content.contains("class RecoveryGrant internal constructor")
            )
            assertFalse(
                "RecoveryGrant MUST NOT be a data class (to prevent public copy() bypass)",
                content.contains("data class RecoveryGrant")
            )
        }

        val grantRegistryFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/RecoveryGrantRegistry.kt")
        if (grantRegistryFile.exists()) {
            val content = grantRegistryFile.readText()
            val publicRegisterPattern = Regex("""(?m)^\s*(public\s+)?fun\s+register\s*\(""")
            val publicClearPattern = Regex("""(?m)^\s*(public\s+)?fun\s+clearForTesting\s*\(""")
            assertFalse(
                "RecoveryGrantRegistry.register MUST be internal, not public",
                publicRegisterPattern.containsMatchIn(content)
            )
            assertFalse(
                "RecoveryGrantRegistry.clearForTesting MUST be internal, not public",
                publicClearPattern.containsMatchIn(content)
            )
        }

        val grantVerifierFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/RecoveryGrantVerifier.kt")
        if (grantVerifierFile.exists()) {
            val content = grantVerifierFile.readText()
            val publicSignPattern = Regex("""(?m)^\s*(public\s+)?fun\s+sign\s*\(""")
            assertFalse(
                "RecoveryGrantVerifier.sign MUST be internal, not public",
                publicSignPattern.containsMatchIn(content)
            )
        }
    }

    @Test
    fun test_single_WalletManagementViewModel_and_Screen_in_wear_main() {
        val rootDir = findProjectRoot()
        val wearMainDir = File(rootDir, "wear/src/main")
        val vmFiles = mutableListOf<File>()
        val screenFiles = mutableListOf<File>()

        wearMainDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                if (file.name == "WalletManagementViewModel.kt") {
                    vmFiles.add(file)
                }
                if (file.name == "WalletManagementScreen.kt") {
                    screenFiles.add(file)
                }
            }
        }

        assertTrue(
            "Exactly 1 WalletManagementViewModel.kt must exist in wear/src/main, found: ${vmFiles.map { it.relativeTo(rootDir).path }}",
            vmFiles.size == 1
        )
        assertTrue(
            "Exactly 1 WalletManagementScreen.kt must exist in wear/src/main, found: ${screenFiles.map { it.relativeTo(rootDir).path }}",
            screenFiles.size == 1
        )

        val vmPath = vmFiles.first().relativeTo(rootDir).path.replace('\\', '/')
        val screenPath = screenFiles.first().relativeTo(rootDir).path.replace('\\', '/')
        assertEquals(
            "wear/src/main/java/com/cbstudio/wearwallet/presentation/wallet/screens/settings/WalletManagementViewModel.kt",
            vmPath
        )
        assertEquals(
            "wear/src/main/java/com/cbstudio/wearwallet/presentation/wallet/screens/settings/WalletManagementScreen.kt",
            screenPath
        )
    }

    @Test
    fun test_apple_platform_authenticators_enforce_coroutine_cancellation_and_isActive() {
        val rootDir = findProjectRoot()
        val appleAuthFiles = listOf(
            File(rootDir, "coreKmp/src/iosMain/kotlin/com/cbstudio/wearwallet/core/security/IOSPlatformAuthenticator.kt"),
            File(rootDir, "coreKmp/src/watchosMain/kotlin/com/cbstudio/wearwallet/core/security/WatchOSPlatformAuthenticator.kt")
        )

        for (file in appleAuthFiles) {
            assertTrue("Authenticator file must exist: ${file.path}", file.exists())
            val content = file.readText()

            assertTrue(
                "Authenticator in ${file.name} MUST register invokeOnCancellation",
                content.contains("continuation.invokeOnCancellation")
            )
            assertTrue(
                "Authenticator in ${file.name} MUST call laContext.invalidate() on cancellation",
                content.contains("laContext.invalidate()")
            )
            assertTrue(
                "Authenticator in ${file.name} MUST check continuation.isActive before resuming",
                content.contains("continuation.isActive")
            )
            assertTrue(
                "Authenticator in ${file.name} MUST have evaluatePolicyAndIssueHandle as public entrypoint",
                content.contains("suspend fun evaluatePolicyAndIssueHandle")
            )
        }
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
