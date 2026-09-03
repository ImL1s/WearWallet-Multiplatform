package com.cbstudio.wearwallet.core.arch

import com.cbstudio.wearwallet.core.security.BackendAttestation
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.RuntimeCapabilityContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

/**
 * Architectural test ensuring CapabilityGate adheres strictly to:
 * 1. 12-tuple explicit request requirement (P1-1).
 * 2. Non-forgeable BackendAttestation and RuntimeCapabilityContext requirement (P1-2).
 * 3. Zero hardcoded boolean evidence in production source sets.
 * 4. CapabilityGate.isChainSupported does not instantiate CapabilityRequest.
 * 5. CapabilityRequest constructor is internal and cannot be constructed directly in production.
 */
class CapabilityGateArchitectureTest {

    private val productionSourceDirectories = listOf(
        "coreKmp/src/commonMain",
        "coreKmp/src/androidMain",
        "coreKmp/src/iosMain",
        "coreKmp/src/watchosMain",
        "wear/src/main"
    )

    private val sourceExtensions = listOf("kt", "java")

    // =========================================================================
    // 1. Interface & Reflection Contract Tests
    // =========================================================================

    @Test
    fun test_CapabilityGate_interface_has_only_single_verifyCapability_method() {
        val verifyMethods = CapabilityGate::class.memberFunctions.filter { it.name == "verifyCapability" }
        assertEquals(
            "CapabilityGate must have exactly 1 verifyCapability method",
            1,
            verifyMethods.size
        )

        val verifyMethod = verifyMethods.first()
        val params = verifyMethod.valueParameters
        assertEquals("verifyCapability must take exactly 1 value parameter", 1, params.size)
        assertEquals(
            "verifyCapability parameter must be CapabilityRequest",
            CapabilityRequest::class,
            params.first().type.classifier
        )
    }

    @Test
    fun test_CapabilityRequest_constructor_is_internal_and_has_no_defaults() {
        val primaryConstructor = CapabilityRequest::class.primaryConstructor
        assertTrue("CapabilityRequest must have a primary constructor", primaryConstructor != null)

        assertEquals(
            "CapabilityRequest primary constructor MUST be INTERNAL to prevent arbitrary external construction",
            KVisibility.INTERNAL,
            primaryConstructor!!.visibility
        )

        val paramsWithDefaults = primaryConstructor.valueParameters.filter { it.isOptional }
        assertTrue(
            "CapabilityRequest constructor must not have any default parameters (all 12 fields must be explicit at compile time). Found optional params: ${paramsWithDefaults.map { it.name }}",
            paramsWithDefaults.isEmpty()
        )
        assertEquals("CapabilityRequest must have exactly 12 fields", 12, primaryConstructor.valueParameters.size)
    }

    @Test
    fun test_BackendAttestation_constructor_is_internal_and_has_no_defaults() {
        val primaryConstructor = BackendAttestation::class.primaryConstructor
        assertTrue("BackendAttestation must have a primary constructor", primaryConstructor != null)

        assertEquals(
            "BackendAttestation primary constructor MUST be INTERNAL to prevent external tampering",
            KVisibility.INTERNAL,
            primaryConstructor!!.visibility
        )

        val paramsWithDefaults = primaryConstructor.valueParameters.filter { it.isOptional }
        assertTrue(
            "BackendAttestation constructor must not have default parameters: ${paramsWithDefaults.map { it.name }}",
            paramsWithDefaults.isEmpty()
        )
    }

    @Test
    fun test_RuntimeCapabilityContext_has_no_defaults() {
        val primaryConstructor = RuntimeCapabilityContext::class.primaryConstructor
        assertTrue("RuntimeCapabilityContext must have a primary constructor", primaryConstructor != null)

        val paramsWithDefaults = primaryConstructor!!.valueParameters.filter { it.isOptional }
        assertTrue(
            "RuntimeCapabilityContext constructor must not have default parameters: ${paramsWithDefaults.map { it.name }}",
            paramsWithDefaults.isEmpty()
        )
    }

    // =========================================================================
    // 2. Static Source Scan: Zero Hardcoded Boolean Evidence
    // =========================================================================

    @Test
    fun test_zero_occurrences_of_hardcoded_backendAvailable_or_smokeVectorVerified_in_production() {
        val rootDir = findProjectRoot()
        val violations = mutableListOf<String>()

        val hardcodedBooleanPattern = Regex("""\b(?:backendAvailable|smokeVectorVerified)\s*=\s*(?:true|false)\b""")

        for (relDir in productionSourceDirectories) {
            val dir = File(rootDir, relDir)
            if (!dir.exists()) continue

            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() in sourceExtensions) {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        // Ignore comments
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }

                        if (hardcodedBooleanPattern.containsMatchIn(trimmed)) {
                            violations.add(
                                "Hardcoded boolean evidence found in ${file.relativeTo(rootDir).path}:${index + 1} -> $trimmed"
                            )
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: Found hardcoded backendAvailable or smokeVectorVerified in production sources:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    // =========================================================================
    // 3. Static Source Scan: CapabilityGate.isChainSupported Has No CapabilityRequest
    // =========================================================================

    @Test
    fun test_CapabilityGate_isChainSupported_does_not_instantiate_CapabilityRequest() {
        val rootDir = findProjectRoot()
        val gateFile = File(rootDir, "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt")
        assertTrue("CapabilityGate.kt must exist", gateFile.exists())

        val content = gateFile.readText()
        val isChainSupportedInterfaceBlock = extractInterfaceIsChainSupportedBlock(content)

        assertFalse(
            "CapabilityGate interface isChainSupported MUST NOT instantiate CapabilityRequest (must be abstract or free of CapabilityRequest): $isChainSupportedInterfaceBlock",
            isChainSupportedInterfaceBlock.contains("CapabilityRequest(") || isChainSupportedInterfaceBlock.contains("CapabilityRequest.fromRuntime(")
        )
    }

    // =========================================================================
    // 4. Static Source Scan: No Direct CapabilityRequest Invocations in Production Callers
    // =========================================================================

    @Test
    fun test_zero_direct_CapabilityRequest_constructor_invocations_outside_CapabilityRequest_file() {
        val rootDir = findProjectRoot()
        val violations = mutableListOf<String>()

        val constructorPattern = Regex("""\bCapabilityRequest\s*\(""")

        for (relDir in productionSourceDirectories) {
            val dir = File(rootDir, relDir)
            if (!dir.exists()) continue

            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() in sourceExtensions) {
                    // CapabilityRequest.kt itself is allowed to instantiate CapabilityRequest in fromRuntime / factory
                    if (file.name == "CapabilityRequest.kt") {
                        return@forEach
                    }

                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                            return@forEachIndexed
                        }

                        if (constructorPattern.containsMatchIn(trimmed)) {
                            violations.add(
                                "Direct CapabilityRequest constructor invocation in production file ${file.relativeTo(rootDir).path}:${index + 1} -> $trimmed"
                            )
                        }
                    }
                }
            }
        }

        assertTrue(
            "Architecture Violation: Direct CapabilityRequest constructor invocations found in production sources (must use CapabilityRequest.fromRuntime):\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun extractInterfaceIsChainSupportedBlock(source: String): String {
        val interfaceIdx = source.indexOf("interface CapabilityGate")
        if (interfaceIdx == -1) return ""
        val afterInterface = source.substring(interfaceIdx)
        val endInterfaceIdx = afterInterface.indexOf("\nclass ")
        val interfaceContent = if (endInterfaceIdx != -1) afterInterface.substring(0, endInterfaceIdx) else afterInterface

        val funcIdx = interfaceContent.indexOf("fun isChainSupported")
        if (funcIdx == -1) return ""
        val afterFunc = interfaceContent.substring(funcIdx)
        val nextFuncIdx = afterFunc.indexOf("\n    fun ", 1)
        return if (nextFuncIdx != -1) afterFunc.substring(0, nextFuncIdx) else afterFunc
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
