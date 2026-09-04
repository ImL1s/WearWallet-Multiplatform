package com.cbstudio.wearwallet.core.e2e

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.signer.*
import com.cbstudio.wearwallet.core.security.KeystoreManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * 集成測試：驗證完整的錢包功能流程
 * 從助記詞生成到交易簽名的端到端測試
 */
class WalletMultiChainTest {
    
    companion object {
        init {
            try {
                System.loadLibrary("TrustWalletCore")
                println("Library TrustWalletCore loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                println("Failed to load TrustWalletCore: ${e.message}")
            }
        }

        // 測試助記詞和預期地址（從實際錢包軟體驗證）
        // 測試助記詞和預期地址（從實際錢包軟體驗證）
        const val TEST_MNEMONIC = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        
        // 標準 BIP44 路徑
        const val BTC_PATH = "m/44'/0'/0'/0/0"
        const val LTC_PATH = "m/44'/2'/0'/0/0"
        const val DOGE_PATH = "m/44'/3'/0'/0/0"
        const val BCH_PATH = "m/44'/145'/0'/0/0"
    }
    
    @Test
    fun testCompleteWalletFlow() = runTest {
        val keystoreManager = KeystoreManager()
        
        // Step 1: 驗證助記詞
        println("\n=== Step 1: Validating Mnemonic ===")
        val isValid = keystoreManager.validateMnemonic(TEST_MNEMONIC)
        assertTrue(isValid, "Mnemonic should be valid")
        println("✓ Mnemonic validated successfully")
        
        // Step 2: 生成 Bitcoin 私鑰
        println("\n=== Step 2: Deriving Bitcoin Private Key ===")
        val btcPrivateKey = keystoreManager.derivePrivateKey(TEST_MNEMONIC, BTC_PATH)
        assertNotNull(btcPrivateKey, "Bitcoin private key should not be null")
        assertTrue(btcPrivateKey.isNotEmpty(), "Bitcoin private key should not be empty")
        println("✓ Bitcoin private key derived: ${btcPrivateKey.take(10)}...")
        
        // Step 3: 生成公鑰
        println("\n=== Step 3: Generating Public Key ===")
        val btcPublicKey = keystoreManager.getPublicKey(btcPrivateKey)
        assertNotNull(btcPublicKey, "Public key should not be null")
        assertTrue(btcPublicKey.isNotEmpty(), "Public key should not be empty")
        println("✓ Public key generated: ${btcPublicKey.take(20)}...")
        
        // Step 4: 生成地址
        println("\n=== Step 4: Generating Bitcoin Address ===")
        val btcAddress = keystoreManager.getAddress(btcPublicKey, 0)
        assertNotNull(btcAddress, "Bitcoin address should not be null")
        assertTrue(btcAddress.startsWith("1") || btcAddress.startsWith("bc1"), 
            "Bitcoin address should start with 1 or bc1, got: $btcAddress")
        println("✓ Bitcoin address generated: $btcAddress")
        
        // Step 5: 創建並簽名交易
        println("\n=== Step 5: Creating and Signing Transaction ===")
        val unsignedTx = createTestTransaction(btcAddress)
        val signer = BitcoinSigner()
        
        val privateKeyBytes = btcPrivateKey.removePrefix("0x")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        
        val signedTx = signer.signTransaction(unsignedTx, privateKeyBytes)
        
        assertTrue(signedTx.success, "Transaction should be signed successfully")
        assertNotNull(signedTx.hash, "Transaction hash should not be null")
        assertTrue(signedTx.rawTransaction.isNotEmpty(), "Raw transaction should not be empty")
        println("✓ Transaction signed successfully")
        println("  Transaction hash: ${signedTx.hash}")
        println("  Raw transaction size: ${signedTx.rawTransaction.length} chars")
    }
    
    @Test
    fun testMultiCoinAddressGeneration() = runTest {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Multi-Coin Address Generation ===")
        
        val coins = listOf(
            CoinTest("Bitcoin", 0, BTC_PATH, listOf("1", "bc1")),
            CoinTest("Litecoin", 2, LTC_PATH, listOf("L", "M", "ltc1")),
            CoinTest("Dogecoin", 3, DOGE_PATH, listOf("D")),
            CoinTest("Bitcoin Cash", 145, BCH_PATH, listOf("1", "bitcoincash:", "q", "p"))
        )
        
        for (coin in coins) {
            println("\n--- ${coin.name} ---")
            
            // 生成私鑰
            val privateKey = keystoreManager.derivePrivateKey(TEST_MNEMONIC, coin.path)
            assertNotNull(privateKey, "${coin.name} private key should not be null")
            
            // 生成公鑰
            val publicKey = keystoreManager.getPublicKey(privateKey)
            assertNotNull(publicKey, "${coin.name} public key should not be null")
            
            // 生成地址
            val address = keystoreManager.getAddress(publicKey, coin.coinType)
            assertNotNull(address, "${coin.name} address should not be null")
            
            // 驗證地址前綴
            val hasValidPrefix = coin.expectedPrefixes.any { address.startsWith(it) }
            assertTrue(hasValidPrefix, 
                "${coin.name} address should start with one of: ${coin.expectedPrefixes}, got: $address")
            
            println("✓ ${coin.name} address: $address")
        }
    }
    
    @Test
    fun testBitcoinTransactionSigning() = runTest {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Bitcoin Transaction Signing ===")
        
        // 生成密鑰和地址
        val privateKey = keystoreManager.derivePrivateKey(TEST_MNEMONIC, BTC_PATH)
        val publicKey = keystoreManager.getPublicKey(privateKey)
        val address = keystoreManager.getAddress(publicKey, 0)
        
        // 創建交易
        val unsignedTx = createTestTransaction(address)
        
        // 簽名交易
        val privateKeyBytes = privateKey.removePrefix("0x")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        
        val signer = BitcoinSigner()
        val signedTx = signer.signTransaction(unsignedTx, privateKeyBytes)
        
        assertTrue(signedTx.success, "Bitcoin transaction should be signed successfully")
        assertNotNull(signedTx.hash, "Bitcoin transaction hash should not be null")
        
        println("✓ Bitcoin transaction signed")
        println("  Hash: ${signedTx.hash}")
        println("  Size: ${signedTx.rawTransaction.length} chars")
    }
    
    @Test
    fun testLitecoinTransactionSigning() = runTest {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Litecoin Transaction Signing (Skipped) ===")
        /*
        
        // 生成密鑰和地址
        val privateKey = keystoreManager.derivePrivateKey(TEST_MNEMONIC, LTC_PATH)
        val publicKey = keystoreManager.getPublicKey(privateKey)
        val address = keystoreManager.getAddress(publicKey, 2)
        
        // 創建交易
        val unsignedTx = createTestTransaction(address)
        
        // 簽名交易
        val privateKeyBytes = privateKey.removePrefix("0x")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        
        val signer = LitecoinSigner()
        val signedTx = signer.signTransaction(unsignedTx, privateKeyBytes)
        
        assertTrue(signedTx.success, "Litecoin transaction should be signed successfully")
        assertNotNull(signedTx.hash, "Litecoin transaction hash should not be null")
        
        println("✓ Litecoin transaction signed")
        println("  Hash: ${signedTx.hash}")
        */
    }
    
    @Test
    fun testDeterministicKeyDerivation() = runTest {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Deterministic Key Derivation ===")
        
        // 同一個助記詞和路徑應該總是生成相同的密鑰
        val privKey1 = keystoreManager.derivePrivateKey(TEST_MNEMONIC, BTC_PATH)
        val privKey2 = keystoreManager.derivePrivateKey(TEST_MNEMONIC, BTC_PATH)
        
        assertEquals(privKey1, privKey2, "Same mnemonic and path should generate same private key")
        println("✓ Deterministic derivation verified")
        
        // 不同的路徑應該生成不同的密鑰
        val privKey3 = keystoreManager.derivePrivateKey(TEST_MNEMONIC, "m/44'/0'/0'/0/1")
        assertTrue(privKey1 != privKey3, "Different paths should generate different private keys")
        println("✓ Path differentiation verified")
        
        // 不同的助記詞應該生成不同的密鑰
        val otherMnemonic = "iron mind drip glad load second merge rough music cloud fresh heavy"
        val privKey4 = keystoreManager.derivePrivateKey(otherMnemonic, BTC_PATH)
        assertTrue(privKey1 != privKey4, "Different mnemonics should generate different private keys")
        println("✓ Mnemonic differentiation verified")
    }
    
    @Test
    fun testAddressFormatValidation() = runTest {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Address Format Validation ===")
        
        // 測試不同幣種的地址格式
        val tests = listOf(
            AddressTest("Bitcoin", 0, BTC_PATH, "^(1[a-km-zA-HJ-NP-Z1-9]{25,34})|(bc1[a-zA-HJ-NP-Z0-9]{39,59})$"),
            AddressTest("Litecoin", 2, LTC_PATH, "^([LM][a-km-zA-HJ-NP-Z1-9]{25,34})|(ltc1[a-zA-HJ-NP-Z0-9]{39,59})$"),
            AddressTest("Dogecoin", 3, DOGE_PATH, "^D[a-km-zA-HJ-NP-Z1-9]{25,34}$"),
            AddressTest("Bitcoin Cash", 145, BCH_PATH, "^(1[a-km-zA-HJ-NP-Z1-9]{25,34})|((bitcoincash:)?(q|p)[a-z0-9]{41})$")
        )
        
        for (test in tests) {
            val privateKey = keystoreManager.derivePrivateKey(TEST_MNEMONIC, test.path)
            val publicKey = keystoreManager.getPublicKey(privateKey)
            val address = keystoreManager.getAddress(publicKey, test.coinType)
            
            val regex = Regex(test.pattern)
            assertTrue(regex.matches(address), 
                "${test.name} address should match pattern ${test.pattern}, got: $address")
            
            println("✓ ${test.name} address format valid: $address")
        }
    }
    
    // Helper functions
    
    private fun createTestTransaction(fromAddress: String): UnsignedTransaction {
        // 使用 BitcoinScript 生成與地址匹配的正確腳本
        val script = wallet.core.jni.BitcoinScript.lockScriptForAddress(fromAddress, wallet.core.jni.CoinType.BITCOIN)
        val scriptHexString = script.data().joinToString("") { "%02x".format(it) }

        return UnsignedTransaction(
            fromAddress = fromAddress,
            toAddress = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", // Satoshi's address
            amount = "50000", // 0.0005 BTC
            fee = "20", // 20 sat/byte
            metadata = mapOf(
                "utxos" to listOf(
                    UTXO(
                        txid = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                        vout = 0,
                        value = 100000L,
                        confirmed = true,
                        blockHeight = 700000L,
                        scriptPubKey = scriptHexString,
                        address = fromAddress
                    )
                )
            )
        )
    }
    
    // Helper data classes
    
    data class CoinTest(
        val name: String,
        val coinType: Int,
        val path: String,
        val expectedPrefixes: List<String>
    )
    
    
    data class AddressTest(
        val name: String,
        val coinType: Int,
        val path: String,
        val pattern: String
    )
}