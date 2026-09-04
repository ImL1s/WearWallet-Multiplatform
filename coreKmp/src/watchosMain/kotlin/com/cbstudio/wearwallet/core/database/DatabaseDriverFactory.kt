package com.cbstudio.wearwallet.core.database

import app.cash.sqldelight.db.SqlDriver

/**
 * watchOS 平台的數據庫驅動工廠
 *
 * 使用 SQLCipher 提供加密保護
 * 密鑰存儲在 watchOS Keychain 中
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // 使用加密的資料庫驅動
        return SecureDatabaseDriverFactory().createDriver(
            databaseName = "core_wallet.db"
        )
    }
}