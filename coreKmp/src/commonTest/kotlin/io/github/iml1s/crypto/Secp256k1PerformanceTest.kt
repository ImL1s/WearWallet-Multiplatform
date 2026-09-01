package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Secp256k1 性能基準測試
 *
 * 測試簽名、驗證、公鑰生成等操作的性能
 *
 * 性能目標（參考值，不同平台可能有差異）：
 * - 簽名: < 10ms/次
 * - 驗證: < 15ms/次
 * - 公鑰生成: < 5ms/次
 * - ECDH: < 10ms/次
 */
class Secp256k1PerformanceTest {

    companion object {
        // 性能測試的迭代次數
        private const val ITERATIONS = 100

        // 壓力測試的迭代次數
        private const val STRESS_ITERATIONS = 1000
    }

    //region 基準性能測試

    /**
     * 測試：簽名操作性能
     */
    @Test
    fun benchmark_signing() {
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0x02 }

        // 預熱
        repeat(10) {
            Secp256k1Provider.sign(privateKey, messageHash)
        }

        // 性能測試
        val startTime = currentTimeMillis()
        repeat(ITERATIONS) {
            Secp256k1Provider.sign(privateKey, messageHash)
        }
        val duration = currentTimeMillis() - startTime

        val avgTime = duration.toDouble() / ITERATIONS
        println("📊 簽名性能: 平均 ${avgTime.formatTwoDecimals()} ms/次 ($ITERATIONS 次)")
        println("   總耗時: $duration ms")

        // 性能驗證（寬鬆限制，主要用於檢測性能退化）
        assertTrue(
            avgTime < 100.0,
            "Signing performance: average time should be < 100ms, got ${avgTime.formatTwoDecimals()}ms"
        )
    }

    /**
     * 測試：驗證操作性能
     */
    @Test
    fun benchmark_verification() {
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0x02 }

        // 預先生成簽名和公鑰
        val signature = Secp256k1Provider.sign(privateKey, messageHash)
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        // 預熱
        repeat(10) {
            Secp256k1Provider.verify(signature, messageHash, publicKey)
        }

        // 性能測試
        val startTime = currentTimeMillis()
        repeat(ITERATIONS) {
            Secp256k1Provider.verify(signature, messageHash, publicKey)
        }
        val duration = currentTimeMillis() - startTime

        val avgTime = duration.toDouble() / ITERATIONS
        println("📊 驗證性能: 平均 ${avgTime.formatTwoDecimals()} ms/次 ($ITERATIONS 次)")
        println("   總耗時: $duration ms")

        assertTrue(
            avgTime < 100.0,
            "Verification performance: average time should be < 100ms, got ${avgTime.formatTwoDecimals()}ms"
        )
    }

    /**
     * 測試：公鑰生成性能（壓縮格式）
     */
    @Test
    fun benchmark_publicKeyGeneration_compressed() {
        val privateKey = ByteArray(32) { 0x01 }

        // 預熱
        repeat(10) {
            Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        }

        // 性能測試
        val startTime = currentTimeMillis()
        repeat(ITERATIONS) {
            Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        }
        val duration = currentTimeMillis() - startTime

        val avgTime = duration.toDouble() / ITERATIONS
        println("📊 公鑰生成（壓縮）: 平均 ${avgTime.formatTwoDecimals()} ms/次 ($ITERATIONS 次)")
        println("   總耗時: $duration ms")

        assertTrue(
            avgTime < 50.0,
            "Public key generation (compressed): average time should be < 50ms, got ${avgTime.formatTwoDecimals()}ms"
        )
    }

    /**
     * 測試：公鑰生成性能（未壓縮格式）
     */
    @Test
    fun benchmark_publicKeyGeneration_uncompressed() {
        val privateKey = ByteArray(32) { 0x01 }

        // 預熱
        repeat(10) {
            Secp256k1Provider.computePublicKey(privateKey, compressed = false)
        }

        // 性能測試
        val startTime = currentTimeMillis()
        repeat(ITERATIONS) {
            Secp256k1Provider.computePublicKey(privateKey, compressed = false)
        }
        val duration = currentTimeMillis() - startTime

        val avgTime = duration.toDouble() / ITERATIONS
        println("📊 公鑰生成（未壓縮）: 平均 ${avgTime.formatTwoDecimals()} ms/次 ($ITERATIONS 次)")
        println("   總耗時: $duration ms")

        assertTrue(
            avgTime < 50.0,
            "Public key generation (uncompressed): average time should be < 50ms, got ${avgTime.formatTwoDecimals()}ms"
        )
    }

    /**
     * 測試：ECDH 性能
     */
    @Test
    fun benchmark_ecdh() {
        val privateKeyA = ByteArray(32) { 0x01 }
        val privateKeyB = ByteArray(32) { 0x02 }
        val publicKeyB = Secp256k1Provider.computePublicKey(privateKeyB)

        // 預熱
        repeat(10) {
            Secp256k1Provider.ecdh(privateKeyA, publicKeyB)
        }

        // 性能測試
        val startTime = currentTimeMillis()
        repeat(ITERATIONS) {
            Secp256k1Provider.ecdh(privateKeyA, publicKeyB)
        }
        val duration = currentTimeMillis() - startTime

        val avgTime = duration.toDouble() / ITERATIONS
        println("📊 ECDH 性能: 平均 ${avgTime.formatTwoDecimals()} ms/次 ($ITERATIONS 次)")
        println("   總耗時: $duration ms")

        assertTrue(
            avgTime < 100.0,
            "ECDH performance: average time should be < 100ms, got ${avgTime.formatTwoDecimals()}ms"
        )
    }

    //endregion

    //region 壓力測試

    /**
     * 測試：大量簽名壓力測試
     *
     * 測試實現在持續負載下的穩定性和一致性
     */
    @Test
    fun stress_continuousSigning() {
        println("\n🔥 壓力測試: $STRESS_ITERATIONS 次連續簽名")

        val privateKey = ByteArray(32) { 0x01 }
        var successCount = 0
        var failureCount = 0

        val startTime = currentTimeMillis()

        repeat(STRESS_ITERATIONS) { i ->
            try {
                // 使用不同的消息
                val messageHash = ByteArray(32) { (i % 256).toByte() }

                // 簽名
                val signature = Secp256k1Provider.sign(privateKey, messageHash)

                // 驗證簽名格式正確
                if (signature.size == 64) {
                    successCount++
                } else {
                    failureCount++
                    println("❌ 簽名 #$i: 格式錯誤（長度 ${signature.size}）")
                }

                // 每 100 次打印進度
                if ((i + 1) % 100 == 0) {
                    println("   進度: ${i + 1}/$STRESS_ITERATIONS")
                }
            } catch (e: Exception) {
                failureCount++
                println("❌ 簽名 #$i: 異常 - ${e.message}")
            }
        }

        val duration = currentTimeMillis() - startTime
        val avgTime = duration.toDouble() / STRESS_ITERATIONS

        println("✅ 完成 $STRESS_ITERATIONS 次簽名")
        println("   成功: $successCount")
        println("   失敗: $failureCount")
        println("   總耗時: $duration ms")
        println("   平均: ${avgTime.formatTwoDecimals()} ms/次")

        // 驗證成功率
        val successRate = successCount.toDouble() / STRESS_ITERATIONS * 100
        assertTrue(
            successRate >= 99.0,
            "Stress test: success rate should be >= 99%, got ${successRate.formatTwoDecimals()}%"
        )
    }

    /**
     * 測試：大量驗證壓力測試
     */
    @Test
    fun stress_continuousVerification() {
        println("\n🔥 壓力測試: $STRESS_ITERATIONS 次連續驗證")

        val privateKey = ByteArray(32) { 0x01 }
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        // 預先生成簽名
        val signatures = (0 until STRESS_ITERATIONS).map { i ->
            val messageHash = ByteArray(32) { (i % 256).toByte() }
            Pair(Secp256k1Provider.sign(privateKey, messageHash), messageHash)
        }

        var successCount = 0
        var failureCount = 0

        val startTime = currentTimeMillis()

        signatures.forEachIndexed { i, (signature, messageHash) ->
            try {
                val isValid = Secp256k1Provider.verify(signature, messageHash, publicKey)

                if (isValid) {
                    successCount++
                } else {
                    failureCount++
                    println("❌ 驗證 #$i: 失敗")
                }

                // 每 100 次打印進度
                if ((i + 1) % 100 == 0) {
                    println("   進度: ${i + 1}/$STRESS_ITERATIONS")
                }
            } catch (e: Exception) {
                failureCount++
                println("❌ 驗證 #$i: 異常 - ${e.message}")
            }
        }

        val duration = currentTimeMillis() - startTime
        val avgTime = duration.toDouble() / STRESS_ITERATIONS

        println("✅ 完成 $STRESS_ITERATIONS 次驗證")
        println("   成功: $successCount")
        println("   失敗: $failureCount")
        println("   總耗時: $duration ms")
        println("   平均: ${avgTime.formatTwoDecimals()} ms/次")

        // 驗證成功率
        val successRate = successCount.toDouble() / STRESS_ITERATIONS * 100
        assertTrue(
            successRate >= 99.0,
            "Stress test: verification success rate should be >= 99%, got ${successRate.formatTwoDecimals()}%"
        )
    }

    /**
     * 測試：簽名和驗證的端到端壓力測試
     */
    @Test
    fun stress_endToEnd_signAndVerify() {
        println("\n🔥 壓力測試: $STRESS_ITERATIONS 次端到端簽名+驗證")

        val privateKey = ByteArray(32) { 0x01 }
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        var successCount = 0
        var failureCount = 0

        val startTime = currentTimeMillis()

        repeat(STRESS_ITERATIONS) { i ->
            try {
                // 生成隨機消息
                val messageHash = ByteArray(32) { ((i * 37 + it * 17) % 256).toByte() }

                // 簽名
                val signature = Secp256k1Provider.sign(privateKey, messageHash)

                // 驗證
                val isValid = Secp256k1Provider.verify(signature, messageHash, publicKey)

                if (isValid && signature.size == 64) {
                    successCount++
                } else {
                    failureCount++
                    println("❌ 端到端 #$i: 驗證失敗或格式錯誤")
                }

                // 每 100 次打印進度
                if ((i + 1) % 100 == 0) {
                    println("   進度: ${i + 1}/$STRESS_ITERATIONS")
                }
            } catch (e: Exception) {
                failureCount++
                println("❌ 端到端 #$i: 異常 - ${e.message}")
            }
        }

        val duration = currentTimeMillis() - startTime
        val avgTime = duration.toDouble() / STRESS_ITERATIONS

        println("✅ 完成 $STRESS_ITERATIONS 次端到端測試")
        println("   成功: $successCount")
        println("   失敗: $failureCount")
        println("   總耗時: $duration ms")
        println("   平均: ${avgTime.formatTwoDecimals()} ms/次")

        // 驗證成功率
        assertEquals(STRESS_ITERATIONS, successCount, "All end-to-end tests should succeed")
    }

    //endregion

    //region 並發性能測試（可選）

    /**
     * 測試：多個不同私鑰的並發簽名
     *
     * 測試實現是否正確處理多個不同的密鑰對
     */
    @Test
    fun performance_multipleKeys() {
        println("\n📊 性能測試: 10 個不同私鑰各簽名 10 次")

        val privateKeys = (0 until 10).map { i ->
            ByteArray(32) { (i * 13 + it).toByte() }
        }

        var totalSignatures = 0
        val startTime = currentTimeMillis()

        privateKeys.forEach { privateKey ->
            repeat(10) { i ->
                val messageHash = ByteArray(32) { (i * 7).toByte() }
                val signature = Secp256k1Provider.sign(privateKey, messageHash)

                assertEquals(64, signature.size, "Signature should be 64 bytes")
                totalSignatures++
            }
        }

        val duration = currentTimeMillis() - startTime
        val avgTime = duration.toDouble() / totalSignatures

        println("✅ 完成 $totalSignatures 次簽名（10 個密鑰）")
        println("   總耗時: $duration ms")
        println("   平均: ${avgTime.formatTwoDecimals()} ms/次")

        assertTrue(
            avgTime < 100.0,
            "Multiple keys performance: average time should be < 100ms, got ${avgTime.formatTwoDecimals()}ms"
        )
    }

    //endregion

    //region 輔助函數

    /**
     * 獲取當前時間（毫秒）
     *
     * 跨平台兼容的時間獲取函數
     */
    private fun currentTimeMillis(): Long {
        return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    }

    /**
     * 格式化浮點數為兩位小數字符串
     *
     * 跨平台兼容版本（避免使用 Java 的 String.format）
     */
    private fun Double.formatTwoDecimals(): String {
        val intPart = this.toInt()
        val fracPart = ((this - intPart) * 100).toInt()
        val absFracPart = if (fracPart < 0) -fracPart else fracPart
        return "$intPart.${absFracPart.toString().padStart(2, '0')}"
    }

    //endregion
}
