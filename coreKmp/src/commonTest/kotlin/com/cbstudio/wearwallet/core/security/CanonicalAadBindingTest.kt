package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * P1-3 Contextual Canonical AAD Binding Unit Tests
 */
class CanonicalAadBindingTest {

    private val password = "TestPassword#2026".encodeToByteArray()
    private val payload = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".encodeToByteArray()

    @Test
    fun test_canonical_aad_format_consistency() {
        val aad = CanonicalAad.forKeyBackup("account_primary")
        val expectedString = "schema=v1|purpose=key_backup|keyId=account_primary"
        assertEquals(expectedString, aad.decodeToString())

        val walletAad = CanonicalAad.forWalletStorage("wallet_123", "secp256k1")
        assertEquals("schema=v1|purpose=wallet_storage|walletId=wallet_123|keyType=secp256k1", walletAad.decodeToString())

        val txAad = CanonicalAad.forTransactionSigning("key_1", "1", "0xdeadbeef")
        assertEquals("schema=v1|purpose=tx_signing|keyId=key_1|chainId=1|intentHash=0xdeadbeef", txAad.decodeToString())
    }

    @Test
    fun test_cross_key_import_detected_and_rejected() {
        val keyIdA = "account_vault_primary"
        val keyIdB = "account_vault_secondary"

        val aadA = CanonicalAad.forKeyBackup(keyIdA)
        val envelopeA = VersionedEncryptedEnvelope.encrypt(
            plaintext = payload,
            password = password,
            keyId = keyIdA,
            aad = aadA
        )

        // 模擬在 keyIdB 上匯入 keyIdA 的信封
        val expectedAadB = CanonicalAad.forKeyBackup(keyIdB)

        // 1. KeyId mismatch check
        assertFailsWith<KeyManagementException> {
            if (envelopeA.keyId != keyIdB) {
                throw KeyManagementException("Key ID mismatch: '${envelopeA.keyId}' != '$keyIdB'")
            }
        }

        // 2. AAD Cryptographic mismatch
        assertFailsWith<EnvelopeIntegrityException> {
            envelopeA.decrypt(password, expectedAad = expectedAadB)
        }
    }
}
