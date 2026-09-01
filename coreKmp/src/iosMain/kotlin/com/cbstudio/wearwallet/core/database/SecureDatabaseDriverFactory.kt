@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.cbstudio.wearwallet.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.*

/**
 * iOS 平台資料庫驅動工廠
 *
 * 注意：加密功能暫時停用，使用標準 SQLite (因編譯環境 Cinterop 問題)。
 * TODO: 解決 Xcode/Cinterop 環境問題後重新啟用 SQLCipher
 */
class SecureDatabaseDriverFactory {

    /**
     * 創建資料庫驅動
     */
    fun createDriver(
        databaseName: String = "core_wallet.db"
    ): SqlDriver {
        val schema = CoreWalletDatabase.Schema
        
        return NativeSqliteDriver(
            schema = schema,
            name = databaseName
        )
    }

    /**
     * 從 Keychain 刪除密鑰
     */
    fun clearKeys() {
        // TODO: 實現 Keychain 整合
    }
}

/**
 * 擴展函數：創建資料庫驅動
 */
fun createSecureDatabaseDriver(databaseName: String = "core_wallet.db"): SqlDriver {
    return SecureDatabaseDriverFactory().createDriver(databaseName)
}

