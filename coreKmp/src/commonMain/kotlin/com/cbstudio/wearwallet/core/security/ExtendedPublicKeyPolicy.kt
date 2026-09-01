package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.Base58
import io.github.iml1s.crypto.Secp256k1Pure

/**
 * ExtendedPublicKeyPolicy enforces strict validation on Extended Public Keys (xpub/tpub),
 * including master fingerprint validation, derivation path constraints, network context,
 * depth validation, Base58Check 78-byte payload decoding, and 8 structural checkpoints under Requirement R5.
 */
data class ExtendedPublicKeyPolicy(
    val expectedMasterFingerprint: String? = null,
    val expectedDerivationPath: String? = null,
    val requireTestnet: Boolean = false,
    val enforceMasterFingerprintFormat: Boolean = true,
    val allowEmptyMasterFingerprint: Boolean = false
) {
    companion object {
        private val MASTER_FINGERPRINT_REGEX = Regex("^[0-9a-fA-F]{8}$")

        val STRICT_DEFAULT = ExtendedPublicKeyPolicy(
            enforceMasterFingerprintFormat = true,
            allowEmptyMasterFingerprint = false
        )
    }

    /**
     * Validates the provided master fingerprint and xpub parameters against policy constraints.
     * Enforces Base58Check 78-byte decoding and 8 structural validation checkpoints:
     * 1. Version bytes (0x0488B21E for mainnet, 0x043587CF for testnet)
     * 2. Depth byte (1 byte at index 4)
     * 3. Parent fingerprint (4 bytes at index 5..8)
     * 4. Child number (4 bytes at index 9..12)
     * 5. Account index check
     * 6. Compressed public key prefix (33 bytes at index 45..77, prefix 0x02 or 0x03, SECP256k1 validity)
     * 7. Derivation path structure check (disallow hardened child paths from xpub)
     * 8. Master fingerprint binding to derivation call
     *
     * Throws [InvalidExtendedPublicKeyPolicyException] on validation failure.
     */
    fun validate(
        masterFingerprint: String,
        xpub: String,
        derivationPath: String,
        isTestnet: Boolean = requireTestnet
    ) {
        val cleanFingerprint = masterFingerprint.trim()

        // Checkpoint 8: Master fingerprint binding
        if (cleanFingerprint.isEmpty()) {
            if (!allowEmptyMasterFingerprint) {
                throw InvalidExtendedPublicKeyPolicyException("Master fingerprint cannot be empty under ExtendedPublicKeyPolicy")
            }
        } else if (enforceMasterFingerprintFormat) {
            if (!MASTER_FINGERPRINT_REGEX.matches(cleanFingerprint)) {
                throw InvalidExtendedPublicKeyPolicyException(
                    "Invalid master fingerprint format: '$cleanFingerprint'. Must be an 8-character hex string (4 bytes)."
                )
            }
        }

        if (expectedMasterFingerprint != null && cleanFingerprint.isNotEmpty()) {
            if (!cleanFingerprint.equals(expectedMasterFingerprint.trim(), ignoreCase = true)) {
                throw InvalidExtendedPublicKeyPolicyException(
                    "Master fingerprint mismatch: expected '$expectedMasterFingerprint', got '$cleanFingerprint'"
                )
            }
        }

        val cleanPath = derivationPath.trim()
        if (expectedDerivationPath != null && cleanPath.isNotEmpty()) {
            if (!cleanPath.equals(expectedDerivationPath.trim(), ignoreCase = true)) {
                throw InvalidExtendedPublicKeyPolicyException(
                    "Derivation path mismatch: expected '$expectedDerivationPath', got '$cleanPath'"
                )
            }
        }

        val cleanXpub = xpub.trim()
        if (cleanXpub.isEmpty()) {
            throw InvalidExtendedPublicKeyPolicyException("xpub string cannot be empty")
        }

        if (isTestnet) {
            if (cleanXpub.startsWith("xpub", ignoreCase = true)) {
                throw InvalidExtendedPublicKeyPolicyException("Mainnet xpub (xpub) is prohibited on testnet context")
            }
        } else {
            if (cleanXpub.startsWith("tpub", ignoreCase = true)) {
                throw InvalidExtendedPublicKeyPolicyException("Testnet xpub (tpub) is prohibited on mainnet context")
            }
        }

        // Checkpoint 7: Derivation path structure check (disallow hardened child paths from xpub)
        if (cleanPath.isNotEmpty()) {
            val isFullMPath = cleanPath.startsWith("m/", ignoreCase = true) || cleanPath == "m"
            val pathSegments = cleanPath.split("/").filter { it.isNotEmpty() && it != "m" && it != "M" }
            if (isFullMPath) {
                for (segment in pathSegments) {
                    val rawVal = segment.trimEnd('\'', 'h', 'H')
                    if (rawVal.isEmpty() || rawVal.any { !it.isDigit() }) {
                        throw InvalidExtendedPublicKeyPolicyException("Invalid derivation path segment '$segment' in path '$cleanPath'")
                    }
                }
            } else {
                for (segment in pathSegments) {
                    if (segment.contains('\'') || segment.endsWith("h", ignoreCase = true)) {
                        throw InvalidExtendedPublicKeyPolicyException("Hardened child derivation path segment '$segment' is prohibited from Extended Public Keys (xpub)")
                    }
                    val rawVal = segment.trimEnd('\'', 'h', 'H')
                    if (rawVal.isEmpty() || rawVal.any { !it.isDigit() }) {
                        throw InvalidExtendedPublicKeyPolicyException("Invalid derivation path segment '$segment' in path '$cleanPath'")
                    }
                }
            }
        }

        // Base58Check 78-byte payload decoding and checksum verification
        val rawBytes = Base58.decode(cleanXpub)
            ?: throw InvalidExtendedPublicKeyPolicyException("Failed to decode Base58 string for xpub: '$cleanXpub'")

        if (rawBytes.size != 82) {
            throw InvalidExtendedPublicKeyPolicyException("Invalid xpub payload length: expected 82 bytes (78 payload + 4 checksum), got ${rawBytes.size}")
        }

        val payload = rawBytes.copyOfRange(0, 78)
        val checksum = rawBytes.copyOfRange(78, 82)

        val calculatedChecksum = platformSha256(platformSha256(payload)).copyOfRange(0, 4)
        if (!checksum.contentEquals(calculatedChecksum)) {
            throw InvalidExtendedPublicKeyPolicyException("Base58Check checksum validation failed for xpub")
        }

        // Checkpoint 1: Version bytes (0x0488B21E for mainnet, 0x043587CF for testnet)
        val versionInt = ((payload[0].toInt() and 0xFF) shl 24) or
                ((payload[1].toInt() and 0xFF) shl 16) or
                ((payload[2].toInt() and 0xFF) shl 8) or
                (payload[3].toInt() and 0xFF)

        val expectedVersion = if (isTestnet) 0x043587CF else 0x0488B21E
        if (versionInt != expectedVersion) {
            val expectedStr = if (isTestnet) "0x043587CF (tpub)" else "0x0488B21E (xpub)"
            val actualStr = "0x" + versionInt.toUInt().toString(16).uppercase().padStart(8, '0')
            throw InvalidExtendedPublicKeyPolicyException("Invalid xpub version bytes: expected $expectedStr, got $actualStr")
        }

        // Checkpoint 2: Depth byte (1 byte at index 4)
        val depth = payload[4].toInt() and 0xFF
        if (depth < 0 || depth > 255) {
            throw InvalidExtendedPublicKeyPolicyException("Invalid xpub depth byte: $depth")
        }

        // Checkpoint 3: Parent fingerprint (4 bytes at index 5..8)
        val parentFingerprintBytes = payload.copyOfRange(5, 9)
        val parentFingerprintHex = parentFingerprintBytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

        if (depth == 0) {
            if (parentFingerprintHex != "00000000") {
                throw InvalidExtendedPublicKeyPolicyException("Master xpub (depth 0) must have parent fingerprint 00000000, got '$parentFingerprintHex'")
            }
        } else if (depth == 1 && cleanFingerprint.isNotEmpty()) {
            if (!parentFingerprintHex.equals(cleanFingerprint, ignoreCase = true)) {
                throw InvalidExtendedPublicKeyPolicyException("xpub parent fingerprint mismatch: expected '$cleanFingerprint' for depth 1, got '$parentFingerprintHex'")
            }
        }

        // Checkpoint 4: Child number (4 bytes at index 9..12)
        val childNumLong = ((payload[9].toLong() and 0xFF) shl 24) or
                ((payload[10].toLong() and 0xFF) shl 16) or
                ((payload[11].toLong() and 0xFF) shl 8) or
                (payload[12].toLong() and 0xFF)

        if (depth == 0) {
            if (childNumLong != 0L) {
                throw InvalidExtendedPublicKeyPolicyException("Master xpub (depth 0) must have child number 0, got $childNumLong")
            }
        }

        // Checkpoint 5: Account index check
        if (cleanPath.isNotEmpty() && cleanPath.startsWith("m/", ignoreCase = true)) {
            val segments = cleanPath.split("/").filter { it.isNotEmpty() && it != "m" && it != "M" }
            if (segments.size >= 3) {
                val accountSegment = segments[2]
                val isHardenedAccount = accountSegment.endsWith("'") || accountSegment.endsWith("h", ignoreCase = true)
                val accountVal = accountSegment.trimEnd('\'', 'h', 'H').toLongOrNull()
                if (accountVal != null && depth == 3) {
                    val expectedChildNum = if (isHardenedAccount) 0x80000000L + accountVal else accountVal
                    if (childNumLong != expectedChildNum) {
                        throw InvalidExtendedPublicKeyPolicyException("xpub child number $childNumLong does not match derivation path account index $expectedChildNum")
                    }
                }
            }
        }

        // Checkpoint 6: Compressed public key prefix (33 bytes at index 45..77, prefix 0x02 or 0x03, SECP256k1 validity)
        val pubKeyBytes = payload.copyOfRange(45, 78)
        val pubKeyPrefix = pubKeyBytes[0].toInt() and 0xFF
        if (pubKeyPrefix != 0x02 && pubKeyPrefix != 0x03) {
            throw InvalidExtendedPublicKeyPolicyException("Invalid compressed public key prefix: 0x${pubKeyPrefix.toString(16).padStart(2, '0')}. Expected 0x02 or 0x03")
        }

        try {
            Secp256k1Pure.decodePublicKey(pubKeyBytes)
        } catch (e: Exception) {
            throw InvalidExtendedPublicKeyPolicyException("Invalid secp256k1 public key point in xpub: ${e.message}")
        }
    }
}

class InvalidExtendedPublicKeyPolicyException(message: String) : IllegalArgumentException(message)
