package com.cbstudio.wearwallet.core.security

import android.os.Build
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Android Root 檢測器
 *
 * 實現多種檢測方法以識別設備是否已被 Root，保護錢包應用的安全性。
 *
 * 檢測方法：
 * 1. Superuser.apk 檢測 - 檢查常見 Root 管理應用
 * 2. su 二進制文件檢測 - 檢查 su 命令的存在
 * 3. BusyBox 檢測 - 檢查 BusyBox 工具集
 * 4. Root 文件系統檢測 - 檢查 Root 相關的系統文件
 * 5. Test-keys 檢測 - 檢查系統是否使用測試簽名
 * 6. 系統分區可寫性檢測 - 檢查 /system 分區是否可寫
 * 7. Root 進程檢測 - 檢查是否有 su 相關進程運行
 *
 * @author WearWallet Security Team
 * @since 2025-10-28
 */
class RootDetector {

    /**
     * 檢測設備是否已被 Root
     *
     * @return true 如果檢測到任何 Root 特徵，false 如果設備看起來安全
     */
    fun isDeviceRooted(): Boolean {
        return checkSuperuserApk() ||
               checkSuBinary() ||
               checkBusyBox() ||
               checkRootFiles() ||
               checkTestKeys() ||
               checkSystemWritable() ||
               checkRootProcesses()
    }

    /**
     * 獲取詳細的檢測報告
     *
     * @return 包含所有檢測結果的列表
     */
    fun getDetectionDetails(): List<RootDetectionResult> {
        val results = mutableListOf<RootDetectionResult>()

        results.add(RootDetectionResult(
            method = "Superuser APK",
            detected = checkSuperuserApk(),
            description = "檢查 Superuser.apk 或 SuperSU.apk 是否存在"
        ))

        results.add(RootDetectionResult(
            method = "su 二進制文件",
            detected = checkSuBinary(),
            description = "檢查 su 命令是否存在於系統路徑"
        ))

        results.add(RootDetectionResult(
            method = "BusyBox",
            detected = checkBusyBox(),
            description = "檢查 BusyBox 工具集是否已安裝"
        ))

        results.add(RootDetectionResult(
            method = "Root 文件",
            detected = checkRootFiles(),
            description = "檢查常見的 Root 相關文件"
        ))

        results.add(RootDetectionResult(
            method = "Test-keys",
            detected = checkTestKeys(),
            description = "檢查系統是否使用測試簽名金鑰"
        ))

        results.add(RootDetectionResult(
            method = "系統分區可寫",
            detected = checkSystemWritable(),
            description = "檢查 /system 分區是否可寫入"
        ))

        results.add(RootDetectionResult(
            method = "Root 進程",
            detected = checkRootProcesses(),
            description = "檢查是否有 su 相關進程運行"
        ))

        return results
    }

    /**
     * 方法 1: 檢測 Superuser.apk
     *
     * 檢查常見的 Root 管理應用是否存在
     */
    private fun checkSuperuserApk(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/data/app/com.noshufou.android.su",
            "/system/app/SuperSU.apk",
            "/system/app/SuperSU",
            "/system/app/Superuser",
            "/data/app/eu.chainfire.supersu",
            "/data/app/com.koushikdutta.superuser",
            "/data/app/com.thirdparty.superuser"
        )

        return paths.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 方法 2: 檢測 su 二進制文件
     *
     * 檢查 su 命令是否存在於常見的系統路徑
     */
    private fun checkSuBinary(): Boolean {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/",
            "/su/bin/su",
            "/su/bin"
        )

        return paths.any { path ->
            try {
                val file = File(path)
                file.exists() && file.canExecute()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 方法 3: 檢測 BusyBox
     *
     * BusyBox 是一個包含多種 Unix 工具的單一可執行文件，
     * 常被 Root 工具使用
     */
    private fun checkBusyBox(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which busybox")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            result != null && result.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 方法 4: 檢測常見 Root 文件
     *
     * 檢查各種 Root 工具創建的文件和目錄
     */
    private fun checkRootFiles(): Boolean {
        val files = listOf(
            "/system/app/SuperSU",
            "/system/etc/init.d/99SuperSUDaemon",
            "/dev/com.koushikdutta.superuser.daemon",
            "/system/xbin/daemonsu",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/mu",
            "/sbin/magisk",
            "/sbin/.magisk",
            "/cache/.disable_magisk",
            "/dev/.magisk.unblock",
            "/cache/magisk.log",
            "/data/adb/magisk",
            "/data/adb/magisk.img",
            "/data/magisk"
        )

        return files.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 方法 5: 檢測 test-keys
     *
     * 檢查系統是否使用測試簽名編譯，這通常表示自定義 ROM
     */
    private fun checkTestKeys(): Boolean {
        return try {
            val buildTags = Build.TAGS
            buildTags != null && buildTags.contains("test-keys")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 方法 6: 檢測系統分區可寫性
     *
     * 在正常的 Android 設備上，/system 分區應該是只讀的
     * Root 後通常會被重新掛載為可寫
     */
    private fun checkSystemWritable(): Boolean {
        return try {
            // 嘗試在 /system 創建測試文件
            val testFile = File("/system/.root_test")
            testFile.createNewFile()
            val writable = testFile.exists()
            if (writable) {
                testFile.delete()
            }
            writable
        } catch (e: Exception) {
            // 如果無法創建文件，說明系統分區是只讀的（正常情況）
            false
        }
    }

    /**
     * 方法 7: 檢測 Root 進程
     *
     * 檢查是否有 su 相關的進程正在運行
     */
    private fun checkRootProcesses(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ps")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    if (it.contains("su") ||
                        it.contains("supersu") ||
                        it.contains("daemonsu") ||
                        it.contains("magisk")) {
                        return true
                    }
                }
            }

            process.waitFor()
            false
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Root 檢測結果數據類
 *
 * @property method 檢測方法名稱
 * @property detected 是否檢測到 Root 特徵
 * @property description 檢測方法的描述
 */
data class RootDetectionResult(
    val method: String,
    val detected: Boolean,
    val description: String
)
