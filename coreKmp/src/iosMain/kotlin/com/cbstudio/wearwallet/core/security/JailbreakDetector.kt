package com.cbstudio.wearwallet.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.*
import platform.posix.fork

/**
 * iOS Jailbreak 檢測器
 *
 * 實現多種檢測方法以識別設備是否已被越獄，保護錢包應用的安全性。
 *
 * 檢測方法：
 * 1. Cydia App 檢測 - 檢查越獄商店應用
 * 2. 可疑文件檢測 - 檢查越獄工具創建的文件
 * 3. 系統權限檢測 - 檢查沙盒外的寫入權限
 * 4. Fork 檢測 - 檢查是否能創建子進程（越獄設備允許）
 * 5. 符號鏈接檢測 - 檢查系統目錄的符號鏈接
 * 6. URL Scheme 檢測 - 檢查越獄應用的 URL Scheme
 * 7. 動態庫檢測 - 檢查可疑的動態庫注入
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
        return checkCydiaApp() ||
               checkSuspiciousFiles() ||
               checkSystemPermissions() ||
               checkFork() ||
               checkSymlinks() ||
               checkSuspiciousURLSchemes() ||
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
            method = "Cydia App",
            detected = checkCydiaApp(),
            description = "檢查 Cydia 越獄商店是否已安裝"
        ))

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
            method = "URL Schemes",
            detected = checkSuspiciousURLSchemes(),
            description = "檢查越獄應用的 URL Scheme"
        ))

        results.add(JailbreakDetectionResult(
            method = "動態庫",
            detected = checkDynamicLibraries(),
            description = "檢查可疑的動態庫注入"
        ))

        return results
    }

    /**
     * 方法 1: 檢測 Cydia App
     *
     * Cydia 是最流行的越獄應用商店
     */
    private fun checkCydiaApp(): Boolean {
        return try {
            val cydiaURL = NSURL.URLWithString("cydia://package/com.example.package")
            UIApplication.sharedApplication.canOpenURL(cydiaURL ?: return false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 方法 2: 檢測可疑文件
     *
     * 檢查越獄工具通常創建的文件和目錄
     */
    private fun checkSuspiciousFiles(): Boolean {
        val paths = listOf(
            "/Applications/Cydia.app",
            "/Applications/blackra1n.app",
            "/Applications/FakeCarrier.app",
            "/Applications/Icy.app",
            "/Applications/IntelliScreen.app",
            "/Applications/MxTube.app",
            "/Applications/RockApp.app",
            "/Applications/SBSettings.app",
            "/Applications/WinterBoard.app",
            "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/Library/MobileSubstrate/DynamicLibraries/LiveClock.plist",
            "/Library/MobileSubstrate/DynamicLibraries/Veency.plist",
            "/bin/bash",
            "/usr/sbin/sshd",
            "/usr/libexec/ssh-keysign",
            "/etc/apt",
            "/private/var/lib/apt/",
            "/private/var/lib/cydia",
            "/private/var/mobile/Library/SBSettings/Themes",
            "/private/var/tmp/cydia.log",
            "/private/var/stash",
            "/usr/libexec/sftp-server",
            "/usr/bin/sshd",
            "/System/Library/LaunchDaemons/com.saurik.Cydia.Startup.plist",
            "/System/Library/LaunchDaemons/com.ikey.bbot.plist",
            "/var/cache/apt",
            "/var/lib/apt",
            "/var/lib/cydia",
            "/etc/ssh/sshd_config"
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
     * 方法 3: 檢測系統權限
     *
     * 嘗試在沙盒外的位置寫入文件
     * 正常設備應該拒絕此操作
     */
    private fun checkSystemPermissions(): Boolean {
        val testPath = "/private/jailbreak_test.txt"
        return try {
            val testString = "test" as NSString
            testString.writeToFile(
                testPath,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null
            )

            // 如果成功寫入，說明設備可能已越獄
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
            // 無法寫入是正常情況
            false
        }
    }

    /**
     * 方法 4: 檢測 Fork 能力
     *
     * 在正常的 iOS 設備上，fork() 系統調用應該被限制
     * 越獄設備通常會解除這個限制
     */
    private fun checkFork(): Boolean {
        return try {
            val result = fork()
            if (result >= 0) {
                // 能夠 fork，說明可能已越獄
                true
            } else {
                // fork 失敗，這是正常情況
                false
            }
        } catch (e: Exception) {
            // 異常也表示正常
            false
        }
    }

    /**
     * 方法 5: 檢測符號鏈接
     *
     * 越獄過程通常會創建符號鏈接來繞過系統保護
     */
    private fun checkSymlinks(): Boolean {
        val pathsToCheck = listOf(
            "/Applications",
            "/Library/Ringtones",
            "/Library/Wallpaper",
            "/usr/arm-apple-darwin9",
            "/usr/include",
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
     * 方法 6: 檢測可疑的 URL Schemes
     *
     * 檢查多個越獄應用的 URL Scheme
     */
    private fun checkSuspiciousURLSchemes(): Boolean {
        val schemes = listOf(
            "cydia://",
            "undecimus://",
            "sileo://",
            "zbra://",
            "filza://",
            "activator://"
        )

        return schemes.any { scheme ->
            try {
                val url = NSURL.URLWithString(scheme)
                UIApplication.sharedApplication.canOpenURL(url ?: return@any false)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 方法 7: 檢測動態庫注入
     *
     * 檢查是否有可疑的動態庫被載入
     *
     * 注意：此方法在當前實現中暫時禁用，因為 _dyld_get_image_name
     * 需要額外的 cinterop 配置。未來版本將通過 Swift 橋接實現。
     */
    private fun checkDynamicLibraries(): Boolean {
        // TODO: 通過 Swift 橋接實現動態庫檢測
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
