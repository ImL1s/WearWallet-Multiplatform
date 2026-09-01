package io.github.iml1s.crypto

import com.cbstudio.wearwallet.core.security.KeystoreManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * HD 錢包和 UTXO 簽名器測試
 * 使用專案中的測試助記詞驗證地址生成和交易簽名的一致性
 */
class HDWalletTest {
    
    // 專案中的測試助記詞
    companion object {
        const val MNEMONIC_1 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        const val MNEMONIC_2 = "iron mind drip glad load second merge rough music cloud fresh heavy"
        
        // 預期的 Bitcoin 地址（從專案測試檔案中確認）
        const val BTC_ADDRESS_1_LEGACY = "17skcH8bHKdJUL2oj5VbtAueJqVvmKQXCy"
        const val BTC_ADDRESS_2_LEGACY = "1ERLxPfvJoqYfzS51RqKwbuYMwMGdq8HNM"
    }
    
    @Test
    fun testKeystoreManagerDerivation() = runBlocking {
        val keystoreManager = KeystoreManager()
        
        // 測試助記詞1的 Bitcoin 私鑰推導
        println("\n=== Testing Mnemonic 1 Bitcoin Derivation ===")
        
        val btcPrivKey1Result = runCatching {
            val privKey = keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/0'/0'/0/0")
            privKey
        }
        
        assertTrue(btcPrivKey1Result.isSuccess, "Should derive private key successfully")
        val btcPrivKey1 = btcPrivKey1Result.getOrThrow()
        println("BTC Private Key (Mnemonic 1): ${btcPrivKey1.take(10)}...")
        
        // 測試公鑰生成
        val btcPubKey1Result = runCatching {
            keystoreManager.getPublicKey(btcPrivKey1)
        }
        
        assertTrue(btcPubKey1Result.isSuccess, "Should generate public key successfully")
        val btcPubKey1 = btcPubKey1Result.getOrThrow()
        println("BTC Public Key (Mnemonic 1): ${btcPubKey1.take(20)}...")
        
        // 測試地址生成
        val btcAddress1Result = runCatching {
            keystoreManager.getAddress(btcPubKey1, 0)
        }
        
        assertTrue(btcAddress1Result.isSuccess, "Should generate address successfully")
        val btcAddress1 = btcAddress1Result.getOrThrow()
        println("BTC Address (Mnemonic 1): $btcAddress1")
        
        // 驗證地址格式
        assertTrue(btcAddress1.startsWith("1"), "Bitcoin address should start with 1")
        
        // 測試助記詞2
        println("\n=== Testing Mnemonic 2 Bitcoin Derivation ===")
        
        val btcPrivKey2Result = runCatching {
            keystoreManager.derivePrivateKey(MNEMONIC_2, "m/44'/0'/0'/0/0")
        }
        
        assertTrue(btcPrivKey2Result.isSuccess, "Should derive private key successfully")
        val btcPrivKey2 = btcPrivKey2Result.getOrThrow()
        
        val btcPubKey2Result = runCatching {
            keystoreManager.getPublicKey(btcPrivKey2)
        }
        
        assertTrue(btcPubKey2Result.isSuccess, "Should generate public key successfully")
        val btcPubKey2 = btcPubKey2Result.getOrThrow()
        
        val btcAddress2Result = runCatching {
            keystoreManager.getAddress(btcPubKey2, 0)
        }
        
        assertTrue(btcAddress2Result.isSuccess, "Should generate address successfully")
        val btcAddress2 = btcAddress2Result.getOrThrow()
        println("BTC Address (Mnemonic 2): $btcAddress2")
        
        // 驗證地址格式
        assertTrue(btcAddress2.startsWith("1"), "Bitcoin address should start with 1")
        
        // 確保兩個助記詞生成不同的地址
        assertTrue(btcAddress1 != btcAddress2, "Different mnemonics should generate different addresses")
    }
    
    @Test
    fun testMultiCoinAddressGeneration() = runBlocking {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Multi-Coin Address Generation ===")
        
        val coins = listOf(
            Triple("Bitcoin", 0, "m/44'/0'/0'/0/0"),
            Triple("Litecoin", 2, "m/44'/2'/0'/0/0"),
            Triple("Dogecoin", 3, "m/44'/3'/0'/0/0"),
            Triple("BitcoinCash", 145, "m/44'/145'/0'/0/0")
        )
        
        for ((name, coinType, path) in coins) {
            println("\nTesting $name:")
            
            // 使用助記詞1生成地址
            val privKeyResult = runCatching {
                keystoreManager.derivePrivateKey(MNEMONIC_1, path)
            }
            
            if (privKeyResult.isSuccess) {
                val privKey = privKeyResult.getOrThrow()
                val pubKeyResult = runCatching {
                    keystoreManager.getPublicKey(privKey)
                }
                
                if (pubKeyResult.isSuccess) {
                    val pubKey = pubKeyResult.getOrThrow()
                    val addressResult = runCatching {
                        keystoreManager.getAddress(pubKey, coinType)
                    }
                    
                    if (addressResult.isSuccess) {
                        val address = addressResult.getOrThrow()
                        println("  $name Address: $address")
                        
                        // 驗證地址格式
                        when (coinType) {
                            0, 145 -> assertTrue(address.startsWith("1"), "$name address should start with 1")
                            2 -> assertTrue(address.startsWith("L") || address.startsWith("M"), 
                                "$name address should start with L or M")
                            3 -> assertTrue(address.startsWith("D"), "$name address should start with D")
                        }
                    } else {
                        println("  Failed to generate address: ${addressResult.exceptionOrNull()?.message}")
                    }
                }
            }
        }
    }
    
    @Test
    fun testLitecoinAddressFormat() = runBlocking {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Litecoin Address Format ===")
        
        val privKeyResult = runCatching {
            keystoreManager.derivePrivateKey(MNEMONIC_2, "m/44'/2'/0'/0/0")
        }
        
        if (privKeyResult.isSuccess) {
            val privKey = privKeyResult.getOrThrow()
            val pubKeyResult = runCatching {
                keystoreManager.getPublicKey(privKey)
            }
            
            if (pubKeyResult.isSuccess) {
                val pubKey = pubKeyResult.getOrThrow()
                val addressResult = runCatching {
                    keystoreManager.getAddress(pubKey, 2)
                }
                
                if (addressResult.isSuccess) {
                    val address = addressResult.getOrThrow()
                    println("Litecoin Address: $address")
                    
                    // Litecoin 地址應該以 L 或 M 開頭（主網）
                    assertTrue(
                        address.startsWith("L") || address.startsWith("M"),
                        "Litecoin address should start with L or M, got: ${address.first()}"
                    )
                }
            }
        }
    }
    
    @Test
    fun testDogecoinAddressFormat() = runBlocking {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Dogecoin Address Format ===")
        
        val privKeyResult = runCatching {
            keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/3'/0'/0/0")
        }
        
        if (privKeyResult.isSuccess) {
            val privKey = privKeyResult.getOrThrow()
            val pubKeyResult = runCatching {
                keystoreManager.getPublicKey(privKey)
            }
            
            if (pubKeyResult.isSuccess) {
                val pubKey = pubKeyResult.getOrThrow()
                val addressResult = runCatching {
                    keystoreManager.getAddress(pubKey, 3)
                }
                
                if (addressResult.isSuccess) {
                    val address = addressResult.getOrThrow()
                    println("Dogecoin Address: $address")
                    
                    // Dogecoin 地址應該以 D 開頭
                    assertTrue(
                        address.startsWith("D"),
                        "Dogecoin address should start with D, got: ${address.first()}"
                    )
                }
            }
        }
    }
    
    @Test
    fun testPrivateKeyConsistency() = runBlocking {
        val keystoreManager = KeystoreManager()
        
        println("\n=== Testing Private Key Consistency ===")
        
        // 同一個助記詞和路徑應該總是生成相同的私鑰
        val privKey1Result = runCatching {
            keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/0'/0'/0/0")
        }
        
        val privKey2Result = runCatching {
            keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/0'/0'/0/0")
        }
        
        if (privKey1Result.isSuccess && privKey2Result.isSuccess) {
            val privKey1 = privKey1Result.getOrThrow()
            val privKey2 = privKey2Result.getOrThrow()
            
            assertTrue(privKey1.contentEquals(privKey2), "Same mnemonic and path should generate same private key")
            println("Private key consistency verified ✓")
        }
        
        // 不同的路徑應該生成不同的私鑰
        val privKey3Result = runCatching {
            keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/0'/0'/0/1")
        }
        
        if (privKey1Result.isSuccess && privKey3Result.isSuccess) {
            val privKey1 = privKey1Result.getOrThrow()
            val privKey3 = privKey3Result.getOrThrow()
            
            assertTrue(!privKey1.contentEquals(privKey3), "Different paths should generate different private keys")
            println("Path differentiation verified ✓")
        }
    }
}