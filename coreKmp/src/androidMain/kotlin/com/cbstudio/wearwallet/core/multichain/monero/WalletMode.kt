package com.cbstudio.wearwallet.core.multichain.monero

import android.util.Log

/**
 * 錢包模式配置
 * 控制錢包創建和操作的行為模式
 *
 * DEVICE_FILE: 實體機專用模式，只使用檔案系統，不允許回退到記憶體
 * HYBRID: 混合模式，優先使用檔案系統，失敗時回退到記憶體（測試用）
 */
object WalletMode {

    private const val TAG = "WalletMode"

    /**
     * 錢包模式類型
     */
    enum class Type {
        /**
         * 實體機檔案模式
         * - 只使用檔案系統存儲錢包
         * - 不允許回退到記憶體模式
         * - 失敗時拋出異常
         * - 適用於實體機測試和生產環境
         */
        DEVICE_FILE,

        /**
         * 混合模式
         * - 優先使用檔案系統
         * - 失敗時自動回退到記憶體模式
         * - 適用於測試環境和模擬器
         */
        HYBRID
    }

    /**
     * 當前模式設定
     * 預設為 DEVICE_FILE（實體機優先）
     */
    @Volatile
    var current: Type = Type.DEVICE_FILE
        set(value) {
            Log.i(TAG, "切換錢包模式: $field -> $value")
            field = value
        }

    /**
     * 檢查是否允許使用記憶體錢包
     */
    fun allowMemoryWallet(): Boolean {
        return current == Type.HYBRID
    }

    /**
     * 檢查是否強制使用檔案錢包
     */
    fun requireFileWallet(): Boolean {
        return current == Type.DEVICE_FILE
    }

    /**
     * 檢查是否允許模擬餘額（743 XMR）
     */
    fun allowSimulatedBalance(): Boolean {
        return current == Type.HYBRID
    }

    /**
     * 獲取模式描述
     */
    fun getModeDescription(): String {
        return when (current) {
            Type.DEVICE_FILE -> "實體機檔案模式 - 只使用真實檔案系統"
            Type.HYBRID -> "混合模式 - 檔案優先，支援記憶體回退"
        }
    }

    /**
     * 根據執行環境自動設定模式
     * @param isEmulator 是否為模擬器
     * @param isTest 是否為測試環境
     */
    fun autoConfigureMode(isEmulator: Boolean = false, isTest: Boolean = false) {
        current = when {
            isEmulator || isTest -> Type.HYBRID
            else -> Type.DEVICE_FILE
        }
        Log.i(TAG, "自動配置模式: ${getModeDescription()}")
        Log.i(TAG, "  isEmulator: $isEmulator, isTest: $isTest")
    }
}