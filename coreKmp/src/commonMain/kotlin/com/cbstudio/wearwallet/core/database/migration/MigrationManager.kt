package com.cbstudio.wearwallet.core.database.migration

import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * 資料庫遷移管理器
 * 
 * 管理從 Room 到 SQLDelight 的遷移
 * 
 * Created: 2025-01-17
 */
class MigrationManager {
    
    /**
     * 遷移步驟定義
     */
    sealed class MigrationStep {
        abstract val version: Int
        abstract val description: String
        abstract suspend fun execute(context: MigrationContext)
        
        data class CreateTable(
            override val version: Int,
            override val description: String,
            val tableName: String,
            val createSql: String
        ) : MigrationStep() {
            override suspend fun execute(context: MigrationContext) {
                context.execute(createSql)
                Logger.d("Migration", "Created table: $tableName")
            }
        }
        
        data class AddColumn(
            override val version: Int,
            override val description: String,
            val tableName: String,
            val columnName: String,
            val columnType: String,
            val defaultValue: String? = null
        ) : MigrationStep() {
            override suspend fun execute(context: MigrationContext) {
                val default = defaultValue?.let { " DEFAULT $it" } ?: ""
                val sql = "ALTER TABLE $tableName ADD COLUMN $columnName $columnType$default"
                context.execute(sql)
                Logger.d("Migration", "Added column $columnName to $tableName")
            }
        }
        
        data class CreateIndex(
            override val version: Int,
            override val description: String,
            val indexName: String,
            val tableName: String,
            val columns: List<String>
        ) : MigrationStep() {
            override suspend fun execute(context: MigrationContext) {
                val sql = "CREATE INDEX IF NOT EXISTS $indexName ON $tableName(${columns.joinToString(", ")})"
                context.execute(sql)
                Logger.d("Migration", "Created index: $indexName")
            }
        }
        
        data class DataMigration(
            override val version: Int,
            override val description: String,
            val migrationLogic: suspend (MigrationContext) -> Unit
        ) : MigrationStep() {
            override suspend fun execute(context: MigrationContext) {
                migrationLogic(context)
                Logger.d("Migration", "Executed data migration: $description")
            }
        }
    }
    
    /**
     * 所有遷移步驟
     */
    private val migrations = listOf(
        // Version 1 -> 2: 添加 metadata 欄位
        MigrationStep.AddColumn(
            version = 2,
            description = "Add metadata column to token table",
            tableName = "token",
            columnName = "metadata",
            columnType = "TEXT",
            defaultValue = "'{}'"
        ),
        
        // Version 2 -> 3: 創建效能索引
        MigrationStep.CreateIndex(
            version = 3,
            description = "Create performance indexes",
            indexName = "idx_token_wallet_chain",
            tableName = "token",
            columns = listOf("wallet_id", "chain_id")
        ),
        
        // Version 3 -> 4: 添加 Keystone 支援
        MigrationStep.AddColumn(
            version = 4,
            description = "Add Keystone support to transactions",
            tableName = "transaction_record",
            columnName = "keystone_sign_request_id",
            columnType = "TEXT",
            defaultValue = null
        ),
        
        // Version 4 -> 5: 資料正規化
        MigrationStep.DataMigration(
            version = 5,
            description = "Normalize wallet data from Room to SQLDelight",
            migrationLogic = { context ->
                migrateRoomData(context)
            }
        ),
        
        // Version 5 -> 6: 創建分析表
        MigrationStep.CreateTable(
            version = 6,
            description = "Create analytics table",
            tableName = "analytics_event",
            createSql = """
                CREATE TABLE IF NOT EXISTS analytics_event (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_name TEXT NOT NULL,
                    event_data TEXT NOT NULL,
                    user_id TEXT,
                    session_id TEXT,
                    timestamp INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
            """.trimIndent()
        )
    )
    
    /**
     * 執行遷移
     */
    suspend fun migrate(
        context: MigrationContext,
        fromVersion: Int,
        toVersion: Int
    ) = withContext(Dispatchers.Default) {
        Logger.i("MigrationManager", 
            "Starting migration from version $fromVersion to $toVersion")
        
        val startTime = Clock.System.now()
        var currentVersion = fromVersion
        
        try {
            // 開始事務
            context.beginTransaction()
            
            // 執行需要的遷移步驟
            migrations
                .filter { it.version > fromVersion && it.version <= toVersion }
                .sortedBy { it.version }
                .forEach { migration ->
                    Logger.d("MigrationManager", 
                        "Executing migration v${migration.version}: ${migration.description}")
                    
                    migration.execute(context)
                    currentVersion = migration.version
                    
                    // 更新版本號
                    context.setVersion(currentVersion)
                }
            
            // 提交事務
            context.commitTransaction()
            
            val duration = Clock.System.now() - startTime
            Logger.i("MigrationManager", 
                "Migration completed successfully in ${duration.inWholeMilliseconds}ms")
            
        } catch (e: Exception) {
            // 回滾事務
            context.rollbackTransaction()
            
            Logger.e("MigrationManager", 
                "Migration failed at version $currentVersion", e)
            throw MigrationException(
                "Failed to migrate from $fromVersion to $toVersion at step $currentVersion", 
                e
            )
        }
    }
    
    /**
     * 從 Room 遷移資料
     */
    private suspend fun migrateRoomData(context: MigrationContext) {
        // 檢查是否有 Room 資料表
        val hasRoomTables = context.tableExists("room_master_table")
        
        if (!hasRoomTables) {
            Logger.d("MigrationManager", "No Room tables found, skipping Room migration")
            return
        }
        
        Logger.i("MigrationManager", "Starting Room to SQLDelight data migration")
        
        // 遷移錢包資料
        migrateWallets(context)
        
        // 遷移代幣資料
        migrateTokens(context)
        
        // 遷移交易資料
        migrateTransactions(context)
        
        Logger.i("MigrationManager", "Room data migration completed")
    }
    
    /**
     * 遷移錢包資料
     */
    private suspend fun migrateWallets(context: MigrationContext) {
        val walletCount = context.query(
            "SELECT COUNT(*) FROM wallet_table"
        ).firstOrNull()?.getLong(0) ?: 0
        
        if (walletCount == 0L) {
            Logger.d("MigrationManager", "No wallets to migrate")
            return
        }
        
        Logger.d("MigrationManager", "Migrating $walletCount wallets")
        
        context.execute("""
            INSERT INTO wallet (name, address, mnemonic, private_key, is_active)
            SELECT name, address, mnemonic, private_key, is_active
            FROM wallet_table
        """.trimIndent())
    }
    
    /**
     * 遷移代幣資料
     */
    private suspend fun migrateTokens(context: MigrationContext) {
        val tokenCount = context.query(
            "SELECT COUNT(*) FROM token_table"
        ).firstOrNull()?.getLong(0) ?: 0
        
        if (tokenCount == 0L) {
            Logger.d("MigrationManager", "No tokens to migrate")
            return
        }
        
        Logger.d("MigrationManager", "Migrating $tokenCount tokens")
        
        // 映射 Room UUID 到 SQLDelight ID
        context.execute("""
            INSERT INTO token (
                wallet_id, address, symbol, name, decimals, 
                chain_type, chain_id, balance, usd_price
            )
            SELECT 
                w.id, t.address, t.symbol, t.name, t.decimals,
                t.chain_type, t.chain_id, t.balance, t.usd_price
            FROM token_table t
            JOIN wallet w ON w.address = t.wallet_address
        """.trimIndent())
    }
    
    /**
     * 遷移交易資料
     */
    private suspend fun migrateTransactions(context: MigrationContext) {
        val txCount = context.query(
            "SELECT COUNT(*) FROM transaction_table"
        ).firstOrNull()?.getLong(0) ?: 0
        
        if (txCount == 0L) {
            Logger.d("MigrationManager", "No transactions to migrate")
            return
        }
        
        Logger.d("MigrationManager", "Migrating $txCount transactions")
        
        context.execute("""
            INSERT INTO transaction_record (
                wallet_id, tx_hash, from_address, to_address, value,
                status, type, chain_type, chain_id, created_at
            )
            SELECT 
                w.id, t.tx_hash, t.from_address, t.to_address, t.value,
                t.status, t.type, t.chain_type, t.chain_id, t.timestamp
            FROM transaction_table t
            JOIN wallet w ON w.address = t.wallet_address
        """.trimIndent())
    }
    
    /**
     * 驗證遷移結果
     */
    suspend fun validateMigration(context: MigrationContext): MigrationValidationResult {
        val issues = mutableListOf<String>()
        
        // 檢查必要的表是否存在
        val requiredTables = listOf("wallet", "token", "transaction_record", "nft")
        requiredTables.forEach { table ->
            if (!context.tableExists(table)) {
                issues.add("Missing required table: $table")
            }
        }
        
        // 檢查索引
        val requiredIndexes = listOf(
            "idx_token_wallet_id",
            "idx_tx_wallet_id",
            "idx_tx_hash"
        )
        requiredIndexes.forEach { index ->
            if (!context.indexExists(index)) {
                issues.add("Missing required index: $index")
            }
        }
        
        // 檢查資料完整性
        val orphanedTokens = context.query("""
            SELECT COUNT(*) FROM token 
            WHERE wallet_id NOT IN (SELECT id FROM wallet)
        """.trimIndent()).firstOrNull()?.getLong(0) ?: 0
        
        if (orphanedTokens > 0) {
            issues.add("Found $orphanedTokens orphaned tokens")
        }
        
        return MigrationValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            version = context.getVersion()
        )
    }
}

/**
 * 遷移上下文
 */
interface MigrationContext {
    suspend fun execute(sql: String)
    suspend fun query(sql: String): List<Row>
    suspend fun tableExists(tableName: String): Boolean
    suspend fun indexExists(indexName: String): Boolean
    suspend fun getVersion(): Int
    suspend fun setVersion(version: Int)
    suspend fun beginTransaction()
    suspend fun commitTransaction()
    suspend fun rollbackTransaction()
}

/**
 * 資料庫行
 */
interface Row {
    fun getString(index: Int): String?
    fun getLong(index: Int): Long
    fun getDouble(index: Int): Double
    fun getInt(index: Int): Int
}

/**
 * 遷移異常
 */
class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 遷移驗證結果
 */
data class MigrationValidationResult(
    val isValid: Boolean,
    val issues: List<String>,
    val version: Int
) {
    fun printReport() {
        println("""
            ╔════════════════════════════════════════╗
            ║     Migration Validation Report         ║
            ╚════════════════════════════════════════╝
            
            📊 Database Version: $version
            ✅ Valid: $isValid
            
            ${if (issues.isNotEmpty()) {
                "❌ Issues Found:\n" + issues.joinToString("\n") { "  • $it" }
            } else {
                "✅ No issues found"
            }}
        """.trimIndent())
    }
}