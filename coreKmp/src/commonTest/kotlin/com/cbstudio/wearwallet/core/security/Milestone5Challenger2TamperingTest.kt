package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Milestone 5 Challenger 2 Adversarial Stress Test Suite:
 * 1. CapabilityGate Adversarial Stress Testing (Network Spoofing & isTestnetSupported Bypassing)
 * 2. VersionedEncryptedEnvelope Adversarial Stress Testing (Bit-flipping, AAD Tampering, Tag Truncation, Magic Header Corruption, Unversioned Injection)
 */
class Milestone5Challenger2TamperingTest {

    private val releaseGate = ReleaseProductionCapabilityGate(
        allowEvmMainnetSend = false,
        allowBroadcast = false
    )

    private val testPassword = "AdversarialChallengerPassword#2026!@#".encodeToByteArray()
    private val testPayload = "0xdeadbeefcafebabe0123456789abcdef0123456789abcdef0123456789abcdef".encodeToByteArray()
    private val testKeyId = "adversarial-target-key-007"
    private val testAad = "context:account_id=vault-007,chain_id=1,op=SIGN".encodeToByteArray()

    // =========================================================================
    // SECTION 1: CapabilityGate Adversarial Stress Tests
    // =========================================================================

    @Test
    fun test_adversarial_capability_gate_mainnet_software_sign_strictly_denied_for_all_supported_chains() {
        // Attempt to bypass mainnet software signing restriction by exploiting isTestnetSupported = true
        val chainsWithTestnet = listOf(MultiChainType.ETHEREUM, MultiChainType.POLYGON)
        for (chain in chainsWithTestnet) {
            assertTrue(chain.isTestnetSupported, "Chain $chain must have isTestnetSupported=true")

            // Attack Vector 1: Pass explicit Network.MAINNET with software local signer
            val decision1 = releaseGate.checkCapability(
                CapabilityRequest(
                    operation = Operation.SOFTWARE_SIGN,
                    chain = chain,
                    network = Network.MAINNET,
                    platform = Platform.WEAR_OS,
                    buildType = BuildType.RELEASE,
                    envelopeType = EvmEnvelope.LEGACY,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                    walletType = WalletType.SOFTWARE_PRIVATE_KEY,
                    backendIdentity = BackendIdentity.PRODUCTION_V1,
                    backendAvailable = true,
                    backendVersion = "1.0.0",
                    smokeVectorVerified = true
                )
            )
            assertTrue(decision1 is CapabilityDecision.Denied, "SOFTWARE_SIGN on $chain MAINNET must be strictly DENIED")

            // Attack Vector 2: Pass CREATE_UNSIGNED_TX on MAINNET
            val decision2 = releaseGate.checkCapability(
                CapabilityRequest(
                    operation = Operation.CREATE_UNSIGNED_TX,
                    chain = chain,
                    network = Network.MAINNET,
                    platform = Platform.WEAR_OS,
                    buildType = BuildType.RELEASE,
                    envelopeType = EvmEnvelope.LEGACY,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                    walletType = WalletType.SOFTWARE_PRIVATE_KEY,
                    backendIdentity = BackendIdentity.PRODUCTION_V1,
                    backendAvailable = true,
                    backendVersion = "1.0.0",
                    smokeVectorVerified = true
                )
            )
            assertTrue(decision2 is CapabilityDecision.Denied, "CREATE_UNSIGNED_TX on $chain MAINNET must be strictly DENIED")

            // Attack Vector 3: Pass CREATE_WALLET on MAINNET
            val decision3 = releaseGate.checkCapability(
                CapabilityRequest(
                    operation = Operation.CREATE_WALLET,
                    chain = chain,
                    network = Network.MAINNET,
                    platform = Platform.WEAR_OS,
                    buildType = BuildType.RELEASE,
                    envelopeType = EvmEnvelope.LEGACY,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                    walletType = WalletType.SOFTWARE_MNEMONIC,
                    backendIdentity = BackendIdentity.PRODUCTION_V1,
                    backendAvailable = true,
                    backendVersion = "1.0.0",
                    smokeVectorVerified = true
                )
            )
            assertTrue(decision3 is CapabilityDecision.Denied, "CREATE_WALLET on $chain MAINNET must be strictly DENIED")

            // Attack Vector 4: verifyCapability helper with MAINNET
            val verified = releaseGate.verifyCapability(
                CapabilityRequest(
                    operation = Operation.SOFTWARE_SIGN,
                    chain = chain,
                    network = Network.MAINNET,
                    platform = Platform.WEAR_OS,
                    buildType = BuildType.RELEASE,
                    envelopeType = EvmEnvelope.LEGACY,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                    walletType = WalletType.SOFTWARE_MNEMONIC,
                    backendIdentity = BackendIdentity.PRODUCTION_V1,
                    backendAvailable = true,
                    backendVersion = "1.0.0",
                    smokeVectorVerified = true
                )
            )
            assertFalse(verified, "verifyCapability on $chain MAINNET must return false")
        }

        // Test other EVM chains on MAINNET
        val otherEvmChains = listOf(MultiChainType.BSC, MultiChainType.ARBITRUM, MultiChainType.OPTIMISM, MultiChainType.BASE)
        for (chain in otherEvmChains) {
            val verified = releaseGate.verifyCapability(
                CapabilityRequest(
                    operation = Operation.SOFTWARE_SIGN,
                    chain = chain,
                    network = Network.MAINNET,
                    platform = Platform.WEAR_OS,
                    buildType = BuildType.RELEASE,
                    envelopeType = EvmEnvelope.LEGACY,
                    signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
                    walletType = WalletType.SOFTWARE_MNEMONIC,
                    backendIdentity = BackendIdentity.PRODUCTION_V1,
                    backendAvailable = true,
                    backendVersion = "1.0.0",
                    smokeVectorVerified = true
                )
            )
            assertFalse(verified, "SOFTWARE_SIGN on $chain MAINNET must be strictly DENIED")
        }
    }

    @Test
    fun test_adversarial_capability_gate_parameter_spoofing_and_unsupported_enums() {
        // Attack Vector: Unsupported / unknown signer implementation
        val unsupportedSignerReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.ETHEREUM,
            network = Network.TESTNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.UNSUPPORTED,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        assertFalse(releaseGate.verifyCapability(unsupportedSignerReq), "SignerImplementation.UNSUPPORTED must be strictly DENIED")

        // Attack Vector: Unknown or unsupported enum parameters
        val unknownNetworkReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.ETHEREUM,
            network = Network.UNKNOWN,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        assertTrue(releaseGate.checkCapability(unknownNetworkReq) is CapabilityDecision.Denied)

        val unknownPlatformReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.ETHEREUM,
            network = Network.TESTNET,
            platform = Platform.UNKNOWN,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        assertTrue(releaseGate.checkCapability(unknownPlatformReq) is CapabilityDecision.Denied)

        val unknownBuildReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.ETHEREUM,
            network = Network.TESTNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.UNKNOWN,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        assertTrue(releaseGate.checkCapability(unknownBuildReq) is CapabilityDecision.Denied)

        val unsupportedBackendReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.ETHEREUM,
            network = Network.TESTNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.SOFTWARE_PRIVATE_KEY,
            backendIdentity = BackendIdentity.UNSUPPORTED,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        assertTrue(releaseGate.checkCapability(unsupportedBackendReq) is CapabilityDecision.Denied)

        val unsupportedWalletReq = CapabilityRequest(
            operation = Operation.SOFTWARE_SIGN,
            chain = MultiChainType.ETHEREUM,
            network = Network.TESTNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.UNSUPPORTED,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        assertTrue(releaseGate.checkCapability(unsupportedWalletReq) is CapabilityDecision.Denied)

        // Attack Vector: Non-allowlisted chains (e.g., SOLANA, BITCOIN, CARDANO)
        val nonAllowlisted = listOf(MultiChainType.SOLANA, MultiChainType.BITCOIN, MultiChainType.CARDANO, MultiChainType.MONERO)
        for (chain in nonAllowlisted) {
            val nonAllowReq = CapabilityRequest(
                operation = Operation.IMPORT_XPUB,
                chain = chain,
                network = Network.MAINNET,
                platform = Platform.WEAR_OS,
                buildType = BuildType.RELEASE,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.KEYSTONE_HARDWARE,
                walletType = WalletType.KEYSTONE_XPUB,
                backendIdentity = BackendIdentity.PRODUCTION_V1,
                backendAvailable = true,
                backendVersion = "1.0.0",
                smokeVectorVerified = true
            )
            assertTrue(releaseGate.checkCapability(nonAllowReq) is CapabilityDecision.Denied, "Non-allowlisted chain $chain must be DENIED")
        }
    }

    @Test
    fun test_adversarial_capability_gate_operation_and_broadcast_tampering() {
        // Attack Vector: Software identity requesting HARDWARE_SIGN_REQUEST
        val hwWithSwSigner = CapabilityRequest(
            operation = Operation.HARDWARE_SIGN_REQUEST,
            chain = MultiChainType.ETHEREUM,
            network = Network.TESTNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL,
            walletType = WalletType.KEYSTONE_XPUB,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        val resHwWithSw = releaseGate.checkCapability(hwWithSwSigner)
        assertTrue(resHwWithSw is CapabilityDecision.Denied, "SOFTWARE_LOCAL claiming HARDWARE_SIGN_REQUEST must be DENIED")

        // Attack Vector: BROADCAST request when allowBroadcast = false
        val broadcastReq = CapabilityRequest(
            operation = Operation.BROADCAST,
            chain = MultiChainType.ETHEREUM,
            network = Network.TESTNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.KEYSTONE_HARDWARE,
            walletType = WalletType.KEYSTONE_XPUB,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        val resBroadcast = releaseGate.checkCapability(broadcastReq)
        assertTrue(resBroadcast is CapabilityDecision.Denied, "BROADCAST when allowBroadcast=false must be DENIED")
    }

    @Test
    fun test_adversarial_capability_gate_12_tuple_mutation_attacks() {
        val baseValidReq = CapabilityRequest(
            operation = Operation.HARDWARE_SIGN_REQUEST,
            chain = MultiChainType.ETHEREUM,
            network = Network.MAINNET,
            platform = Platform.WEAR_OS,
            buildType = BuildType.RELEASE,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.KEYSTONE_HARDWARE,
            walletType = WalletType.KEYSTONE_XPUB,
            backendIdentity = BackendIdentity.PRODUCTION_V1,
            backendAvailable = true,
            backendVersion = "1.0.0",
            smokeVectorVerified = true
        )
        assertTrue(releaseGate.checkCapability(baseValidReq) is CapabilityDecision.Allowed)

        // Mutate backendAvailable to false
        assertTrue(releaseGate.checkCapability(baseValidReq.copy(backendAvailable = false)) is CapabilityDecision.Denied)

        // Mutate smokeVectorVerified to false
        assertTrue(releaseGate.checkCapability(baseValidReq.copy(smokeVectorVerified = false)) is CapabilityDecision.Denied)

        // Mutate backendVersion to an unknown version
        assertTrue(releaseGate.checkCapability(baseValidReq.copy(backendVersion = "9.9.9")) is CapabilityDecision.Denied)

        // Mutate buildType to DEBUG under ReleaseProductionCapabilityGate
        assertTrue(releaseGate.checkCapability(baseValidReq.copy(buildType = BuildType.DEBUG)) is CapabilityDecision.Denied)

        // Mutate backendIdentity to STAGING
        assertTrue(releaseGate.checkCapability(baseValidReq.copy(backendIdentity = BackendIdentity.STAGING)) is CapabilityDecision.Denied)
    }

    // =========================================================================
    // SECTION 2: VersionedEncryptedEnvelope Adversarial Stress Tests
    // =========================================================================

    @Test
    fun test_adversarial_envelope_bit_flip_attacks_fail_closed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        // Attack 1: Flip bits in ciphertext at various offsets (start, middle, end)
        val ctOffsets = listOf(0, envelope.ciphertext.size / 2, envelope.ciphertext.size - 1)
        for (offset in ctOffsets) {
            val tamperedCt = envelope.ciphertext.copyOf()
            tamperedCt[offset] = (tamperedCt[offset].toInt() xor 0x01).toByte()
            val tamperedEnvelope = envelope.copy(ciphertext = tamperedCt)

            assertFailsWith<EnvelopeIntegrityException>("Flipped bit in ciphertext at offset $offset must fail closed") {
                tamperedEnvelope.decrypt(testPassword, testAad)
            }
        }

        // Attack 2: Flip bits in auth tag at various offsets
        val tagOffsets = listOf(0, envelope.authTag.size / 2, envelope.authTag.size - 1)
        for (offset in tagOffsets) {
            val tamperedTag = envelope.authTag.copyOf()
            tamperedTag[offset] = (tamperedTag[offset].toInt() xor 0x80).toByte()
            val tamperedEnvelope = envelope.copy(authTag = tamperedTag)

            assertFailsWith<EnvelopeIntegrityException>("Flipped bit in authTag at offset $offset must fail closed") {
                tamperedEnvelope.decrypt(testPassword, testAad)
            }
        }

        // Attack 3: Flip bit in nonce
        val tamperedNonce = envelope.nonce.copyOf()
        tamperedNonce[0] = (tamperedNonce[0].toInt() xor 0x02).toByte()
        val tamperedNonceEnvelope = envelope.copy(nonce = tamperedNonce)
        assertFailsWith<EnvelopeIntegrityException>("Flipped bit in nonce must fail closed") {
            tamperedNonceEnvelope.decrypt(testPassword, testAad)
        }

        // Attack 4: Flip bit in salt
        val tamperedSalt = envelope.salt.copyOf()
        tamperedSalt[0] = (tamperedSalt[0].toInt() xor 0x04).toByte()
        val tamperedSaltEnvelope = envelope.copy(salt = tamperedSalt)
        assertFailsWith<EnvelopeIntegrityException>("Flipped bit in salt must fail closed") {
            tamperedSaltEnvelope.decrypt(testPassword, testAad)
        }
    }

    @Test
    fun test_adversarial_envelope_aad_tampering_fails_closed() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        // Attack 1: Subtly tampered expected AAD (e.g. chain_id=2 instead of 1)
        val maliciousAad1 = "context:account_id=vault-007,chain_id=2,op=SIGN".encodeToByteArray()
        assertFailsWith<EnvelopeIntegrityException>("Subtly altered expected AAD must fail closed") {
            envelope.decrypt(testPassword, expectedAad = maliciousAad1)
        }

        // Attack 2: Empty expected AAD when envelope was encrypted with non-empty AAD
        assertFailsWith<EnvelopeIntegrityException>("Empty expected AAD against non-empty envelope AAD must fail closed") {
            envelope.decrypt(testPassword, expectedAad = byteArrayOf())
        }

        // Attack 3: Tampered internal envelope AAD
        val tamperedEnvelope = envelope.copy(aad = maliciousAad1)
        assertFailsWith<EnvelopeIntegrityException>("Tampered internal AAD must fail closed") {
            tamperedEnvelope.decrypt(testPassword, expectedAad = maliciousAad1)
        }

        // Attack 4: AAD bit flip in binary serialized stream
        val serialized = envelope.serialize()
        // AAD starts after MAGIC(4) + ver(1) + kdf(1+4+2) + salt(1+saltLen) + cipher(1+1+nonceLen) + keyId(2+keyIdLen) + aadLen(4)
        // Find AAD content inside serialized buffer and flip a byte
        val keyIdBytes = testKeyId.encodeToByteArray()
        val aadOffset = 4 + 1 + 1 + 4 + 2 + 1 + envelope.salt.size + 1 + 1 + envelope.nonce.size + 2 + keyIdBytes.size + 4
        serialized[aadOffset] = (serialized[aadOffset].toInt() xor 0xFF).toByte()

        val deserializedFromTamperedBinary = VersionedEncryptedEnvelope.deserialize(serialized)
        assertFailsWith<EnvelopeIntegrityException>("Deserialized envelope with tampered AAD must fail closed on decrypt") {
            deserializedFromTamperedBinary.decrypt(testPassword, testAad)
        }
    }

    @Test
    fun test_adversarial_envelope_magic_header_and_version_corruption() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )
        val validBinary = envelope.serialize()

        // Attack 1: Corrupt magic bytes
        val invalidMagics = listOf(
            byteArrayOf(0x00, 0x00, 0x00, 0x00),
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            "HTTP".encodeToByteArray(),
            "PK\u0003\u0004".encodeToByteArray(),
            byteArrayOf('W'.code.toByte(), 'W'.code.toByte(), 'E'.code.toByte(), 'X'.code.toByte())
        )

        for (badMagic in invalidMagics) {
            val corrupted = validBinary.copyOf()
            badMagic.copyInto(corrupted, 0, 0, 4)
            assertFailsWith<InvalidEnvelopeHeaderException>("Bad magic header must throw InvalidEnvelopeHeaderException") {
                VersionedEncryptedEnvelope.deserialize(corrupted)
            }
        }

        // Attack 2: Corrupt version byte (offset 4)
        val invalidVersions = listOf(0.toByte(), 2.toByte(), 3.toByte(), 99.toByte(), (-1).toByte())
        for (badVer in invalidVersions) {
            val corrupted = validBinary.copyOf()
            corrupted[4] = badVer
            assertFailsWith<UnsupportedEnvelopeVersionException>("Bad version $badVer must throw UnsupportedEnvelopeVersionException") {
                VersionedEncryptedEnvelope.deserialize(corrupted)
            }
        }
    }

    @Test
    fun test_adversarial_envelope_buffer_truncation_and_malformed_length_markers() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )
        val validBinary = envelope.serialize()

        // Attack 1: Arbitrary truncation lengths
        val truncationLengths = listOf(0, 1, 3, 4, 8, 16, 24, 32, 40, validBinary.size / 2, validBinary.size - 1)
        for (len in truncationLengths) {
            val truncated = validBinary.copyOfRange(0, len)
            assertFailsWith<EnvelopeException>("Truncated buffer of length $len must fail closed") {
                VersionedEncryptedEnvelope.deserialize(truncated)
            }
        }

        // Attack 2: Malformed length markers inside serialized stream
        // Corrupt salt length (offset 12 = 4 magic + 1 ver + 1 kdfId + 4 iter + 2 keyLen)
        val saltLenOffset = 4 + 1 + 1 + 4 + 2
        val badSaltLenBinary = validBinary.copyOf()
        badSaltLenBinary[saltLenOffset] = 4 // Salt length < MIN_SALT_LENGTH (16)
        assertFailsWith<EnvelopeCorruptedException>("Salt length < 16 must throw EnvelopeCorruptedException") {
            VersionedEncryptedEnvelope.deserialize(badSaltLenBinary)
        }

        // Corrupt nonce length
        val nonceLenOffset = saltLenOffset + 1 + envelope.salt.size + 1 // + cipherId (1)
        val badNonceLenBinary = validBinary.copyOf()
        badNonceLenBinary[nonceLenOffset] = 8 // Nonce length < MIN_NONCE_LENGTH (12)
        assertFailsWith<EnvelopeCorruptedException>("Nonce length < 12 must throw EnvelopeCorruptedException") {
            VersionedEncryptedEnvelope.deserialize(badNonceLenBinary)
        }

        // Corrupt auth tag length
        val keyIdBytes = testKeyId.encodeToByteArray()
        val tagLenOffset = nonceLenOffset + 1 + envelope.nonce.size + 2 + keyIdBytes.size + 4 + envelope.aad.size
        val badTagLenBinary = validBinary.copyOf()
        badTagLenBinary[tagLenOffset] = 10 // Tag length < AUTH_TAG_LENGTH (16)
        assertFailsWith<EnvelopeCorruptedException>("Tag length < 16 must throw EnvelopeCorruptedException") {
            VersionedEncryptedEnvelope.deserialize(badTagLenBinary)
        }
    }

    @Test
    fun test_adversarial_envelope_unversioned_plaintext_injection_fails_closed() {
        val unversionedPayloads = listOf(
            "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f360873", // Raw hex private key
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", // Raw mnemonic
            "{\"address\":\"0x1234\",\"privateKey\":\"0xabcd\"}", // Raw JSON
            "WWEN_NOT_REAL_BASE64_ENVELOPE_DATA",
            "dGVzdF9wbGFpbnRleHRfd2l0aG91dF9tYWdpYw==", // Base64("test_plaintext_without_magic")
            "",
            "   \n\t  "
        )

        for (payload in unversionedPayloads) {
            assertFailsWith<UnversionedPlaintextException>("Unversioned plaintext injection '$payload' must throw UnversionedPlaintextException") {
                VersionedEncryptedEnvelope.deserializeFromBase64(payload)
            }
        }
    }

    @Test
    fun test_adversarial_envelope_legacy_format_rejection_in_standard_deserializer() {
        // Construct legacy format string (v1:salt:nonce:tag:ciphertext)
        val salt = CryptoUtils.randomBytes(16)
        val derivedKey = CryptoUtils.pbkdf2(
            password = testPassword,
            salt = salt,
            iterations = 100_000,
            keyLength = 32
        )
        val encrypted = CryptoUtils.aesGcmEncrypt(testPayload, derivedKey)
        val legacyString = listOf(
            "v1",
            salt.toBase64(),
            encrypted.nonce.toBase64(),
            encrypted.authTag.toBase64(),
            encrypted.ciphertext.toBase64()
        ).joinToString(":")

        assertTrue(VersionedEncryptedEnvelope.isLegacyFormat(legacyString), "isLegacyFormat must detect legacy string")

        // Standard deserializer MUST fail closed with UnversionedPlaintextException
        assertFailsWith<UnversionedPlaintextException>("deserializeFromBase64 must reject legacy format") {
            VersionedEncryptedEnvelope.deserializeFromBase64(legacyString)
        }

        // Migration pathway MUST successfully convert legacy data to genuine VersionedEncryptedEnvelope
        val migrated = VersionedEncryptedEnvelope.migrateLegacy(
            legacyString = legacyString,
            password = testPassword.decodeToString(),
            keyId = "migrated-key-adversarial",
            aad = testAad
        )
        assertEquals(VersionedEncryptedEnvelope.CURRENT_VERSION, migrated.version)
        assertEquals("migrated-key-adversarial", migrated.keyId)
        val decrypted = migrated.decrypt(testPassword, testAad)
        assertTrue(decrypted.contentEquals(testPayload), "Migrated envelope must decrypt accurately")
    }

    @Test
    fun test_adversarial_envelope_memory_zeroization_after_wipe() {
        val envelope = VersionedEncryptedEnvelope.encrypt(
            plaintext = testPayload,
            password = testPassword,
            keyId = testKeyId,
            aad = testAad
        )

        assertFalse(envelope.salt.all { it == 0.toByte() }, "Salt must not be initially zeroized")
        assertFalse(envelope.nonce.all { it == 0.toByte() }, "Nonce must not be initially zeroized")
        assertFalse(envelope.ciphertext.all { it == 0.toByte() }, "Ciphertext must not be initially zeroized")
        assertFalse(envelope.authTag.all { it == 0.toByte() }, "Auth tag must not be initially zeroized")

        envelope.secureZero()

        assertTrue(envelope.salt.all { it == 0.toByte() }, "Salt must be zeroized after secureZero()")
        assertTrue(envelope.nonce.all { it == 0.toByte() }, "Nonce must be zeroized after secureZero()")
        assertTrue(envelope.ciphertext.all { it == 0.toByte() }, "Ciphertext must be zeroized after secureZero()")
        assertTrue(envelope.authTag.all { it == 0.toByte() }, "Auth tag must be zeroized after secureZero()")
        assertTrue(envelope.aad.all { it == 0.toByte() }, "AAD must be zeroized after secureZero()")
    }
}
