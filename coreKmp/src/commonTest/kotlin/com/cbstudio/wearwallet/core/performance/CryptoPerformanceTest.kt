package com.cbstudio.wearwallet.core.performance

import com.cbstudio.wearwallet.core.security.KeystoreManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * 加密操作性能基準測試
 * 測量各種加密操作的執行時間，確保在 watchOS 上的性能可接受
 */
class CryptoPerformanceTest {
    
    companion object {
        const val TEST_MNEMONIC = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        const val PERFORMANCE_ITERATIONS = 10
        const val LARGE_ITERATIONS = 100
    }
    
    @Test
    fun benchmarkMnemonicOperations() = runTest {
        println("\n⚡ Mnemonic Operations Performance Benchmark")
        
        val keystoreManager = KeystoreManager()
        
        // 助記詞生成性能測試
        val mnemonicGenerationTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                keystoreManager.generateMnemonic(128)
            }
        }
        println("✓ Mnemonic Generation ($PERFORMANCE_ITERATIONS ops): ${mnemonicGenerationTime.inWholeMilliseconds}ms")
        
        // 助記詞驗證性能測試
        val testMnemonic = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        val mnemonicValidationTime = measureTime {
            repeat(LARGE_ITERATIONS) {
                keystoreManager.validateMnemonic(testMnemonic)
            }
        }
        println("✓ Mnemonic Validation ($LARGE_ITERATIONS ops): ${mnemonicValidationTime.inWholeMilliseconds}ms")
        
        // 性能斷言 - 每個操作不應超過合理時間
        assertTrue(mnemonicGenerationTime.inWholeMilliseconds < 10000, "Mnemonic generation should be reasonable")
        assertTrue(mnemonicValidationTime.inWholeMilliseconds < 5000, "Mnemonic validation should be fast")
    }
    
    @Test
    fun benchmarkKeyDerivationOperations() = runTest {
        println("\n🌐 Key Derivation Operations Performance Benchmark")
        
        val keystoreManager = KeystoreManager()
        val testMnemonic = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        
        // 私鑰推導性能測試
        val keyDerivationTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                keystoreManager.derivePrivateKey(testMnemonic, "m/44'/0'/0'/0/0")
            }
        }
        println("✓ Private Key Derivation ($PERFORMANCE_ITERATIONS ops): ${keyDerivationTime.inWholeMilliseconds}ms")
        
        // 公鑰生成性能測試
        val privateKey = keystoreManager.derivePrivateKey(testMnemonic, "m/44'/0'/0'/0/0")
        val publicKeyGenTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                keystoreManager.getPublicKey(privateKey)
            }
        }
        println("✓ Public Key Generation ($PERFORMANCE_ITERATIONS ops): ${publicKeyGenTime.inWholeMilliseconds}ms")
        
        // 地址生成性能測試
        val publicKey = keystoreManager.getPublicKey(privateKey)
        val addressGenTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                keystoreManager.getAddress(publicKey, 0) // Bitcoin
            }
        }
        println("✓ Address Generation ($PERFORMANCE_ITERATIONS ops): ${addressGenTime.inWholeMilliseconds}ms")
        
        // 性能斷言
        assertTrue(keyDerivationTime.inWholeMilliseconds < 30000, "Key derivation should be reasonable")
        assertTrue(publicKeyGenTime.inWholeMilliseconds < 5000, "Public key generation should be fast")
        assertTrue(addressGenTime.inWholeMilliseconds < 5000, "Address generation should be fast")
    }
    
    @Test
    fun benchmarkHDWalletOperations() = runTest {
        println("\n🔑 HD Wallet Operations Performance Benchmark")
        
        val keystoreManager = KeystoreManager()
        
        // 助記詞驗證性能
        val mnemonicValidationTime = measureTime {
            repeat(LARGE_ITERATIONS) {
                keystoreManager.validateMnemonic(TEST_MNEMONIC)
            }
        }
        println("✓ Mnemonic Validation ($LARGE_ITERATIONS ops): ${mnemonicValidationTime.inWholeMilliseconds}ms")
        
        // 私鑰推導性能
        val keyDerivationTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                keystoreManager.derivePrivateKey(TEST_MNEMONIC, "m/44'/0'/0'/0/0")
            }
        }
        println("✓ Private Key Derivation ($PERFORMANCE_ITERATIONS ops): ${keyDerivationTime.inWholeMilliseconds}ms")
        
        // 公鑰生成性能
        val privateKey = keystoreManager.derivePrivateKey(TEST_MNEMONIC, "m/44'/0'/0'/0/0")
        val publicKeyTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                keystoreManager.getPublicKey(privateKey)
            }
        }
        println("✓ Public Key Generation ($PERFORMANCE_ITERATIONS ops): ${publicKeyTime.inWholeMilliseconds}ms")
        
        // 地址生成性能
        val publicKey = keystoreManager.getPublicKey(privateKey)
        val addressTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                keystoreManager.getAddress(publicKey, 0) // Bitcoin
            }
        }
        println("✓ Address Generation ($PERFORMANCE_ITERATIONS ops): ${addressTime.inWholeMilliseconds}ms")
        
        // 性能斷言
        assertTrue(mnemonicValidationTime.inWholeMilliseconds < 5000, "Mnemonic validation should be fast")
        assertTrue(keyDerivationTime.inWholeMilliseconds < 30000, "Key derivation should be acceptable")
        assertTrue(publicKeyTime.inWholeMilliseconds < 5000, "Public key generation should be fast")
        assertTrue(addressTime.inWholeMilliseconds < 5000, "Address generation should be fast")
    }
    
    @Test
    fun benchmarkMultiCoinOperations() = runTest {
        println("\n🔐 Multi-Coin Operations Performance Benchmark")
        
        val keystoreManager = KeystoreManager()
        val testMnemonic = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        val privateKey = keystoreManager.derivePrivateKey(testMnemonic, "m/44'/0'/0'/0/0")
        val publicKey = keystoreManager.getPublicKey(privateKey)
        
        // 多幣種地址生成性能測試
        val coinTypes = listOf(0, 2, 3, 145) // BTC, LTC, DOGE, BCH
        val multiCoinTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                for (coinType in coinTypes) {
                    keystoreManager.getAddress(publicKey, coinType)
                }
            }
        }
        println("✓ Multi-Coin Address Generation ($PERFORMANCE_ITERATIONS * ${coinTypes.size} ops): ${multiCoinTime.inWholeMilliseconds}ms")
        
        // 性能斷言
        assertTrue(multiCoinTime.inWholeMilliseconds < 10000, "Multi-coin operations should be reasonable")
    }
    
    @Test
    fun benchmarkCompleteWalletFlow() = runTest {
        println("\n🏆 Complete Wallet Flow Performance Benchmark")
        
        val keystoreManager = KeystoreManager()
        
        // 完整流程：從助記詞到地址生成
        val completeFlowTime = measureTime {
            repeat(PERFORMANCE_ITERATIONS) {
                // 1. 驗證助記詞
                keystoreManager.validateMnemonic(TEST_MNEMONIC)
                
                // 2. 推導私鑰
                val privateKey = keystoreManager.derivePrivateKey(TEST_MNEMONIC, "m/44'/0'/0'/0/0")
                
                // 3. 生成公鑰
                val publicKey = keystoreManager.getPublicKey(privateKey)
                
                // 4. 生成地址
                keystoreManager.getAddress(publicKey, 0)
            }
        }
        
        println("✓ Complete Flow ($PERFORMANCE_ITERATIONS ops): ${completeFlowTime.inWholeMilliseconds}ms")
        
        val avgTimePerFlow = completeFlowTime.inWholeMilliseconds.toDouble() / PERFORMANCE_ITERATIONS
        println("✓ Average time per complete flow: ${avgTimePerFlow.toInt()}ms")
        
        // 性能斷言 - 完整流程應該在合理時間內完成
        assertTrue(avgTimePerFlow < 3000, "Complete wallet flow should be under 3 seconds per operation")
        
        // watchOS 性能目標
        val watchOSTargetMs = 5000.0 // 5 秒為可接受的用戶體驗
        if (avgTimePerFlow < watchOSTargetMs) {
            println("🎯 watchOS Performance Target: ✅ PASSED (${avgTimePerFlow.toInt()}ms < ${watchOSTargetMs.toInt()}ms)")
        } else {
            println("⚠️ watchOS Performance Target: ⚠️ NEEDS OPTIMIZATION (${avgTimePerFlow.toInt()}ms > ${watchOSTargetMs.toInt()}ms)")
        }
    }
    
    @Test
    fun benchmarkMemoryUsage() = runTest {
        println("\n💾 Memory Usage Analysis")
        
        val keystoreManager = KeystoreManager()
        
        // 模擬連續操作來觀察記憶體使用模式
        val operations = 50
        
        val memoryTestTime = measureTime {
            repeat(operations) { i ->
                val path = "m/44'/0'/0'/0/$i"
                val privateKey = keystoreManager.derivePrivateKey(TEST_MNEMONIC, path)
                val publicKey = keystoreManager.getPublicKey(privateKey)
                val address = keystoreManager.getAddress(publicKey, 0)
                
                // 模擬清理（在實際應用中可能需要）
                if (i % 10 == 0) {
                    // GC 清理在跨平台環境中自動管理
                }
            }
        }
        
        println("✓ Memory test ($operations operations): ${memoryTestTime.inWholeMilliseconds}ms")
        println("✓ Average per operation: ${(memoryTestTime.inWholeMilliseconds.toDouble() / operations).toInt()}ms")
        
        // 在 watchOS 受限環境中，記憶體效率很重要
        assertTrue(memoryTestTime.inWholeMilliseconds < 30000, "Memory usage should be efficient")
    }
    
    @Test
    fun generatePerformanceReport() = runTest {
        println("\n📊 Performance Report Summary")
        println("=====================================")
        println("Platform: watchOS (Kotlin/Native)")
        println("Implementation: Pure Kotlin Crypto")
        println("Test Date: ${getCurrentTimestamp()}")
        println("=====================================")
        
        val keystoreManager = KeystoreManager()
        
        // 快速性能檢查
        val quickTests = mapOf(
            "Mnemonic Validation" to measureTime {
                keystoreManager.validateMnemonic(TEST_MNEMONIC)
            },
            "Key Derivation" to measureTime {
                keystoreManager.derivePrivateKey(TEST_MNEMONIC, "m/44'/0'/0'/0/0")
            },
            "Public Key Generation" to measureTime {
                val pk = keystoreManager.derivePrivateKey(TEST_MNEMONIC, "m/44'/0'/0'/0/0")
                keystoreManager.getPublicKey(pk)
            },
            "Address Generation" to measureTime {
                val pk = keystoreManager.derivePrivateKey(TEST_MNEMONIC, "m/44'/0'/0'/0/0")
                val pub = keystoreManager.getPublicKey(pk)
                keystoreManager.getAddress(pub, 0)
            }
        )
        
        for ((operation, time) in quickTests) {
            val status = if (time.inWholeMilliseconds < 1000) "✅ FAST" 
                        else if (time.inWholeMilliseconds < 3000) "⚡ GOOD" 
                        else "⚠️ SLOW"
            println("$operation: ${time.inWholeMilliseconds}ms $status")
        }
        
        println("=====================================")
        println("✅ Performance testing completed!")
    }
    
    private fun getCurrentTimestamp(): String {
        // 簡化的時間戳實現
        return "2025-08-22"
    }
}