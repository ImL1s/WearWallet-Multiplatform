package com.cbstudio.wearwallet.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.cbstudio.wearwallet.core.security.DatabaseKeyManager
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * 創建加密的 SQLite Driver (Android)
 *
 * 使用 SQLCipher 提供資料庫加密:
 * - AES-256 加密算法
 * - 密鑰存儲在 Android Keystore
 * - 符合 FIPS 140-2 標準
 *
 * @param context Android Context
 */
class SecureDatabaseDriverFactory(private val context: Context) {

    /**
     * 創建加密的資料庫驅動
     */
    fun createDriver(
        databaseName: String = "core_wallet.db"
    ): SqlDriver {
        // 初始化 SQLCipher 庫
        SQLiteDatabase.loadLibs(context)

        // 獲取加密密鑰
        val keyManager = DatabaseKeyManager(context)
        val key = keyManager.getDatabaseKey()

        // 創建 SupportFactory 用於 SQLCipher
        // SQLCipher 需要 ByteArray 密碼
        val passphrase = SQLiteDatabase.getBytes(key.toHexString().toCharArray())
        val factory = SupportFactory(passphrase)

        // 創建加密的 Driver
        return AndroidSqliteDriver(
            schema = CoreWalletDatabase.Schema,
            context = context,
            name = databaseName,
            factory = factory
        )
    }

    /**
     * 遷移未加密資料庫到加密資料庫
     *
     * 使用場景：應用已有未加密資料庫，需要升級為加密版本
     *
     * @param oldDatabaseName 舊的未加密資料庫名稱
     * @param newDatabaseName 新的加密資料庫名稱
     * @return Boolean 是否成功遷移
     */
    fun migrateToEncrypted(
        oldDatabaseName: String = "core_wallet.db",
        newDatabaseName: String = "core_wallet_encrypted.db"
    ): Boolean {
        return try {
            SQLiteDatabase.loadLibs(context)

            val keyManager = DatabaseKeyManager(context)
            val key = keyManager.getDatabaseKey()
            val keyHex = key.toHexString()

            // 打開舊的未加密資料庫
            val oldDbPath = context.getDatabasePath(oldDatabaseName).absolutePath
            val oldDb = SQLiteDatabase.openDatabase(
                oldDbPath,
                "",
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            // 執行 SQLCipher ATTACH 命令進行加密遷移
            val newDbPath = context.getDatabasePath(newDatabaseName).absolutePath
            oldDb.rawExecSQL("ATTACH DATABASE '$newDbPath' AS encrypted KEY x'$keyHex'")
            oldDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            oldDb.rawExecSQL("DETACH DATABASE encrypted")

            oldDb.close()

            // 刪除舊的未加密資料庫
            context.deleteDatabase(oldDatabaseName)

            // 重命名加密資料庫
            val newDbFile = context.getDatabasePath(newDatabaseName)
            val finalDbFile = context.getDatabasePath(oldDatabaseName)
            newDbFile.renameTo(finalDbFile)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * ByteArray 轉 Hex 字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

/**
 * 擴展函數：為 Context 創建加密的資料庫驅動
 */
fun Context.createSecureDatabaseDriver(
    databaseName: String = "core_wallet.db"
): SqlDriver {
    return SecureDatabaseDriverFactory(this).createDriver(databaseName)
}
