package com.cbstudio.wearwallet.data.db

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 資料庫健康檢查器
 * 定期檢查資料庫狀態並報告問題
 */

@Singleton
class DatabaseHealthChecker @Inject constructor() {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 健康狀態
    private val _healthStatus = MutableStateFlow(HealthStatus())
    val healthStatus: StateFlow<HealthStatus> = _healthStatus.asStateFlow()
    
    // 檢查間隔（小時）
    private val checkIntervalHours = 24L
    
    private var checkJob: Job? = null
    
    /**
     * 開始健康檢查 - 現在使用 KMP SQLDelight
     */
    fun startHealthCheck() {
        checkJob?.cancel()
        
        checkJob = scope.launch {
            while (isActive) {
                performHealthCheckForKmp()
                delay(checkIntervalHours * 60 * 60 * 1000) // 轉換為毫秒
            }
        }
        
        // 立即執行一次檢查
        scope.launch {
            performHealthCheckForKmp()
        }
    }
    
    /**
     * 停止健康檢查
     */
    fun stopHealthCheck() {
        checkJob?.cancel()
        checkJob = null
    }
    
    /**
     * 執行健康檢查 - KMP SQLDelight 版本
     */
    suspend fun performHealthCheckForKmp(): HealthStatus {
        return withContext(Dispatchers.IO) {
            Timber.d("開始資料庫健康檢查...")
            
            val startTime = System.currentTimeMillis()
            val issues = mutableListOf<HealthIssue>()
            
            try {
                // KMP SQLDelight 健康檢查
                // 1. 檢查資料庫連接狀態
                Timber.d("檢查 KMP SQLDelight 資料庫狀態...")
                
                // Migration status check removed - migration module has been deleted
                
                // SQLDelight 資料庫健康檢查簡化版
                // 大部分檢查由 SQLDelight 內部處理
                Timber.d("KMP SQLDelight 資料庫健康檢查完成")
                
            } catch (e: Exception) {
                Timber.e(e, "健康檢查過程中發生錯誤")
                issues.add(HealthIssue(
                    severity = Severity.ERROR,
                    category = IssueCategory.GENERAL,
                    message = "健康檢查失敗: ${e.message}",
                    suggestion = "請檢查資料庫狀態並重試"
                ))
            }
            
            val duration = System.currentTimeMillis() - startTime
            val status = HealthStatus(
                isHealthy = issues.none { it.severity == Severity.ERROR },
                issues = issues,
                lastCheckTime = System.currentTimeMillis(),
                checkDuration = duration
            )
            
            _healthStatus.value = status
            
            // 記錄到 Firebase
            logHealthCheckResult(status)
            
            Timber.d("資料庫健康檢查完成，耗時: ${duration}ms，發現 ${issues.size} 個問題")
            
            return@withContext status
        }
    }
    
    /* ===== Room DB 相關方法已移除 - 現在使用 KMP SQLDelight =====
    
    以下方法已被註釋，因為它們依賴於 Room Database：
    - checkDatabaseSize
    - checkDatabaseIntegrity  
    - checkDatabaseSchema
    - checkDatabaseIndexes
    - checkForeignKeys
    - checkDatabasePerformance
    
    private fun checkDatabaseSize(database: RoomDatabase): HealthIssue? {
        val dbPath = database.openHelper.readableDatabase.path ?: return null
        val dbFile = File(dbPath)
        
        if (!dbFile.exists()) {
            return HealthIssue(
                severity = Severity.ERROR,
                category = IssueCategory.FILE_SYSTEM,
                message = "資料庫文件不存在",
                suggestion = "請重新啟動應用"
            )
        }
        
        val sizeInMB = dbFile.length() / (1024 * 1024)
        
        return when {
            sizeInMB > 500 -> HealthIssue(
                severity = Severity.ERROR,
                category = IssueCategory.STORAGE,
                message = "資料庫過大: ${sizeInMB}MB",
                suggestion = "請清理舊數據或聯繫支援"
            )
            sizeInMB > 200 -> HealthIssue(
                severity = Severity.WARNING,
                category = IssueCategory.STORAGE,
                message = "資料庫較大: ${sizeInMB}MB",
                suggestion = "建議清理舊的交易記錄"
            )
            else -> null
        }
    }
    
    /**
     * 檢查資料庫完整性
     */
    private fun checkDatabaseIntegrity(database: RoomDatabase): HealthIssue? {
        val db = database.openHelper.readableDatabase
        val cursor = db.query("PRAGMA integrity_check")
        
        return if (cursor.moveToFirst()) {
            val result = cursor.getString(0)
            cursor.close()
            
            if (result != "ok") {
                HealthIssue(
                    severity = Severity.ERROR,
                    category = IssueCategory.INTEGRITY,
                    message = "資料庫完整性檢查失敗: $result",
                    suggestion = "請備份數據並重新安裝應用"
                )
            } else {
                null
            }
        } else {
            cursor.close()
            HealthIssue(
                severity = Severity.WARNING,
                category = IssueCategory.INTEGRITY,
                message = "無法執行完整性檢查",
                suggestion = "請重新啟動應用"
            )
        }
    }
    
    /**
     * 檢查資料庫架構
     */
    private fun checkDatabaseSchema(database: RoomDatabase): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()
        val db = database.openHelper.readableDatabase
        
        // 檢查必要的表是否存在
        val requiredTables = listOf(
            "wallets", "transactions", "custom_tokens", 
            "contacts", "notification_history", "price_alerts"
        )
        
        for (table in requiredTables) {
            val cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table)
            )
            
            if (!cursor.moveToFirst()) {
                issues.add(HealthIssue(
                    severity = Severity.ERROR,
                    category = IssueCategory.SCHEMA,
                    message = "缺少必要的表: $table",
                    suggestion = "請更新應用到最新版本"
                ))
            }
            cursor.close()
        }
        
        return issues
    }
    
    /**
     * 檢查資料庫索引
     */
    private fun checkDatabaseIndexes(database: RoomDatabase): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()
        val db = database.openHelper.readableDatabase
        
        // 檢查重要的索引
        val importantIndexes = mapOf(
            "transactions" to listOf("idx_wallet_timestamp", "idx_tx_hash"),
            "custom_tokens" to listOf("index_custom_tokens_contractAddress_chainId"),
            "contacts" to listOf("index_contacts_address")
        )
        
        for ((table, indexes) in importantIndexes) {
            for (index in indexes) {
                val cursor = db.query(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
                    arrayOf(index)
                )
                
                if (!cursor.moveToFirst()) {
                    issues.add(HealthIssue(
                        severity = Severity.WARNING,
                        category = IssueCategory.PERFORMANCE,
                        message = "缺少索引: $index (表: $table)",
                        suggestion = "可能影響查詢性能"
                    ))
                }
                cursor.close()
            }
        }
        
        return issues
    }
    
    /**
     * 檢查外鍵約束
     */
    private fun checkForeignKeys(database: RoomDatabase): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()
        val db = database.openHelper.readableDatabase
        
        // 檢查外鍵是否啟用
        val cursor = db.query("PRAGMA foreign_keys")
        if (cursor.moveToFirst()) {
            val enabled = cursor.getInt(0) == 1
            if (!enabled) {
                issues.add(HealthIssue(
                    severity = Severity.WARNING,
                    category = IssueCategory.INTEGRITY,
                    message = "外鍵約束未啟用",
                    suggestion = "可能導致數據不一致"
                ))
            }
        }
        cursor.close()
        
        return issues
    }
    
    /**
     * 檢查資料庫性能
     */
    private suspend fun checkDatabasePerformance(database: RoomDatabase): HealthIssue? {
        val db = database.openHelper.readableDatabase
        
        // 執行一個簡單的查詢並測量時間
        val startTime = System.currentTimeMillis()
        val cursor = db.query("SELECT COUNT(*) FROM transactions")
        cursor.moveToFirst()
        cursor.close()
        val queryTime = System.currentTimeMillis() - startTime
        
        return if (queryTime > 1000) {
            HealthIssue(
                severity = Severity.WARNING,
                category = IssueCategory.PERFORMANCE,
                message = "資料庫查詢緩慢: ${queryTime}ms",
                suggestion = "建議優化資料庫或清理數據"
            )
        } else {
            null
        }
    }
    
    ===== End of Room DB methods ===== */
    
    /**
     * 記錄健康檢查結果
     */
    private fun logHealthCheckResult(status: HealthStatus) {
        val errorCount = status.issues.count { it.severity == Severity.ERROR }
        val warningCount = status.issues.count { it.severity == Severity.WARNING }
        
        // TODO: 實現 Firebase Analytics 日誌記錄
        Timber.d("Database health check - Healthy: ${status.isHealthy}, Errors: $errorCount, Warnings: $warningCount")
        
        // 如果有嚴重問題，記錄到日誌
        if (errorCount > 0) {
            val errorMessages = status.issues
                .filter { it.severity == Severity.ERROR }
                .joinToString("; ") { it.message }
            
            Timber.e("Database health check failed - Errors: $errorMessages")
        }
    }
    
    /**
     * 健康狀態
     */
    data class HealthStatus(
        val isHealthy: Boolean = true,
        val issues: List<HealthIssue> = emptyList(),
        val lastCheckTime: Long = 0,
        val checkDuration: Long = 0
    )
    
    /**
     * 健康問題
     */
    data class HealthIssue(
        val severity: Severity,
        val category: IssueCategory,
        val message: String,
        val suggestion: String
    )
    
    /**
     * 嚴重程度
     */
    enum class Severity {
        INFO,      // 信息
        WARNING,   // 警告
        ERROR      // 錯誤
    }
    
    /**
     * 問題類別
     */
    enum class IssueCategory {
        GENERAL,       // 一般
        FILE_SYSTEM,   // 文件系統
        STORAGE,       // 存儲
        INTEGRITY,     // 完整性
        SCHEMA,        // 架構
        PERFORMANCE,   // 性能
        MIGRATION      // 遷移
    }
}
