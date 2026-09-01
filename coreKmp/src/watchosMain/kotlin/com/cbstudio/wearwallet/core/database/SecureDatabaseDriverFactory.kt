@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package com.cbstudio.wearwallet.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*

/**
 * watchOS 平台加密資料庫驅動工廠
 *
 * 注意：加密功能暫時停用，使用標準 SQLite (因編譯環境 Cinterop 問題)。
 * TODO: 解決 Xcode/Cinterop 環境問題後重新啟用 SQLCipher
 */
class SecureDatabaseDriverFactory {

    /**
     * 創建加密的資料庫驅動
     */
    fun createDriver(
        databaseName: String = "core_wallet.db"
    ): SqlDriver {
        return NativeSqliteDriver(CoreWalletDatabase.Schema, databaseName)
    }


    // Private methods for Keychain access removed for simplification (P4 fix)
}

fun createSecureDatabaseDriver(databaseName: String = "core_wallet.db"): SqlDriver {
    return SecureDatabaseDriverFactory().createDriver(databaseName)
}

