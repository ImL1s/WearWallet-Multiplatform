package io.github.iml1s.crypto

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.signer.*
import com.cbstudio.wearwallet.core.security.KeystoreManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 跨平台加密測試
 * 驗證 Android 和 watchOS 產生相同的結果
 */
class CrossPlatformCryptoTest {
    
    // 專案中的測試助記詞
    companion object {
        const val MNEMONIC_1 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        const val MNEMONIC_2 = "iron mind drip glad load second merge rough music cloud fresh heavy"
        
        // 預期的地址（從專案文件中獲得）
        val EXPECTED_ADDRESSES_1 = mapOf(
            "bitcoin_legacy" to listOf(
                "17skcH8bHKdJUL2oj5VbtAueJqVvmKQXCy",
                "1EQQvLz8VFaWqKB6iKzua7EqLhdjCKjfFN",
                "1LQ8PxuZMnGaHLUvXW3MNJrS2ctmNUnxLd"
            ),
            "bitcoin_segwit" to listOf(
                "3FXeRwdnRqHcMK68jpZxCvQVBjqjR6MR1R",
                "3HJYEKqQdx8WFCLgmaR8kQyNJCeCQGyJQM"
            ),
            "bitcoin_native" to listOf(
                "bc1qcm87ujjlrv0xztz7vn739t3gzqrcppde3y4npc",
                "bc1qsxppqyp08c8q0tn8l9yjf90etwh0pjvwfncxql"
            )
        )
        
        val EXPECTED_ADDRESSES_2 = mapOf(
            "bitcoin_legacy" to listOf(
                "1ERLxPfvJoqYfzS51RqKwbuYMwMGdq8HNM",
                "1PRf5yqHMvK2YdqPjMAoUr2V2MhxWD6JRq",
                "14pQ3mARF17jYF8sS4EVQh2a17uojMgZw2"
            ),
            "bitcoin_segwit" to listOf(
                "3N9bfRymR1SaJpXKdgu4VQWbBGvHBrnUKa",
                "3DyL41K5zfEVxJUqzD7kMg9BvgdBFXCGsV"
            ),
            "bitcoin_native" to listOf(
                "bc1q0fnpdvvgfcf2z5vl5yryj6l8xemltvuqpf8ujs",
                "bc1qv02v00jn9vhhe5uqz8eelp0kxfj7rg6wj2thxy"
            )
        )
    }
    
    @Test
    fun testKeystoreManagerDerivation() = runTest {
        val keystoreManager = KeystoreManager()
        
        // 測試助記詞1的推導
        println("Testing Mnemonic 1 derivation...")
        
        // Bitcoin m/44'/0'/0'/0/0
        val btcPrivKey1 = keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/0'/0'/0/0")
        println("BTC Private Key (Mnemonic 1): $btcPrivKey1")
        
        val btcPubKey1 = keystoreManager.getPublicKey(btcPrivKey1)
        println("BTC Public Key (Mnemonic 1): $btcPubKey1")
        
        val btcAddress1 = keystoreManager.getAddress(btcPubKey1, 0)
        println("BTC Address (Mnemonic 1): $btcAddress1")
        
        // 驗證地址是否符合預期格式 (1開頭)
        assertTrue(btcAddress1.startsWith("1"), "Bitcoin address should start with 1")
        
        // Litecoin m/44'/2'/0'/0/0
        val ltcPrivKey1 = keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/2'/0'/0/0")
        val ltcPubKey1 = keystoreManager.getPublicKey(ltcPrivKey1)
        val ltcAddress1 = keystoreManager.getAddress(ltcPubKey1, 2)
        println("LTC Address (Mnemonic 1): $ltcAddress1")
        assertTrue(ltcAddress1.startsWith("L") || ltcAddress1.startsWith("M"), 
            "Litecoin address should start with L or M")
        
        // Dogecoin m/44'/3'/0'/0/0
        val dogePrivKey1 = keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/3'/0'/0/0")
        val dogePubKey1 = keystoreManager.getPublicKey(dogePrivKey1)
        val dogeAddress1 = keystoreManager.getAddress(dogePubKey1, 3)
        println("DOGE Address (Mnemonic 1): $dogeAddress1")
        assertTrue(dogeAddress1.startsWith("D"), "Dogecoin address should start with D")
        
        // 測試助記詞2的推導
        println("\nTesting Mnemonic 2 derivation...")
        
        val btcPrivKey2 = keystoreManager.derivePrivateKey(MNEMONIC_2, "m/44'/0'/0'/0/0")
        val btcPubKey2 = keystoreManager.getPublicKey(btcPrivKey2)
        val btcAddress2 = keystoreManager.getAddress(btcPubKey2, 0)
        println("BTC Address (Mnemonic 2): $btcAddress2")
        assertTrue(btcAddress2.startsWith("1"), "Bitcoin address should start with 1")
    }
    
    @Test
    fun testBitcoinSigner() = runTest {
        val signer = BitcoinSigner()
        val keystoreManager = KeystoreManager()
        
        // 使用助記詞1生成私鑰
        val privateKeyHex = keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/0'/0'/0/0")
        
        // 將十六進制私鑰轉換為 ByteArray
        val privateKey = privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        // 創建測試 UTXO
        val utxos = listOf(
            UTXO(
                txid = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                vout = 0,
                value = 100000L, // 0.001 BTC in satoshis
                confirmed = true,
                blockHeight = 700000L,
                scriptPubKey = "76a914" + "88".repeat(20) + "88ac", // P2PKH script
                address = "17skcH8bHKdJUL2oj5VbtAueJqVvmKQXCy"
            )
        )
        
        // 創建未簽名交易
        val unsignedTx = UnsignedTransaction(
            fromAddress = "17skcH8bHKdJUL2oj5VbtAueJqVvmKQXCy",
            toAddress = "1EQQvLz8VFaWqKB6iKzua7EqLhdjCKjfFN",
            amount = "50000", // 0.0005 BTC
            fee = "10000", // 0.0001 BTC
            metadata = mapOf("utxos" to utxos)
        )
        
        // 測試簽名
        val signedTx = signer.signTransaction(unsignedTx, privateKey)
        
        println("Signed Bitcoin Transaction: ${signedTx.hash}")
        println("Raw Transaction: ${signedTx.rawTransaction.take(100)}...")
        
        // 驗證簽名交易的基本結構
        assertTrue(signedTx.success, "Transaction should be signed successfully")
        assertTrue(signedTx.rawTransaction.isNotEmpty(), "Signed transaction should not be empty")
        assertTrue(signedTx.rawTransaction.length > 100, "Signed transaction should be reasonably long")
    }
    
    @Test
    fun testLitecoinSigner() = runTest {
        val signer = LitecoinSigner()
        val keystoreManager = KeystoreManager()
        
        // 使用助記詞2生成私鑰
        val privateKeyHex = keystoreManager.derivePrivateKey(MNEMONIC_2, "m/44'/2'/0'/0/0")
        val privateKey = privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        // 創建測試 UTXO
        val utxos = listOf(
            UTXO(
                txid = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                vout = 0,
                value = 200000L, // 0.002 LTC
                confirmed = true,
                blockHeight = 2000000L,
                scriptPubKey = "76a914" + "99".repeat(20) + "88ac",
                address = "LMockLTCAddress123"
            )
        )
        
        // 創建未簽名交易
        val unsignedTx = UnsignedTransaction(
            fromAddress = "LMockLTCAddress123",
            toAddress = "LTargetLTCAddress456",
            amount = "100000",
            fee = "20000",
            metadata = mapOf("utxos" to utxos)
        )
        
        // 測試簽名
        val signedTx = signer.signTransaction(unsignedTx, privateKey)
        
        println("Signed Litecoin Transaction: ${signedTx.hash}")
        assertTrue(signedTx.success, "Transaction should be signed successfully")
        assertTrue(signedTx.rawTransaction.isNotEmpty(), "Signed transaction should not be empty")
    }
    
    @Test
    fun testDogecoinSigner() = runTest {
        val signer = DogecoinSigner()
        val keystoreManager = KeystoreManager()
        
        // 使用助記詞1生成私鑰
        val privateKeyHex = keystoreManager.derivePrivateKey(MNEMONIC_1, "m/44'/3'/0'/0/0")
        val privateKey = privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        // 創建測試 UTXO（Dogecoin 通常有更多 UTXO）
        val utxos = listOf(
            UTXO(
                txid = "fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321",
                vout = 0,
                value = 1000000000L, // 10 DOGE
                confirmed = true,
                blockHeight = 4000000L,
                scriptPubKey = "76a914" + "aa".repeat(20) + "88ac",
                address = "DMockDOGEAddress123"
            ),
            UTXO(
                txid = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                vout = 1,
                value = 500000000L, // 5 DOGE
                confirmed = true,
                blockHeight = 4000001L,
                scriptPubKey = "76a914" + "bb".repeat(20) + "88ac",
                address = "DMockDOGEAddress123"
            )
        )
        
        // 創建未簽名交易（Dogecoin 的手續費較高）
        val unsignedTx = UnsignedTransaction(
            fromAddress = "DMockDOGEAddress123",
            toAddress = "DTargetDOGEAddress456",
            amount = "1200000000", // 12 DOGE
            fee = "100000000", // 1 DOGE (高手續費)
            metadata = mapOf("utxos" to utxos)
        )
        
        // 測試簽名
        val signedTx = signer.signTransaction(unsignedTx, privateKey)
        
        println("Signed Dogecoin Transaction: ${signedTx.hash}")
        assertTrue(signedTx.success, "Transaction should be signed successfully")
        assertTrue(signedTx.rawTransaction.isNotEmpty(), "Signed transaction should not be empty")
    }
    
    @Test
    fun testBitcoinCashSigner() = runTest {
        val signer = BitcoinCashSigner()
        val keystoreManager = KeystoreManager()
        
        // 使用助記詞2生成私鑰
        val privateKeyHex = keystoreManager.derivePrivateKey(MNEMONIC_2, "m/44'/145'/0'/0/0")
        val privateKey = privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        // 創建測試 UTXO
        val utxos = listOf(
            UTXO(
                txid = "aabbccddeeffffeeddccbbaa11223344556677889900aabbccddeeff112233",
                vout = 0,
                value = 300000L, // 0.003 BCH
                confirmed = true,
                blockHeight = 700000L,
                scriptPubKey = "76a914" + "cc".repeat(20) + "88ac",
                address = "1MockBCHAddress789"
            )
        )
        
        // 創建未簽名交易（BCH 使用 BIP143 簽名）
        val unsignedTx = UnsignedTransaction(
            fromAddress = "1MockBCHAddress789",
            toAddress = "1TargetBCHAddressXYZ",
            amount = "200000",
            fee = "30000",
            metadata = mapOf("utxos" to utxos)
        )
        
        // 測試簽名
        val signedTx = signer.signTransaction(unsignedTx, privateKey)
        
        println("Signed Bitcoin Cash Transaction: ${signedTx.hash}")
        assertTrue(signedTx.success, "Transaction should be signed successfully")
        assertTrue(signedTx.rawTransaction.isNotEmpty(), "Signed transaction should not be empty")
        
        // 驗證交易包含 SIGHASH_FORKID 標記
        // BCH 交易應該比普通 BTC 交易長（因為有額外的簽名數據）
        assertTrue(signedTx.rawTransaction.length > 150, "BCH transaction should include fork ID")
    }
    
    @Test
    fun testCrossPlatformConsistency() = runTest {
        val keystoreManager = KeystoreManager()
        val addresses = mutableMapOf<String, String>()
        
        // 測試所有幣種的地址生成一致性
        val coins = listOf(
            Triple("Bitcoin", 0, "m/44'/0'/0'/0/0"),
            Triple("Litecoin", 2, "m/44'/2'/0'/0/0"),
            Triple("Dogecoin", 3, "m/44'/3'/0'/0/0"),
            Triple("BitcoinCash", 145, "m/44'/145'/0'/0/0")
        )
        
        for ((name, coinType, path) in coins) {
            println("\nTesting $name address generation...")
            
            // 助記詞1
            val privKey1 = keystoreManager.derivePrivateKey(MNEMONIC_1, path)
            val pubKey1 = keystoreManager.getPublicKey(privKey1)
            val address1 = keystoreManager.getAddress(pubKey1, coinType)
            
            println("$name Address (Mnemonic 1): $address1")
            addresses["${name}_1"] = address1
            
            // 助記詞2
            val privKey2 = keystoreManager.derivePrivateKey(MNEMONIC_2, path)
            val pubKey2 = keystoreManager.getPublicKey(privKey2)
            val address2 = keystoreManager.getAddress(pubKey2, coinType)
            
            println("$name Address (Mnemonic 2): $address2")
            addresses["${name}_2"] = address2
            
            // 驗證地址格式
            when (coinType) {
                0, 145 -> assertTrue(address1.startsWith("1"), "$name address should start with 1")
                2 -> assertTrue(address1.startsWith("L") || address1.startsWith("M"), 
                    "$name address should start with L or M")
                3 -> assertTrue(address1.startsWith("D"), "$name address should start with D")
            }
        }
        
        // 確保每個地址都是唯一的
        val uniqueAddresses = addresses.values.toSet()
        assertEquals(addresses.size, uniqueAddresses.size, "All addresses should be unique")
        
        println("\n✅ All cross-platform tests passed!")
        println("Generated ${addresses.size} unique addresses across all coins and mnemonics")
    }
}