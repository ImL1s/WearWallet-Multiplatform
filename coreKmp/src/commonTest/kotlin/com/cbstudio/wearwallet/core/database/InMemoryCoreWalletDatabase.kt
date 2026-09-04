package com.cbstudio.wearwallet.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

internal fun createInMemoryCoreWalletDatabase(): CoreWalletDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    CoreWalletDatabase.Schema.create(driver)
    return CoreWalletDatabase(driver)
}
