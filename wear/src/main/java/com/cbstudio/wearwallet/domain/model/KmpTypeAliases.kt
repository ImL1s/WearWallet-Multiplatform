package com.cbstudio.wearwallet.domain.model

/**
 * KMP 類型別名
 * 
 * 為了兼容性，將 sharedKmp 的類型映射到 wear 模組
 */

// MAINTENANCE MODE: 暫時停用所有 KMP 類型別名
// 由於 sharedKmp 模組中的 models 包結構問題，暫時停用類型別名
// TODO: 等待 sharedKmp 模組架構穩定後重新啟用

// 簡化的類型定義
enum class ChainType { ETHEREUM, POLYGON, BSC, CRONOS, UNKNOWN }
data class Token(val symbol: String, val name: String, val address: String)
data class Transaction(val hash: String, val status: String)
data class Network(val name: String, val chainId: Int)

// 提醒：不要將 Chain 別名到不同來源，避免衝突
// 如需鏈資訊，請使用 ChainType 或具體 Chain 定義

// 常見的未解析類型別名 - MAINTENANCE MODE
// 這些類型暫時指向簡化實現，避免編譯錯誤
interface NotificationHistory { val id: String; val message: String }
// RiskLevel 在其他檔案已定義，這裡只提供引用
// enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL } // 已移除重複宣告
enum class ChallengeType { VOICE, BIOMETRIC, PIN, PATTERN }

// MAINTENANCE MODE: SpendingStats 和相關類型定義
data class SpendingStats(
    val period: SpendingPeriod,
    val totalSpent: String,
    val transactionCount: Int,
    val categorizedSpending: Map<String, String>,
    val averageTransaction: String
)

enum class SpendingPeriod {
    DAILY, WEEKLY, MONTHLY
}
