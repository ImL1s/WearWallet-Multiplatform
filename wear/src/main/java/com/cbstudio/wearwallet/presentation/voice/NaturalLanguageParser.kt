package com.cbstudio.wearwallet.presentation.voice

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton

/**
 * 自然語言解析器
 * 用於解析 Gemini/Google Assistant 的語音命令
 */
@Singleton
class NaturalLanguageParser constructor() {
    
    /**
     * 解析交易命令
     * 支援多種自然語言格式
     */
    fun parseTransactionCommand(command: String): TransactionIntent? {
        val normalizedCommand = command.lowercase().trim()
        
        // 定義多種命令模式
        val patterns = listOf(
            // "send 0.1 ETH to John"
            Regex("send\\s+(\\d+\\.?\\d*)\\s+(\\w+)\\s+to\\s+(.+)"),
            // "transfer 50 USDT to 0x123..."
            Regex("transfer\\s+(\\d+\\.?\\d*)\\s+(\\w+)\\s+to\\s+(.+)"),
            // "pay John 100 USDC"
            Regex("pay\\s+(.+?)\\s+(\\d+\\.?\\d*)\\s+(\\w+)"),
            // "給 John 發送 0.5 ETH" (中文)
            Regex("給\\s*(.+?)\\s*發送\\s*(\\d+\\.?\\d*)\\s*(\\w+)"),
            // "發送 0.5 ETH 給 John" (中文)
            Regex("發送\\s*(\\d+\\.?\\d*)\\s*(\\w+)\\s*給\\s*(.+)"),
            // "0.1 ETH to John" (簡化格式)
            Regex("(\\d+\\.?\\d*)\\s+(\\w+)\\s+to\\s+(.+)"),
        )
        
        for (pattern in patterns) {
            pattern.find(normalizedCommand)?.let { match ->
                return extractTransactionIntent(match.groupValues)
            }
        }
        
        // 嘗試更寬鬆的解析
        return parseLooseCommand(normalizedCommand)
    }
    
    /**
     * 解析餘額查詢命令
     */
    fun parseBalanceCommand(command: String): BalanceIntent? {
        val normalizedCommand = command.lowercase().trim()
        
        val patterns = listOf(
            // "check ETH balance"
            Regex("check\\s+(\\w+)\\s+balance"),
            // "balance of USDT"
            Regex("balance\\s+of\\s+(\\w+)"),
            // "how much ETH"
            Regex("how\\s+much\\s+(\\w+)"),
            // "ETH 餘額" (中文)
            Regex("(\\w+)\\s*餘額"),
            // "查詢 ETH" (中文)
            Regex("查詢\\s*(\\w+)"),
        )
        
        for (pattern in patterns) {
            pattern.find(normalizedCommand)?.let { match ->
                return BalanceIntent(
                    currency = match.groupValues[1].uppercase()
                )
            }
        }
        
        // 如果沒有指定幣種，返回查詢所有餘額
        if (normalizedCommand.contains("balance") || normalizedCommand.contains("餘額")) {
            return BalanceIntent(currency = null)
        }
        
        return null
    }
    
    /**
     * 解析 AI 助手命令
     */
    fun parseAICommand(command: String): AIIntent? {
        val queries = listOf(
            "what is gas fee",
            "how to stake",
            "market analysis",
            "price prediction",
            "什麼是 gas",
            "如何質押",
            "市場分析",
            "價格預測"
        )
        
        for (query in queries) {
            if (command.lowercase().contains(query)) {
                return AIIntent(query = command)
            }
        }
        
        // 通用 AI 查詢
        if (command.contains("ask") || command.contains("tell me") || 
            command.contains("詢問") || command.contains("告訴我")) {
            return AIIntent(query = command)
        }
        
        return null
    }
    
    /**
     * 寬鬆解析 - 嘗試提取關鍵信息
     */
    private fun parseLooseCommand(command: String): TransactionIntent? {
        var amount: String? = null
        var currency: String? = null
        var recipient: String? = null
        
        // 提取數字作為金額
        Regex("(\\d+\\.?\\d*)").find(command)?.let {
            amount = it.value
        }
        
        // 提取已知的幣種
        val knownCurrencies = listOf("ETH", "BTC", "USDT", "USDC", "BNB", "MATIC")
        for (curr in knownCurrencies) {
            if (command.uppercase().contains(curr)) {
                currency = curr
                break
            }
        }
        
        // 提取可能的收款人（地址或名字）
        // 0x 開頭的地址
        Regex("0x[a-fA-F0-9]{40}").find(command)?.let {
            recipient = it.value
        }
        
        // 如果沒有地址，嘗試提取 "to" 或 "給" 後面的內容
        if (recipient == null) {
            Regex("(?:to|給)\\s+([^\\s]+)").find(command)?.let {
                recipient = it.groupValues[1]
            }
        }
        
        // 如果有足夠的信息，創建 intent
        return if (amount != null || currency != null || recipient != null) {
            TransactionIntent(
                amount = amount ?: "",
                currency = currency ?: "ETH",
                recipient = recipient ?: ""
            )
        } else {
            null
        }
    }
    
    /**
     * 從正則匹配結果中提取交易意圖
     */
    private fun extractTransactionIntent(groups: List<String>): TransactionIntent {
        // 根據不同的模式，參數位置可能不同
        return when {
            // "send X TOKEN to RECIPIENT" 格式
            groups.size >= 4 && groups[1].matches(Regex("\\d+\\.?\\d*")) -> {
                TransactionIntent(
                    amount = groups[1],
                    currency = groups[2].uppercase(),
                    recipient = groups[3].trim()
                )
            }
            // "pay RECIPIENT X TOKEN" 格式
            groups.size >= 4 && groups[2].matches(Regex("\\d+\\.?\\d*")) -> {
                TransactionIntent(
                    amount = groups[2],
                    currency = groups[3].uppercase(),
                    recipient = groups[1].trim()
                )
            }
            else -> {
                TransactionIntent("", "ETH", "")
            }
        }
    }
    
    /**
     * 解析錢包切換命令
     */
    fun parseWalletCommand(command: String): WalletIntent? {
        val patterns = listOf(
            Regex("switch to (.+) wallet"),
            Regex("use (.+) wallet"),
            Regex("切換到(.+)錢包"),
            Regex("使用(.+)錢包")
        )
        
        for (pattern in patterns) {
            pattern.find(command.lowercase())?.let { match ->
                return WalletIntent(
                    walletName = match.groupValues[1].trim()
                )
            }
        }
        
        return null
    }
}

// 數據類定義
data class TransactionIntent(
    val amount: String,
    val currency: String,
    val recipient: String,
    val network: String? = null
)

data class BalanceIntent(
    val currency: String? // null 表示查詢所有
)

data class AIIntent(
    val query: String
)

data class WalletIntent(
    val walletName: String
)
