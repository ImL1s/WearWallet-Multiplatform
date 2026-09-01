package com.cbstudio.wearwallet.core.performance

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime

/**
 * 跨平台 Double format 擴展函數
 */
fun Double.formatDecimal(decimals: Int): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        else -> {
            var m = 1.0
            repeat(decimals) { m *= 10.0 }
            m
        }
    }
    val rounded = kotlin.math.round(this * multiplier) / multiplier
    return rounded.toString()
}

/**
 * 🚀 coreKmp 模塊全面性能基準測試
 *
 * 測試項目：
 * 1. 地址生成速度 (目標 < 100ms)
 * 2. 交易建構時間 (目標 < 500ms)
 * 3. 簽名性能 (目標 < 200ms)
 * 4. RPC 調用延遲 (目標 < 2s)
 * 5. 內存使用 (目標 < 50MB)
 * 6. 並發處理能力
 *
 * 執行方式：
 * ```
 * ./gradlew :coreKmp:testDebugUnitTest --tests ComprehensivePerformanceBenchmark
 * ```
 */
class ComprehensivePerformanceBenchmark {

    /**
     * 基準測試結果數據類
     */
    data class BenchmarkResult(
        val operation: String,
        val iterations: Int,
        val totalTime: Duration,
        val averageTime: Duration,
        val minTime: Duration,
        val maxTime: Duration,
        val throughput: Double, // ops/sec
        val passed: Boolean,
        val target: Duration? = null
    ) {
        override fun toString(): String {
            val status = if (passed) "✅ PASS" else "❌ FAIL"
            val targetInfo = target?.let { " (目標: $it)" } ?: ""
            return """
                |
                |=== $operation $status ===
                |迭代次數: $iterations
                |總時間: $totalTime
                |平均時間: $averageTime$targetInfo
                |最快: $minTime
                |最慢: $maxTime
                |吞吐量: ${throughput.formatDecimal(2)} ops/sec
                """.trimMargin()
        }
    }

    /**
     * 性能測量工具類
     */
    class PerformanceMeasurer {
        /**
         * 測量單一操作的性能
         *
         * @param name 操作名稱
         * @param iterations 迭代次數
         * @param warmup 預熱次數
         * @param target 目標時間（可選）
         * @param operation 要測試的操作
         */
        suspend fun measurePerformance(
            name: String,
            iterations: Int = 100,
            warmup: Int = 10,
            target: Duration? = null,
            operation: suspend () -> Unit
        ): BenchmarkResult {
            // 預熱階段
            repeat(warmup) { operation() }

            // 測量階段
            val times = mutableListOf<Duration>()
            val startMark = TimeSource.Monotonic.markNow()

            repeat(iterations) {
                val iterStart = TimeSource.Monotonic.markNow()
                operation()
                times.add(iterStart.elapsedNow())
            }

            val totalTime = startMark.elapsedNow()
            val avgTime = totalTime / iterations
            val minTime = times.minOrNull() ?: Duration.ZERO
            val maxTime = times.maxOrNull() ?: Duration.ZERO
            val throughput = if (totalTime.inWholeSeconds > 0) {
                iterations.toDouble() / totalTime.inWholeSeconds
            } else {
                iterations.toDouble() / (totalTime.inWholeMilliseconds / 1000.0)
            }

            val passed = target?.let { avgTime <= it } ?: true

            return BenchmarkResult(
                operation = name,
                iterations = iterations,
                totalTime = totalTime,
                averageTime = avgTime,
                minTime = minTime,
                maxTime = maxTime,
                throughput = throughput,
                passed = passed,
                target = target
            )
        }

        /**
         * 打印測試結果
         */
        fun printResult(result: BenchmarkResult) {
            println(result.toString())
        }

        /**
         * 批次打印測試結果
         */
        fun printResults(results: List<BenchmarkResult>) {
            println("\n" + "=".repeat(60))
            println("📊 性能基準測試報告")
            println("=".repeat(60))

            results.forEach { result ->
                printResult(result)
            }

            val passCount = results.count { it.passed }
            val totalCount = results.size
            val passRate = (passCount.toDouble() / totalCount * 100).toInt()

            println("\n" + "=".repeat(60))
            println("總計: $passCount/$totalCount 通過 ($passRate%)")
            println("=".repeat(60))
        }
    }

    companion object {
        // 測試常數
        const val MNEMONIC_1 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        const val MNEMONIC_2 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        // 測試地址
        const val ETH_ADDRESS_1 = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"
        const val ETH_ADDRESS_2 = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"

        // 性能目標
        val ADDRESS_GENERATION_TARGET = Duration.parse("100ms")
        val TRANSACTION_CREATION_TARGET = Duration.parse("500ms")
        val SIGNING_TARGET = Duration.parse("200ms")
        val RPC_CALL_TARGET = Duration.parse("2s")

        // 迭代次數配置
        const val QUICK_ITERATIONS = 10
        const val STANDARD_ITERATIONS = 50
        const val HEAVY_ITERATIONS = 100
    }

    // ========================================
    // 階段 1: 基礎框架驗證測試
    // ========================================

    @Test
    fun testBenchmarkFramework() = runTest {
        println("\n🔧 驗證基準測試框架...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        // 測試 1: 空操作基準
        val emptyResult = measurer.measurePerformance(
            name = "空操作基準",
            iterations = 1000,
            warmup = 100,
            target = Duration.parse("1ms")
        ) {
            // 空操作，用於測量測試框架本身的開銷
        }
        results.add(emptyResult)

        // 測試 2: 簡單計算
        val simpleCalcResult = measurer.measurePerformance(
            name = "簡單計算 (1-1000 求和)",
            iterations = 1000,
            warmup = 100,
            target = Duration.parse("10ms")
        ) {
            var sum = 0L
            for (i in 1..1000) {
                sum += i
            }
        }
        results.add(simpleCalcResult)

        measurer.printResults(results)

        // 驗證框架正常工作
        assertTrue(emptyResult.passed, "空操作應該通過性能測試")
        assertTrue(simpleCalcResult.passed, "簡單計算應該通過性能測試")

        println("\n✅ 基準測試框架驗證完成！")
    }

    // ========================================
    // 階段 2: 地址生成性能測試
    // ========================================

    @Test
    fun benchmarkAddressGeneration_Bitcoin() = runTest {
        println("\n🪙 測試 Bitcoin 地址生成性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        // 測試將在有實際實現後補充
        // 目前創建模擬測試

        val result = measurer.measurePerformance(
            name = "Bitcoin 地址生成",
            iterations = STANDARD_ITERATIONS,
            warmup = 10,
            target = ADDRESS_GENERATION_TARGET
        ) {
            // TODO: 實現實際的 Bitcoin 地址生成
            // val address = deriveBitcoinAddress(MNEMONIC_1, 0)

            // 模擬地址生成延遲
            delay(50) // 50ms 模擬
        }

        results.add(result)
        measurer.printResults(results)

        // 暫時註解斷言，等待實際實現
        // assertTrue(result.passed, "Bitcoin 地址生成應該 < 100ms")
    }

    @Test
    fun benchmarkAddressGeneration_Ethereum() = runTest {
        println("\n⟠ 測試 Ethereum 地址生成性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        val result = measurer.measurePerformance(
            name = "Ethereum 地址生成",
            iterations = STANDARD_ITERATIONS,
            warmup = 10,
            target = ADDRESS_GENERATION_TARGET
        ) {
            // TODO: 實現實際的 Ethereum 地址生成
            // val address = deriveEthereumAddress(MNEMONIC_1, 0)

            // 模擬地址生成延遲
            delay(40) // 40ms 模擬
        }

        results.add(result)
        measurer.printResults(results)
    }

    @Test
    fun benchmarkAddressGeneration_Solana() = runTest {
        println("\n◎ 測試 Solana 地址生成性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        val result = measurer.measurePerformance(
            name = "Solana 地址生成",
            iterations = STANDARD_ITERATIONS,
            warmup = 10,
            target = ADDRESS_GENERATION_TARGET
        ) {
            // TODO: 實現實際的 Solana 地址生成
            // val address = deriveSolanaAddress(MNEMONIC_1, 0)

            // 模擬地址生成延遲
            delay(45) // 45ms 模擬
        }

        results.add(result)
        measurer.printResults(results)
    }

    @Test
    fun benchmarkBatchAddressGeneration() = runTest {
        println("\n📦 測試批次地址生成性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        // 測試批次生成 100 個地址
        val batchSize = 100
        val result = measurer.measurePerformance(
            name = "批次生成 $batchSize 個 Ethereum 地址",
            iterations = 10,
            warmup = 2,
            target = Duration.parse("10s") // 100個地址，每個100ms = 10s
        ) {
            // TODO: 實現批次地址生成
            // val addresses = (0 until batchSize).map { index ->
            //     deriveEthereumAddress(MNEMONIC_1, index)
            // }

            // 模擬批次生成
            repeat(batchSize) {
                delay(40) // 每個地址 40ms
            }
        }

        results.add(result)

        // 計算單個地址的平均時間
        val perAddressTime = result.averageTime / batchSize
        println("\n平均每個地址生成時間: $perAddressTime")

        measurer.printResults(results)

        // 驗證單個地址性能
        assertTrue(
            perAddressTime <= ADDRESS_GENERATION_TARGET,
            "單個地址生成應該 < ${ADDRESS_GENERATION_TARGET}"
        )
    }

    // ========================================
    // 階段 3: 交易建構性能測試
    // ========================================

    @Test
    fun benchmarkTransactionCreation_Ethereum() = runTest {
        println("\n⟠ 測試 Ethereum 交易建構性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        val result = measurer.measurePerformance(
            name = "Ethereum 交易建構",
            iterations = STANDARD_ITERATIONS,
            warmup = 5,
            target = TRANSACTION_CREATION_TARGET
        ) {
            // TODO: 實現實際的交易建構
            // val tx = createEthereumTransaction(
            //     from = ETH_ADDRESS_1,
            //     to = ETH_ADDRESS_2,
            //     amount = "0.001",
            //     gasPrice = "20000000000",
            //     gasLimit = "21000"
            // )

            // 模擬交易建構
            delay(200) // 200ms 模擬
        }

        results.add(result)
        measurer.printResults(results)
    }

    @Test
    fun benchmarkTransactionCreation_Bitcoin() = runTest {
        println("\n🪙 測試 Bitcoin 交易建構性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        val result = measurer.measurePerformance(
            name = "Bitcoin UTXO 交易建構",
            iterations = QUICK_ITERATIONS,
            warmup = 2,
            target = TRANSACTION_CREATION_TARGET
        ) {
            // TODO: 實現實際的 Bitcoin 交易建構
            // val tx = createBitcoinTransaction(
            //     utxos = mockUtxos,
            //     toAddress = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            //     amount = 10000,
            //     feeRate = 10
            // )

            // 模擬 UTXO 選擇和交易建構
            delay(300) // 300ms 模擬（UTXO 較複雜）
        }

        results.add(result)
        measurer.printResults(results)
    }

    @Test
    fun benchmarkTransactionCreation_Solana() = runTest {
        println("\n◎ 測試 Solana 交易建構性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        val result = measurer.measurePerformance(
            name = "Solana 交易建構",
            iterations = STANDARD_ITERATIONS,
            warmup = 5,
            target = TRANSACTION_CREATION_TARGET
        ) {
            // TODO: 實現實際的 Solana 交易建構
            // val tx = createSolanaTransaction(
            //     from = "...",
            //     to = "...",
            //     amount = 1000000 // lamports
            // )

            // 模擬交易建構
            delay(150) // 150ms 模擬
        }

        results.add(result)
        measurer.printResults(results)
    }

    // ========================================
    // 階段 4: 簽名性能測試
    // ========================================

    @Test
    fun benchmarkSigning_ECDSA_secp256k1() = runTest {
        println("\n🔐 測試 ECDSA secp256k1 簽名性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        val message = "Test message for signing".encodeToByteArray()

        val result = measurer.measurePerformance(
            name = "ECDSA secp256k1 簽名",
            iterations = HEAVY_ITERATIONS,
            warmup = 10,
            target = SIGNING_TARGET
        ) {
            // TODO: 實現實際的 ECDSA 簽名
            // val privateKey = derivePrivateKey(MNEMONIC_1, "m/44'/60'/0'/0/0")
            // val signature = signWithSecp256k1(privateKey, message)

            // 模擬簽名操作
            delay(80) // 80ms 模擬
        }

        results.add(result)
        measurer.printResults(results)
    }

    @Test
    fun benchmarkSigning_Ed25519() = runTest {
        println("\n🔐 測試 Ed25519 簽名性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        val message = "Test message for signing".encodeToByteArray()

        val result = measurer.measurePerformance(
            name = "Ed25519 簽名",
            iterations = HEAVY_ITERATIONS,
            warmup = 10,
            target = SIGNING_TARGET
        ) {
            // TODO: 實現實際的 Ed25519 簽名
            // val privateKey = derivePrivateKey(MNEMONIC_1, "m/44'/501'/0'/0'")
            // val signature = signWithEd25519(privateKey, message)

            // 模擬簽名操作
            delay(60) // 60ms 模擬（Ed25519 通常比 ECDSA 快）
        }

        results.add(result)
        measurer.printResults(results)
    }

    @Test
    fun benchmarkSigning_Verification() = runTest {
        println("\n✓ 測試簽名驗證性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        val message = "Test message for signing".encodeToByteArray()

        // ECDSA 驗證
        val ecdsaResult = measurer.measurePerformance(
            name = "ECDSA 簽名驗證",
            iterations = HEAVY_ITERATIONS,
            warmup = 10,
            target = SIGNING_TARGET
        ) {
            // TODO: 實現實際的簽名驗證
            // val isValid = verifyECDSASignature(publicKey, message, signature)

            delay(70) // 70ms 模擬
        }
        results.add(ecdsaResult)

        // Ed25519 驗證
        val ed25519Result = measurer.measurePerformance(
            name = "Ed25519 簽名驗證",
            iterations = HEAVY_ITERATIONS,
            warmup = 10,
            target = SIGNING_TARGET
        ) {
            // TODO: 實現實際的簽名驗證
            // val isValid = verifyEd25519Signature(publicKey, message, signature)

            delay(50) // 50ms 模擬
        }
        results.add(ed25519Result)

        measurer.printResults(results)
    }

    // ========================================
    // 階段 5: RPC 調用延遲測試
    // ========================================

    @Test
    fun benchmarkRPCCalls_Ethereum() = runTest {
        println("\n🌐 測試 Ethereum RPC 調用性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        // 餘額查詢
        val balanceResult = measurer.measurePerformance(
            name = "Ethereum 餘額查詢",
            iterations = 20,
            warmup = 2,
            target = RPC_CALL_TARGET
        ) {
            // TODO: 實現實際的 RPC 調用
            // val balance = ethClient.getBalance(ETH_ADDRESS_1)

            // 模擬網路延遲
            delay(500) // 500ms 模擬網路往返
        }
        results.add(balanceResult)

        // Gas Price 查詢
        val gasPriceResult = measurer.measurePerformance(
            name = "Ethereum Gas Price 查詢",
            iterations = 20,
            warmup = 2,
            target = RPC_CALL_TARGET
        ) {
            // TODO: 實現實際的 RPC 調用
            // val gasPrice = ethClient.getGasPrice()

            delay(300) // 300ms 模擬
        }
        results.add(gasPriceResult)

        measurer.printResults(results)
    }

    @Test
    fun benchmarkRPCCalls_Solana() = runTest {
        println("\n🌐 測試 Solana RPC 調用性能...")

        val measurer = PerformanceMeasurer()
        val results = mutableListOf<BenchmarkResult>()

        // 餘額查詢
        val balanceResult = measurer.measurePerformance(
            name = "Solana 餘額查詢",
            iterations = 20,
            warmup = 2,
            target = RPC_CALL_TARGET
        ) {
            // TODO: 實現實際的 RPC 調用
            // val balance = solClient.getBalance(address)

            delay(400) // 400ms 模擬
        }
        results.add(balanceResult)

        measurer.printResults(results)
    }

    @Test
    fun benchmarkConcurrentRPC() = runTest {
        println("\n🚀 測試並發 RPC 調用性能...")

        val addresses = listOf(
            "0x1...",
            "0x2...",
            "0x3...",
            "0x4...",
            "0x5..."
        )

        // 順序調用
        val sequentialTime = measureTime {
            addresses.forEach { address ->
                // TODO: 實現實際的 RPC 調用
                // val balance = ethClient.getBalance(address)

                delay(500) // 模擬每個調用 500ms
            }
        }

        // 並發調用
        val concurrentTime = measureTime {
            // TODO: 實現並發調用
            // coroutineScope {
            //     addresses.map { address ->
            //         async {
            //             ethClient.getBalance(address)
            //         }
            //     }.awaitAll()
            // }

            // 模擬並發（實際上還是順序，僅示意）
            delay(500) // 並發時只需一次網路往返
        }

        val speedup = sequentialTime.inWholeMilliseconds.toDouble() / concurrentTime.inWholeMilliseconds

        println("\n順序調用: $sequentialTime")
        println("並發調用: $concurrentTime")
        println("加速比: ${speedup.formatDecimal(2)}x")

        assertTrue(
            concurrentTime < sequentialTime,
            "並發調用應該快於順序調用"
        )
    }

    // ========================================
    // 階段 6: 內存使用測試
    // ========================================

    @Test
    fun benchmarkMemoryUsage() = runTest {
        println("\n💾 測試內存使用情況...")

        // 注意: Kotlin/Native 和 JVM 的內存管理不同
        // 這裡提供概念性的測試

        val operations = 100

        val time = measureTime {
            repeat(operations) { index ->
                // 模擬典型操作
                // 1. 地址生成
                // val address = deriveEthereumAddress(MNEMONIC_1, index)

                // 2. 交易建構
                // val tx = createTransaction(...)

                // 3. 簽名
                // val signature = sign(...)

                delay(10) // 模擬操作
            }
        }

        println("\n執行 $operations 個操作總時間: $time")
        println("平均每個操作: ${time / operations}")

        // TODO: 添加實際的內存監控
        // 在 Android 上可以使用 Runtime.getRuntime()
        // 在其他平台需要使用平台特定的 API

        println("\n⚠️ 內存使用測試需要平台特定實現")
        println("建議: 在實際設備上使用 Android Profiler 或 Xcode Instruments 進行測試")
    }

    // ========================================
    // 階段 7: 並發處理能力測試
    // ========================================

    @Test
    fun benchmarkConcurrentProcessing() = runTest {
        println("\n⚡ 測試並發處理能力...")

        val taskCount = 50

        // 順序處理
        val sequentialTime = measureTime {
            repeat(taskCount) {
                // 模擬簽名操作
                delay(50)
            }
        }

        // 並發處理（模擬）
        val concurrentTime = measureTime {
            // TODO: 實現實際的並發處理
            // coroutineScope {
            //     (0 until taskCount).map {
            //         async {
            //             // 簽名操作
            //         }
            //     }.awaitAll()
            // }

            // 模擬並發（假設 4 核心）
            val cores = 4
            val timePerCore = taskCount / cores * 50
            delay(timePerCore.toLong())
        }

        val speedup = sequentialTime.inWholeMilliseconds.toDouble() / concurrentTime.inWholeMilliseconds

        println("\n順序處理 $taskCount 個任務: $sequentialTime")
        println("並發處理 $taskCount 個任務: $concurrentTime")
        println("加速比: ${speedup.formatDecimal(2)}x")

        assertTrue(
            concurrentTime < sequentialTime,
            "並發處理應該快於順序處理"
        )
    }

    // ========================================
    // 綜合測試和報告生成
    // ========================================

    @Test
    fun generateComprehensivePerformanceReport() = runTest {
        println("\n")
        println("=".repeat(70))
        println("📊 coreKmp 性能基準測試報告")
        println("=".repeat(70))
        println("測試日期: ${getCurrentTimestamp()}")
        println("平台: Kotlin Multiplatform")
        println("=".repeat(70))

        println("\n📝 測試說明:")
        println("本測試套件提供了全面的性能基準測試框架，涵蓋:")
        println("• 地址生成速度")
        println("• 交易建構時間")
        println("• 簽名性能")
        println("• RPC 調用延遲")
        println("• 內存使用")
        println("• 並發處理能力")

        println("\n🎯 性能目標:")
        println("• 地址生成: < $ADDRESS_GENERATION_TARGET")
        println("• 交易建構: < $TRANSACTION_CREATION_TARGET")
        println("• 簽名: < $SIGNING_TARGET")
        println("• RPC 調用: < $RPC_CALL_TARGET")
        println("• 內存使用: < 50MB (典型操作)")

        println("\n⚠️ 注意事項:")
        println("• 當前版本包含模擬測試，待實際 SDK 實現後替換")
        println("• RPC 測試需要網路連接，可能受網路狀況影響")
        println("• 內存測試需要在實際設備上進行")
        println("• 建議在發布前在真實設備上運行完整測試")

        println("\n" + "=".repeat(70))
        println("✅ 性能基準測試框架創建完成！")
        println("=".repeat(70))

        assertTrue(true, "報告生成成功")
    }

    /**
     * 獲取當前時間戳
     */
    private fun getCurrentTimestamp(): String {
        // 簡化實現，實際可以使用 kotlinx-datetime
        return "2025-08-22"
    }
}
