package com.cbstudio.wearwallet.core.arch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture Test: Repository No-Op Fake Success Elimination
 *
 * Enforces:
 * 1. Production repository implementations MUST NOT contain expression-body fake successes:
 *    - `= Result.Success(Unit)`
 *    - `= Result.Success(false)`
 *    - `= Result.Success(0)`
 *    - `= Result.Success(emptyList())`
 *    - `= Result.Success(emptyMap())`
 * 2. All methods in PriceAlertRepositoryImpl must either interact with database queries
 *    or explicitly return Result.Failure(TypedUnsupportedOperationException).
 */
class RepositoryNoOpFakeSuccessArchitectureTest {

    private val productionRepositoryDirectories = listOf(
        "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/data/repository",
        "coreKmp/src/androidMain/kotlin/com/cbstudio/wearwallet/core/data/repository"
    )

    private val bannedFakeSuccessPatterns = listOf(
        Regex("""=\s*Result\.Success\s*\(\s*Unit\s*\)"""),
        Regex("""=\s*Result\.Success\s*\(\s*false\s*\)"""),
        Regex("""=\s*Result\.Success\s*\(\s*0\s*\)"""),
        Regex("""=\s*Result\.Success\s*\(\s*emptyList\s*\(\s*\)\s*\)"""),
        Regex("""=\s*Result\.Success\s*\(\s*emptyMap\s*\(\s*\)\s*\)"""),
        Regex("""=\s*Result\.Success\s*\(\s*PriceAlertStatistics\s*\(\s*0\s*,""")
    )

    @Test
    fun test_no_expression_body_fake_success_in_production_repositories() {
        val rootDir = findProjectRoot()
        val violations = mutableListOf<String>()

        for (relDir in productionRepositoryDirectories) {
            val dir = File(rootDir, relDir)
            if (!dir.exists()) continue

            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "kt") {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }
                        for (pattern in bannedFakeSuccessPatterns) {
                            if (pattern.containsMatchIn(line)) {
                                violations.add(
                                    "Fake success expression detected in ${file.relativeTo(rootDir).path}:${index + 1}\n   --> $trimmed"
                                )
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: Found expression-body fake success in production repositories:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun test_price_alert_repository_impl_wires_all_methods_or_fails_typed() {
        val rootDir = findProjectRoot()
        val file = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/data/repository/PriceAlertRepository.kt")
        assertTrue("PriceAlertRepository.kt must exist", file.exists())

        val content = file.readText()

        // 1. Must use priceAlertQueries for CRUD and queries
        assertTrue("Must use priceAlertQueries.existsBySameConfig", content.contains("priceAlertQueries.existsBySameConfig"))
        assertTrue("Must use priceAlertQueries.insert", content.contains("priceAlertQueries.insert"))
        assertTrue("Must use priceAlertQueries.selectById", content.contains("priceAlertQueries.selectById"))
        assertTrue("Must use priceAlertQueries.selectAll", content.contains("priceAlertQueries.selectAll"))
        assertTrue("Must use priceAlertQueries.update", content.contains("priceAlertQueries.update"))
        assertTrue("Must use priceAlertQueries.deleteById", content.contains("priceAlertQueries.deleteById"))
        assertTrue("Must use priceAlertQueries.selectEnabled", content.contains("priceAlertQueries.selectEnabled"))
        assertTrue("Must use priceAlertQueries.selectNotTriggered", content.contains("priceAlertQueries.selectNotTriggered"))
        assertTrue("Must use priceAlertQueries.selectByAssetSymbol", content.contains("priceAlertQueries.selectByAssetSymbol"))
        assertTrue("Must use priceAlertQueries.selectByChainType", content.contains("priceAlertQueries.selectByChainType"))
        assertTrue("Must use priceAlertQueries.selectByAlertType", content.contains("priceAlertQueries.selectByAlertType"))
        assertTrue("Must use priceAlertQueries.searchAlerts", content.contains("priceAlertQueries.searchAlerts"))
        assertTrue("Must use priceAlertQueries.selectForMonitoring", content.contains("priceAlertQueries.selectForMonitoring"))
        assertTrue("Must use priceAlertQueries.selectNearTrigger", content.contains("priceAlertQueries.selectNearTrigger"))
        assertTrue("Must use priceAlertQueries.updateCurrentPrice", content.contains("priceAlertQueries.updateCurrentPrice"))
        assertTrue("Must use priceAlertQueries.updateEnabledStatus", content.contains("priceAlertQueries.updateEnabledStatus"))
        assertTrue("Must use priceAlertQueries.triggerAlert", content.contains("priceAlertQueries.triggerAlert"))
        assertTrue("Must use priceAlertQueries.markNotificationSent", content.contains("priceAlertQueries.markNotificationSent"))
        assertTrue("Must use priceAlertQueries.resetTriggerStatus", content.contains("priceAlertQueries.resetTriggerStatus"))
        assertTrue("Must use priceAlertQueries.updateLastCheckedTime", content.contains("priceAlertQueries.updateLastCheckedTime"))
        assertTrue("Must use priceAlertQueries.deleteByAssetSymbol", content.contains("priceAlertQueries.deleteByAssetSymbol"))
        assertTrue("Must use priceAlertQueries.deleteTriggered", content.contains("priceAlertQueries.deleteTriggered"))
        assertTrue("Must use priceAlertQueries.deleteDisabled", content.contains("priceAlertQueries.deleteDisabled"))
        assertTrue("Must use priceAlertQueries.deleteAll", content.contains("priceAlertQueries.deleteAll"))
        assertTrue("Must use priceAlertQueries.countAll", content.contains("priceAlertQueries.countAll"))
        assertTrue("Must use priceAlertQueries.getChainStats", content.contains("priceAlertQueries.getChainStats"))
        assertTrue("Must use priceAlertQueries.getAssetStats", content.contains("priceAlertQueries.getAssetStats"))
        assertTrue("Must use priceAlertQueries.getTriggerHistory", content.contains("priceAlertQueries.getTriggerHistory"))

        // 2. Unsupported methods must explicitly use TypedUnsupportedOperationException
        assertTrue("Must use TypedUnsupportedOperationException for cleanupExpiredTriggers", content.contains("TypedUnsupportedOperationException(\"cleanupExpiredTriggers"))
        assertTrue("Must use TypedUnsupportedOperationException for resetStaleAlerts", content.contains("TypedUnsupportedOperationException(\"resetStaleAlerts"))
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
