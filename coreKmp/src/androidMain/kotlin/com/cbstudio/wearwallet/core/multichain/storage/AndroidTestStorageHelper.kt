package com.cbstudio.wearwallet.core.multichain.storage

import android.content.Context
import java.io.File

/**
 * Android 測試環境檔案儲存輔助工具
 * 解決 Android Instrumented Test 中的檔案寫入權限問題
 */
object AndroidTestStorageHelper {


    /**
     * 獲取 Android 測試環境中可寫的目錄路徑
     */
    fun getTestWritableDirectory(context: Context): String {
        // 1. 優先使用 internal storage files 目錄
        val filesDir = context.filesDir
        return filesDir.absolutePath
    }

    /**
     * 獲取 Monero 錢包專用目錄
     */
    fun getMoneroWalletDirectory(context: Context): String {
        val baseDir = getTestWritableDirectory(context)
        val moneroDir = File(baseDir, "monero_wallets")

        // 確保目錄存在且有寫入權限
        if (!moneroDir.exists()) {
            val created = moneroDir.mkdirs()
            if (!created) {
                throw RuntimeException("無法創建 Monero 錢包目錄: ${moneroDir.absolutePath}")
            }
        }

        // 測試目錄是否可寫
        val testFile = File(moneroDir, "test_write.tmp")
        try {
            testFile.writeText("test")
            testFile.delete()
        } catch (e: Exception) {
            throw RuntimeException("Monero 錢包目錄不可寫: ${moneroDir.absolutePath}", e)
        }

        return moneroDir.absolutePath
    }

    /**
     * 獲取快取目錄（通常用於臨時檔案）
     */
    fun getCacheDirectory(context: Context): String {
        val cacheDir = context.cacheDir
        return cacheDir.absolutePath
    }

    /**
     * 獲取外部檔案目錄（如果需要的話）
     * 注意：這需要額外的權限
     */
    fun getExternalFilesDirectory(context: Context): String? {
        val externalFilesDir = context.getExternalFilesDir(null)
        return externalFilesDir?.absolutePath
    }

    /**
     * 清理測試檔案
     */
    fun cleanupTestFiles(context: Context) {
        try {
            val moneroDir = File(getTestWritableDirectory(context), "monero_wallets")
            if (moneroDir.exists()) {
                moneroDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // 清理失敗不應該影響測試
            e.printStackTrace()
        }
    }

    /**
     * 檢查目錄權限
     */
    fun checkDirectoryPermissions(dirPath: String): PermissionCheckResult {
        val dir = File(dirPath)

        return PermissionCheckResult(
            exists = dir.exists(),
            readable = dir.canRead(),
            writable = dir.canWrite(),
            canCreateFiles = canCreateFilesIn(dir),
            path = dirPath
        )
    }

    private fun canCreateFilesIn(dir: File): Boolean {
        if (!dir.exists()) {
            try {
                dir.mkdirs()
            } catch (e: Exception) {
                return false
            }
        }

        val testFile = File(dir, "permission_test_${System.currentTimeMillis()}.tmp")
        return try {
            testFile.writeText("test")
            testFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 權限檢查結果
     */
    data class PermissionCheckResult(
        val exists: Boolean,
        val readable: Boolean,
        val writable: Boolean,
        val canCreateFiles: Boolean,
        val path: String
    ) {
        val isFullyAccessible = exists && readable && writable && canCreateFiles

        override fun toString(): String {
            return """
                目錄權限檢查結果:
                路徑: $path
                存在: $exists
                可讀: $readable
                可寫: $writable
                可創建檔案: $canCreateFiles
                完全可用: $isFullyAccessible
            """.trimIndent()
        }
    }
}