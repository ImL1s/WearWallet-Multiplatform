package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.time.Duration
import io.github.iml1s.crypto.SecureByteArray

class SecureByteArrayPerformanceTest {

    @Test
    fun `test secure clearing performance`() {
        val iterations = 1000
        val keySize = 32 // 256-bit key

        val duration = measureTime {
            repeat(iterations) {
                SecureByteArray.create(keySize).use<Unit> { key ->
                    // 模擬簽名操作
                    @Suppress("UNUSED_VARIABLE")
                    val sum = key.sum()
                }
            }
        }

        val avgTime = duration.inWholeMilliseconds.toDouble() / iterations

        println("Secure clearing performance test:")
        println("  Total iterations: $iterations")
        println("  Key size: $keySize bytes")
        println("  Total duration: ${duration.inWholeMilliseconds}ms")
        println("  Average per operation: ${avgTime}ms")

        // 應該很快 (< 1ms per operation on most platforms)
        assertTrue(avgTime < 1.0, "Average clearing time should be < 1ms, was ${avgTime}ms")
    }

    @Test
    fun `test secure clearing vs regular clearing performance`() {
        val iterations = 1000
        val keySize = 32

        // 測試 SecureByteArray
        val secureDuration = measureTime {
            repeat(iterations) {
                SecureByteArray.create(keySize).use<Unit> { key ->
                    @Suppress("UNUSED_VARIABLE")
                    val sum = key.sum()
                }
            }
        }

        // 測試普通 ByteArray（不清除）
        val regularDuration = measureTime {
            repeat(iterations) {
                val key = ByteArray(keySize)
                @Suppress("UNUSED_VARIABLE")
                val sum = key.sum()
                // 不清除
            }
        }

        println("Performance comparison:")
        println("  SecureByteArray: ${secureDuration.inWholeMilliseconds}ms")
        println("  Regular ByteArray: ${regularDuration.inWholeMilliseconds}ms")
        println("  Overhead: ${(secureDuration - regularDuration).inWholeMilliseconds}ms")

        // 安全清除的開銷應該是可接受的（<10倍）
        val overhead = secureDuration.inWholeMilliseconds.toDouble() /
                      (regularDuration.inWholeMilliseconds.toDouble() + 1.0) // +1 避免除零

        println("  Overhead ratio: ${overhead}x")
        assertTrue(overhead < 10.0, "Overhead should be < 10x, was ${overhead}x")
    }

    @Test
    fun `test large key clearing performance`() {
        val keySize = 1024 // 1KB key
        val iterations = 100

        val duration = measureTime {
            repeat(iterations) {
                SecureByteArray.create(keySize).use<Unit> { key ->
                    @Suppress("UNUSED_VARIABLE")
                    val sum = key.sum()
                }
            }
        }

        val avgTime = duration.inWholeMilliseconds.toDouble() / iterations

        println("Large key clearing performance:")
        println("  Key size: ${keySize} bytes")
        println("  Average per operation: ${avgTime}ms")

        // 即使是大 key 也應該很快
        assertTrue(avgTime < 5.0, "Large key clearing should be < 5ms, was ${avgTime}ms")
    }

    @Test
    fun `test memory allocation stress test`() {
        val keySize = 32
        val iterations = 10000

        val duration = measureTime {
            repeat(iterations) {
                SecureByteArray.create(keySize).use<Unit> { _ ->
                    // 立即釋放
                }
            }
        }

        println("Memory allocation stress test:")
        println("  Iterations: $iterations")
        println("  Total duration: ${duration.inWholeMilliseconds}ms")
        println("  Average per operation: ${duration.inWholeMilliseconds.toDouble() / iterations}ms")

        // 應該能處理大量分配和釋放
        assertTrue(duration.inWholeSeconds < 10, "Should complete in < 10 seconds")
    }

    @Test
    fun `test concurrent secure byte array usage`() {
        val keySize = 32
        val iterations = 100

        // 測試多個 SecureByteArray 同時存在
        val duration = measureTime {
            repeat(iterations) {
                val keys = List(10) { SecureByteArray.create(keySize) }

                // 使用所有 keys
                keys.forEach { key ->
                    key.use<Unit> { data ->
                        @Suppress("UNUSED_VARIABLE")
                        val sum = data.sum()
                    }
                }
            }
        }

        println("Concurrent usage test:")
        println("  Total duration: ${duration.inWholeMilliseconds}ms")

        assertTrue(duration.inWholeSeconds < 5, "Should complete quickly")
    }
}
