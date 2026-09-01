package com.cbstudio.wearwallet.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver

/**
 * Android 平台的數據庫驅動工廠
 *
 * 使用 SQLCipher 提供加密保護。在發生任何錯誤時 Fail-Closed，嚴禁回退至 in-memory 或未加密資料庫。
 */
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return try {
            // 使用加密的資料庫驅動
            SecureDatabaseDriverFactory(context).createDriver(
                databaseName = "core_wallet.db"
            )
        } catch (t: Throwable) {
            throw DatabaseInitializationException("Failed to initialize encrypted database driver: ${t.message}", t)
        }
    }
}

/**
 * 提供 Context 的接口
 */
interface PlatformContext {
    fun getApplicationContext(): Context
}

/**
 * Android 平台 Context 實現
 */
class AndroidPlatformContext(private val context: Context) : PlatformContext {
    override fun getApplicationContext(): Context = context.applicationContext
}