package com.cbstudio.wearwallet.core.database

import app.cash.sqldelight.db.SqlDriver

/**
 * 數據庫初始化異常 (Fail-Closed)
 */
open class DatabaseInitializationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * 跨平台數據庫驅動工廠
 * 各平台需要實現自己的驅動創建邏輯
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}