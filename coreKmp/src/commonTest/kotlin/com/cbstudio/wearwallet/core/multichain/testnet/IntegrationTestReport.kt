package com.cbstudio.wearwallet.core.multichain.testnet

import kotlinx.datetime.Clock

/**
 * 跨平台 Double format 擴展函數
 */
private fun Double.formatDecimal(decimals: Int): String {
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
 * 集成測試報告生成器
 * 用於記錄和生成 Markdown 格式的測試報告
 */
class IntegrationTestReport {

    /**
     * 測試結果數據類
     */
    data class TestResult(
        val chain: String,
        val operation: String,
        val success: Boolean,
        val details: String,
        val txHash: String? = null,
        val error: String? = null,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    )

    /**
     * 地址信息數據類
     */
    data class AddressInfo(
        val chain: String,
        val wallet: String,
        val address: String,
        val explorerUrl: String
    )

    private val results = mutableListOf<TestResult>()
    private val addresses = mutableListOf<AddressInfo>()
    private val transactions = mutableListOf<TransactionRecord>()

    data class TransactionRecord(
        val chain: String,
        val txHash: String,
        val fromAddress: String,
        val toAddress: String,
        val amount: String,
        val status: String,
        val explorerUrl: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    )

    /**
     * 添加測試結果
     */
    fun addResult(
        chain: String,
        operation: String,
        success: Boolean,
        details: String,
        txHash: String? = null,
        error: String? = null
    ) {
        results.add(
            TestResult(
                chain = chain,
                operation = operation,
                success = success,
                details = details,
                txHash = txHash,
                error = error
            )
        )
    }

    /**
     * 添加地址信息
     */
    fun addAddress(chain: String, wallet: String, address: String, explorerUrl: String) {
        addresses.add(AddressInfo(chain, wallet, address, explorerUrl))
    }

    /**
     * 添加交易記錄
     */
    fun addTransaction(
        chain: String,
        txHash: String,
        fromAddress: String,
        toAddress: String,
        amount: String,
        status: String,
        explorerUrl: String
    ) {
        transactions.add(
            TransactionRecord(
                chain = chain,
                txHash = txHash,
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount,
                status = status,
                explorerUrl = explorerUrl
            )
        )
    }

    /**
     * 生成 Markdown 格式報告
     */
    fun generateMarkdown(): String {
        val sb = StringBuilder()

        // 標題和總覽
        sb.appendLine("# WearWallet CoreKmp Testnet 集成測試報告")
        sb.appendLine()
        sb.appendLine("**生成時間**: ${formatTimestamp(Clock.System.now().toEpochMilliseconds())}")
        sb.appendLine()

        // 統計總結
        val totalTests = results.size
        val passedTests = results.count { it.success }
        val failedTests = totalTests - passedTests
        val successRate = if (totalTests > 0) (passedTests * 100.0 / totalTests) else 0.0

        sb.appendLine("## 📊 測試總結")
        sb.appendLine()
        sb.appendLine("| 指標 | 數值 |")
        sb.appendLine("|------|------|")
        sb.appendLine("| 總測試數 | $totalTests |")
        sb.appendLine("| ✅ 通過 | $passedTests |")
        sb.appendLine("| ❌ 失敗 | $failedTests |")
        sb.appendLine("| 成功率 | ${successRate.formatDecimal(2)}% |")
        sb.appendLine()

        // 按鏈分組的結果
        sb.appendLine("## 🔗 各鏈測試結果")
        sb.appendLine()

        val resultsByChain = results.groupBy { it.chain }
        resultsByChain.forEach { (chain, chainResults) ->
            val chainPassed = chainResults.count { it.success }
            val chainTotal = chainResults.size
            val chainIcon = if (chainPassed == chainTotal) "✅" else "⚠️"

            sb.appendLine("### $chainIcon $chain ($chainPassed/$chainTotal)")
            sb.appendLine()
            sb.appendLine("| 操作 | 狀態 | 詳情 | 交易哈希 |")
            sb.appendLine("|------|------|------|----------|")

            chainResults.forEach { result ->
                val statusIcon = if (result.success) "✅" else "❌"
                val txHashDisplay = result.txHash?.take(16)?.let { "$it..." } ?: "N/A"
                val details = result.details.take(50).let { if (it.length < result.details.length) "$it..." else it }
                sb.appendLine("| ${result.operation} | $statusIcon | $details | `$txHashDisplay` |")
            }
            sb.appendLine()
        }

        // 地址清單
        if (addresses.isNotEmpty()) {
            sb.appendLine("## 📍 測試錢包地址")
            sb.appendLine()

            val addressesByWallet = addresses.groupBy { it.wallet }
            addressesByWallet.forEach { (wallet, walletAddresses) ->
                sb.appendLine("### $wallet")
                sb.appendLine()
                sb.appendLine("| 鏈 | 地址 | 區塊瀏覽器 |")
                sb.appendLine("|----|------|-----------|")

                walletAddresses.forEach { addr ->
                    val shortAddr = "${addr.address.take(8)}...${addr.address.takeLast(6)}"
                    sb.appendLine("| ${addr.chain} | `${addr.address}` | [查看](${addr.explorerUrl}/address/${addr.address}) |")
                }
                sb.appendLine()
            }
        }

        // 交易記錄
        if (transactions.isNotEmpty()) {
            sb.appendLine("## 📝 交易記錄")
            sb.appendLine()
            sb.appendLine("| 鏈 | 交易哈希 | 金額 | 狀態 | 區塊瀏覽器 |")
            sb.appendLine("|----|---------|------|------|-----------|")

            transactions.forEach { tx ->
                val shortHash = "${tx.txHash.take(8)}...${tx.txHash.takeLast(6)}"
                val statusIcon = when (tx.status) {
                    "SUCCESS", "CONFIRMED" -> "✅"
                    "PENDING" -> "⏳"
                    "FAILED" -> "❌"
                    else -> "❓"
                }
                sb.appendLine("| ${tx.chain} | `$shortHash` | ${tx.amount} | $statusIcon ${tx.status} | [查看](${tx.explorerUrl}) |")
            }
            sb.appendLine()
        }

        // 失敗詳情
        val failures = results.filter { !it.success }
        if (failures.isNotEmpty()) {
            sb.appendLine("## ⚠️ 失敗詳情")
            sb.appendLine()

            failures.forEach { failure ->
                sb.appendLine("### ${failure.chain} - ${failure.operation}")
                sb.appendLine("**錯誤信息**: ${failure.error ?: "未知錯誤"}")
                sb.appendLine("**詳情**: ${failure.details}")
                sb.appendLine()
            }
        }

        // Faucet 連結
        sb.appendLine("## 💧 Testnet Faucets")
        sb.appendLine()
        sb.appendLine("| 網絡 | Faucet 連結 |")
        sb.appendLine("|------|-------------|")
        sb.appendLine("| Ethereum Sepolia | ${TestnetConfig.Ethereum.FAUCET_URL} |")
        sb.appendLine("| Solana Devnet | `${TestnetConfig.Solana.FAUCET_COMMAND}` |")
        sb.appendLine("| TRON Shasta | ${TestnetConfig.Tron.FAUCET_URL} |")
        sb.appendLine("| Cardano Preprod | ${TestnetConfig.Cardano.FAUCET_URL} |")
        sb.appendLine("| Polkadot Westend | ${TestnetConfig.Polkadot.FAUCET_URL} |")
        sb.appendLine()

        // 結論
        sb.appendLine("## 📌 結論")
        sb.appendLine()
        if (successRate == 100.0) {
            sb.appendLine("✅ **所有測試均通過！** CoreKmp 模塊的多鏈功能在 testnet 環境下運行正常。")
        } else if (successRate >= 80.0) {
            sb.appendLine("⚠️ **大部分測試通過**，但存在一些問題需要解決。請查看上方的失敗詳情。")
        } else {
            sb.appendLine("❌ **測試失敗率較高**，需要進一步調查和修復。請優先處理失敗的測試案例。")
        }
        sb.appendLine()

        sb.appendLine("---")
        sb.appendLine("*此報告由 WearWallet CoreKmp 自動生成*")

        return sb.toString()
    }

    /**
     * 生成簡短總結
     */
    fun generateSummary(): String {
        val totalTests = results.size
        val passedTests = results.count { it.success }
        val successRate = if (totalTests > 0) (passedTests * 100.0 / totalTests) else 0.0

        return """
            ╔════════════════════════════════════════╗
            ║   Testnet 集成測試總結                 ║
            ╠════════════════════════════════════════╣
            ║ 總測試數: $totalTests
            ║ 通過: $passedTests
            ║ 失敗: ${totalTests - passedTests}
            ║ 成功率: ${successRate.formatDecimal(2)}%
            ╚════════════════════════════════════════╝
        """.trimIndent()
    }

    /**
     * 格式化時間戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        // 簡化實現，實際應使用 kotlinx-datetime 格式化
        return "$timestamp"
    }

    /**
     * 保存到文件（測試環境用）
     */
    fun saveToConsole() {
        println(generateMarkdown())
    }

    /**
     * 清空報告
     */
    fun clear() {
        results.clear()
        addresses.clear()
        transactions.clear()
    }
}
