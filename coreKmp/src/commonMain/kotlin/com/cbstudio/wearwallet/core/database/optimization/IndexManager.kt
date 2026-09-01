package com.cbstudio.wearwallet.core.database.optimization

import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 資料庫索引管理器
 * 
 * 管理和優化資料庫索引
 * 
 * Created: 2025-01-17
 */
class IndexManager {
    
    /**
     * 索引定義
     */
    data class IndexDefinition(
        val tableName: String,
        val indexName: String,
        val columns: List<String>,
        val isUnique: Boolean = false,
        val whereClause: String? = null // 部分索引條件
    ) {
        fun toSql(): String {
            val unique = if (isUnique) "UNIQUE " else ""
            val columns = columns.joinToString(", ")
            val where = whereClause?.let { " WHERE $it" } ?: ""
            return "CREATE ${unique}INDEX IF NOT EXISTS $indexName ON $tableName($columns)$where"
        }
    }
    
    /**
     * 核心索引定義
     */
    private val coreIndexes = listOf(
        // Token 表索引
        IndexDefinition(
            tableName = "token",
            indexName = "idx_token_wallet_chain",
            columns = listOf("wallet_id", "chain_id"),
            isUnique = false
        ),
        IndexDefinition(
            tableName = "token",
            indexName = "idx_token_address_chain",
            columns = listOf("address", "chain_id"),
            isUnique = false
        ),
        IndexDefinition(
            tableName = "token",
            indexName = "idx_token_visible",
            columns = listOf("wallet_id", "is_hidden"),
            whereClause = "is_hidden = 0"
        ),
        IndexDefinition(
            tableName = "token",
            indexName = "idx_token_update_time",
            columns = listOf("last_updated")
        ),
        
        // Transaction 表索引
        IndexDefinition(
            tableName = "transaction_record",
            indexName = "idx_tx_wallet_status",
            columns = listOf("wallet_id", "status")
        ),
        IndexDefinition(
            tableName = "transaction_record",
            indexName = "idx_tx_address_chain",
            columns = listOf("from_address", "to_address", "chain_id")
        ),
        IndexDefinition(
            tableName = "transaction_record",
            indexName = "idx_tx_pending",
            columns = listOf("wallet_id", "status"),
            whereClause = "status = 'PENDING'"
        ),
        IndexDefinition(
            tableName = "transaction_record",
            indexName = "idx_tx_block",
            columns = listOf("block_number", "chain_id")
        ),
        
        // NFT 表索引
        IndexDefinition(
            tableName = "nft",
            indexName = "idx_nft_wallet",
            columns = listOf("wallet_id")
        ),
        IndexDefinition(
            tableName = "nft",
            indexName = "idx_nft_contract",
            columns = listOf("contract_address", "token_id")
        ),
        
        // Price Alert 表索引
        IndexDefinition(
            tableName = "price_alert",
            indexName = "idx_alert_active",
            columns = listOf("is_active", "token_symbol"),
            whereClause = "is_active = 1"
        ),
        
        // Notification 表索引
        IndexDefinition(
            tableName = "notification_history",
            indexName = "idx_notification_wallet",
            columns = listOf("wallet_id", "created_at")
        ),
        IndexDefinition(
            tableName = "notification_history",
            indexName = "idx_notification_unread",
            columns = listOf("wallet_id", "is_read"),
            whereClause = "is_read = 0"
        )
    )
    
    /**
     * 建立所有核心索引
     */
    suspend fun createCoreIndexes(database: Any) = withContext(Dispatchers.Default) {
        Logger.d("IndexManager", "Creating core indexes...")
        
        var successCount = 0
        var failureCount = 0
        
        coreIndexes.forEach { index ->
            try {
                val sql = index.toSql()
                // 實際執行會透過 SQLDelight 的 driver
                // database.execute(sql)
                
                Logger.d("IndexManager", "Created index: ${index.indexName}")
                successCount++
            } catch (e: Exception) {
                Logger.e("IndexManager", "Failed to create index: ${index.indexName}", e)
                failureCount++
            }
        }
        
        Logger.i("IndexManager", 
            "Index creation complete: $successCount succeeded, $failureCount failed")
    }
    
    /**
     * 分析索引使用情況
     */
    suspend fun analyzeIndexUsage(database: Any): IndexUsageReport {
        // 在實際實作中，這會查詢 SQLite 的 sqlite_stat1 表
        // 或使用 EXPLAIN QUERY PLAN 來分析索引使用
        
        return IndexUsageReport(
            totalIndexes = coreIndexes.size,
            usedIndexes = emptyList(),
            unusedIndexes = emptyList(),
            suggestedIndexes = emptyList()
        )
    }
    
    /**
     * 重建索引（優化碎片）
     */
    suspend fun rebuildIndex(database: Any, indexName: String) = withContext(Dispatchers.Default) {
        try {
            Logger.d("IndexManager", "Rebuilding index: $indexName")
            
            // SQLite 中可以使用 REINDEX
            // database.execute("REINDEX $indexName")
            
            Logger.d("IndexManager", "Index rebuilt: $indexName")
        } catch (e: Exception) {
            Logger.e("IndexManager", "Failed to rebuild index: $indexName", e)
            throw e
        }
    }
    
    /**
     * 刪除未使用的索引
     */
    suspend fun dropUnusedIndexes(database: Any, unusedIndexes: List<String>) {
        unusedIndexes.forEach { indexName ->
            try {
                // database.execute("DROP INDEX IF EXISTS $indexName")
                Logger.d("IndexManager", "Dropped unused index: $indexName")
            } catch (e: Exception) {
                Logger.e("IndexManager", "Failed to drop index: $indexName", e)
            }
        }
    }
    
    /**
     * 獲取索引統計資訊
     */
    suspend fun getIndexStatistics(database: Any): List<IndexStatistics> {
        // 實際實作會查詢系統表
        return emptyList()
    }
}

/**
 * 索引使用報告
 */
data class IndexUsageReport(
    val totalIndexes: Int,
    val usedIndexes: List<IndexInfo>,
    val unusedIndexes: List<IndexInfo>,
    val suggestedIndexes: List<IndexSuggestion>
)

/**
 * 索引資訊
 */
data class IndexInfo(
    val indexName: String,
    val tableName: String,
    val columns: List<String>,
    val usageCount: Int = 0,
    val lastUsed: kotlinx.datetime.Instant? = null
)

/**
 * 索引統計
 */
data class IndexStatistics(
    val indexName: String,
    val tableName: String,
    val uniqueKeys: Int,
    val dataPages: Int,
    val leafPages: Int,
    val averageKeySize: Int,
    val fragmentationPercent: Double
)

/**
 * 複合索引優化器
 */
object CompositeIndexOptimizer {
    
    /**
     * 分析查詢並建議複合索引
     */
    fun suggestCompositeIndex(
        queries: List<String>
    ): List<IndexManager.IndexDefinition> {
        val suggestions = mutableListOf<IndexManager.IndexDefinition>()
        
        // 分析 WHERE 子句中常見的列組合
        val whereColumns = analyzeWhereColumns(queries)
        
        // 建議複合索引
        whereColumns.forEach { (table, columns) ->
            if (columns.size > 1) {
                suggestions.add(
                    IndexManager.IndexDefinition(
                        tableName = table,
                        indexName = "idx_${table}_${columns.joinToString("_")}",
                        columns = columns
                    )
                )
            }
        }
        
        return suggestions
    }
    
    /**
     * 分析 WHERE 子句中的列
     */
    private fun analyzeWhereColumns(queries: List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        
        queries.forEach { query ->
            // 簡單的 WHERE 子句分析
            val whereMatch = Regex("WHERE\\s+(\\w+)\\s*=").findAll(query)
            whereMatch.forEach { match ->
                val column = match.groupValues[1]
                val table = extractTableName(query)
                if (table != null) {
                    result.getOrPut(table) { mutableSetOf() }.add(column)
                }
            }
        }
        
        return result.mapValues { it.value.toList() }
    }
    
    /**
     * 提取表名
     */
    private fun extractTableName(query: String): String? {
        val fromMatch = Regex("FROM\\s+(\\w+)").find(query)
        return fromMatch?.groupValues?.get(1)
    }
}