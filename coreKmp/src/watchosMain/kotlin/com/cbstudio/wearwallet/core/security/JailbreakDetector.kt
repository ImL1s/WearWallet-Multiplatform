package com.cbstudio.wearwallet.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.Foundation.*
import platform.darwin.*


/**
 * watchOS Jailbreak 檢測器
 *
 * watchOS 版本的越獄檢測，由於 watchOS 沒有 UIApplication，
 * 因此部分檢測方法與 iOS 版本有所不同。
 *
 * 檢測方法：
 * 1. 可疑文件檢測 - 檢查越獄工具創建的文件
 * 2. 系統權限檢測 - 檢查沙盒外的寫入權限
 * 3. Fork 檢測 - 檢查是否能創建子進程
 * 4. 符號鏈接檢測 - 檢查系統目錄的符號鏈接
 * 5. 動態庫檢測 - 檢查可疑的動態庫注入
 *
 * @author WearWallet Security Team
 * @since 2025-10-28
 */
@OptIn(ExperimentalForeignApi::class)
class JailbreakDetector {

    /**
     * 檢測設備是否已被越獄
     *
     * @return true 如果檢測到任何越獄特徵，false 如果設備看起來安全
     */
    fun isDeviceJailbroken(): Boolean {
        return checkSuspiciousFiles() ||
               checkSystemPermissions() ||
               checkFork() ||
               checkSymlinks() ||
               checkDynamicLibraries()
    }

    /**
     * 獲取詳細的檢測報告
     *
     * @return 包含所有檢測結果的列表
     */
    fun getDetectionDetails(): List<JailbreakDetectionResult> {
        val results = mutableListOf<JailbreakDetectionResult>()

        results.add(JailbreakDetectionResult(
            method = "可疑文件",
            detected = checkSuspiciousFiles(),
            description = "檢查越獄工具創建的文件"
        ))

        results.add(JailbreakDetectionResult(
            method = "系統權限",
            detected = checkSystemPermissions(),
            description = "檢查沙盒外的寫入權限"
        ))

        results.add(JailbreakDetectionResult(
            method = "Fork 能力",
            detected = checkFork(),
            description = "檢查是否能創建子進程"
        ))

        results.add(JailbreakDetectionResult(
            method = "符號鏈接",
            detected = checkSymlinks(),
            description = "檢查系統目錄的符號鏈接"
        ))

        results.add(JailbreakDetectionResult(
            method = "動態庫",
            detected = checkDynamicLibraries(),
            description = "檢查可疑的動態庫注入"
        ))

        return results
    }

    /**
     * 檢測可疑文件
     */
    private fun checkSuspiciousFiles(): Boolean {
        val paths = listOf(
            "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/bin/bash",
            "/usr/sbin/sshd",
            "/etc/apt",
            "/private/var/lib/apt/",
            "/private/var/lib/cydia",
            "/private/var/tmp/cydia.log",
            "/private/var/stash"
        )

        return paths.any { path ->
            try {
                NSFileManager.defaultManager.fileExistsAtPath(path)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 檢測系統權限
     */
    @OptIn(kotlinx.cinterop.UnsafeNumber::class)
    private fun checkSystemPermissions(): Boolean {
        val testPath = "/private/jailbreak_test.txt"
        return try {
            val testString = "test" as NSString
            testString.writeToFile(
                testPath,
                atomically = true,
                encoding = NSUTF8StringEncoding.convert(),
                error = null
            )

            val fileExists = NSFileManager.defaultManager.fileExistsAtPath(testPath)
            if (fileExists) {
                try {
                    NSFileManager.defaultManager.removeItemAtPath(testPath, error = null)
                } catch (e: Exception) {
                    // 忽略清理錯誤
                }
            }
            fileExists
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 檢測 Fork 能力
     */
    private fun checkFork(): Boolean {
        // fork() is not supported on WatchOS
        return false
    }

    /**
     * 檢測符號鏈接
     */
    private fun checkSymlinks(): Boolean {
        val pathsToCheck = listOf(
            "/Library/Ringtones",
            "/Library/Wallpaper",
            "/usr/libexec",
            "/usr/share"
        )

        return pathsToCheck.any { path ->
            try {
                val attributes = NSFileManager.defaultManager
                    .attributesOfItemAtPath(path, error = null)

                attributes?.get(NSFileType) == NSFileTypeSymbolicLink
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 檢測動態庫注入
     *
     * 注意：此方法在當前實現中暫時禁用，因為 _dyld_get_image_name
     * 需要額外的 cinterop 配置。
     */
    private fun checkDynamicLibraries(): Boolean {
        // TODO: 實現動態庫檢測
        // 暫時返回 false 以保證編譯通過
        return false
    }
}

/**
 * Jailbreak 檢測結果數據類
 *
 * @property method 檢測方法名稱
 * @property detected 是否檢測到越獄特徵
 * @property description 檢測方法的描述
 */
data class JailbreakDetectionResult(
    val method: String,
    val detected: Boolean,
    val description: String
)
