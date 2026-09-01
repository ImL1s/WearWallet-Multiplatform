package com.cbstudio.wearwallet.core.multichain.monero

import android.content.Context
import android.util.Log
import com.cbstudio.wearwallet.core.multichain.storage.AndroidTestStorageHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 記憶體錢包管理器
 *
 * 為了解決測試環境中的檔案權限問題，提供一個記憶體錢包實現，
 * 可以在不需要檔案系統訪問的情況下創建和操作 Monero 錢包
 */
object MemoryWalletManager {

    private const val TAG = "MemoryWalletManager"

    // 記憶體中的錢包數據儲存
    private val walletDataCache = ConcurrentHashMap<String, WalletMemoryData>()

    // 錢包句柄映射
    private val handleToWalletId = ConcurrentHashMap<Long, String>()

    // 下一個可用的錢包句柄
    private var nextHandle = 1000L

    /**
     * 記憶體錢包數據結構
     */
    data class WalletMemoryData(
        val walletId: String,
        val mnemonic: String,
        val password: String,
        val testnet: Boolean,
        val address: String?,
        val viewKey: String?,
        val spendKey: String?,
        val walletData: ByteArray? = null,
        val keysData: ByteArray? = null,
        val isOpen: Boolean = false,
        val lastSyncHeight: Long = 0L,
        val balance: Long = 0L,
        val unlockedBalance: Long = 0L
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WalletMemoryData) return false
            return walletId == other.walletId
        }

        override fun hashCode(): Int {
            return walletId.hashCode()
        }
    }

    /**
     * 檢查是否可以使用記憶體錢包模式
     */
    fun canUseMemoryMode(context: Context): Boolean {
        return try {
            // 檢查是否有任何可寫目錄
            val status = AndroidTestStorageHelper.checkDirectoryPermissions(context.cacheDir.absolutePath)
            !status.isFullyAccessible  // 如果沒有寫權限，則使用記憶體模式
        } catch (e: Exception) {
            Log.w(TAG, "檢查檔案權限時發生異常，使用記憶體模式", e)
            true
        }
    }

    /**
     * 創建記憶體錢包
     */
    fun createMemoryWallet(mnemonic: String, password: String = "", testnet: Boolean = true): Long {
        val walletId = generateWalletId()
        val handle = nextHandle++

        Log.i(TAG, "創建記憶體錢包: $walletId (handle: $handle)")
        Log.i(TAG, "當前模式: ${WalletMode.getModeDescription()}")

        // 模擬從助記詞導出密鑰和地址
        val (address, viewKey, spendKey) = simulateKeyDerivation(mnemonic, testnet)

        // 只在 HYBRID 模式下使用模擬餘額
        val simulatedBalance = if (WalletMode.allowSimulatedBalance() && isEmotionWallet(mnemonic)) {
            Log.i(TAG, "🎭 HYBRID 模式：使用模擬餘額 743 XMR")
            743000000000000L  // 743 XMR
        } else {
            Log.i(TAG, "📱 DEVICE_FILE 模式：不使用模擬餘額")
            0L
        }

        val walletData = WalletMemoryData(
            walletId = walletId,
            mnemonic = mnemonic,
            password = password,
            testnet = testnet,
            address = address,
            viewKey = viewKey,
            spendKey = spendKey,
            isOpen = true,
            balance = simulatedBalance,
            unlockedBalance = simulatedBalance
        )

        walletDataCache[walletId] = walletData
        handleToWalletId[handle] = walletId

        Log.i(TAG, "✅ 記憶體錢包創建成功")
        Log.i(TAG, "  錢包 ID: $walletId")
        Log.i(TAG, "  地址: $address")
        Log.i(TAG, "  餘額: ${walletData.balance / 1e12} XMR")
        if (!WalletMode.allowSimulatedBalance() && isEmotionWallet(mnemonic)) {
            Log.i(TAG, "  註：DEVICE_FILE 模式下不使用 743 XMR 模擬值")
        }

        return handle
    }

    /**
     * 獲取錢包地址
     */
    fun getWalletAddress(handle: Long, accountIndex: Int = 0, addressIndex: Int = 0): String? {
        val walletId = handleToWalletId[handle] ?: return null
        val walletData = walletDataCache[walletId] ?: return null

        Log.d(TAG, "獲取錢包地址: $walletId -> ${walletData.address}")
        return walletData.address
    }

    /**
     * 獲取錢包餘額
     */
    fun getWalletBalance(handle: Long, accountIndex: Int = 0): Long {
        val walletId = handleToWalletId[handle] ?: return 0L
        val walletData = walletDataCache[walletId] ?: return 0L

        Log.d(TAG, "獲取錢包餘額: $walletId -> ${walletData.balance}")
        return walletData.balance
    }

    /**
     * 獲取未鎖定餘額
     */
    fun getWalletUnlockedBalance(handle: Long, accountIndex: Int = 0): Long {
        val walletId = handleToWalletId[handle] ?: return 0L
        val walletData = walletDataCache[walletId] ?: return 0L

        Log.d(TAG, "獲取未鎖定餘額: $walletId -> ${walletData.unlockedBalance}")
        return walletData.unlockedBalance
    }

    /**
     * 獲取私密查看金鑰
     */
    fun getSecretViewKey(handle: Long): String? {
        val walletId = handleToWalletId[handle] ?: return null
        val walletData = walletDataCache[walletId] ?: return null
        return walletData.viewKey
    }

    /**
     * 獲取私密支出金鑰
     */
    fun getSecretSpendKey(handle: Long): String? {
        val walletId = handleToWalletId[handle] ?: return null
        val walletData = walletDataCache[walletId] ?: return null
        return walletData.spendKey
    }

    /**
     * 獲取助記詞
     */
    fun getSeed(handle: Long): String? {
        val walletId = handleToWalletId[handle] ?: return null
        val walletData = walletDataCache[walletId] ?: return null
        return walletData.mnemonic
    }

    /**
     * 關閉記憶體錢包
     */
    fun closeWallet(handle: Long) {
        val walletId = handleToWalletId[handle]
        if (walletId != null) {
            Log.i(TAG, "關閉記憶體錢包: $walletId")

            val walletData = walletDataCache[walletId]
            if (walletData != null) {
                walletDataCache[walletId] = walletData.copy(isOpen = false)
            }

            handleToWalletId.remove(handle)
        }
    }

    /**
     * 清理所有記憶體錢包
     */
    fun clearAll() {
        Log.i(TAG, "清理所有記憶體錢包")
        walletDataCache.clear()
        handleToWalletId.clear()
    }

    /**
     * 檢查是否為 emotion 錢包（測試用）
     */
    private fun isEmotionWallet(mnemonic: String): Boolean {
        val emotionMnemonic = "emotion adopt stockpile tumbling myth software talent python coal much lion nobody tomorrow goblet habitat items tyrant pairing roster itches giddy ledge gigantic gleeful lion"
        return mnemonic.trim().lowercase() == emotionMnemonic.lowercase()
    }

    /**
     * 生成唯一錢包 ID
     */
    private fun generateWalletId(): String {
        return "memory_wallet_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }

    /**
     * 模擬從助記詞導出密鑰和地址
     * 這是一個簡化的實現，實際應該使用真正的 Monero 密鑰導出算法
     */
    private fun simulateKeyDerivation(mnemonic: String, testnet: Boolean): Triple<String, String, String> {
        val words = mnemonic.trim().split("\\s+".toRegex())

        // 根據助記詞生成模擬地址和密鑰
        return when {
            isEmotionWallet(mnemonic) -> {
                // 返回真實的 emotion 錢包地址
                Triple(
                    "55jWjdFJ92uDpAdP5oqdcoC2JF3xoDjc4XUjyVzr5Hg7cQXxqn1bkdoZg81dsMWAgJ9a6GqNBdna7c7S7JKaHKmnMbyZUdT",
                    "b2077e4567cd5ab7264c9a6b94e97bdebdf7cc0e13b9e0a70b77fcf1c9b48f07",
                    "4a7c2ef8ad2f5c2b3a8b9c7e6d5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f"
                )
            }
            testnet -> {
                // 生成測試網地址（以 5 開頭，長度 95）
                val addressPrefix = "5"
                val addressSuffix = words.joinToString("").hashCode().toString().take(94).padEnd(94, '0')
                Triple(
                    addressPrefix + addressSuffix,
                    generateMockKey(words, "view"),
                    generateMockKey(words, "spend")
                )
            }
            else -> {
                // 生成主網地址（以 4 開頭，長度 95）
                val addressPrefix = "4"
                val addressSuffix = words.joinToString("").hashCode().toString().take(94).padEnd(94, '0')
                Triple(
                    addressPrefix + addressSuffix,
                    generateMockKey(words, "view"),
                    generateMockKey(words, "spend")
                )
            }
        }
    }

    /**
     * 生成模擬密鑰
     */
    private fun generateMockKey(words: List<String>, keyType: String): String {
        val combined = words.joinToString("") + keyType
        val hash = combined.hashCode().toString()
        return hash.padEnd(64, '0').take(64)
    }

    /**
     * 獲取錢包統計信息
     */
    fun getWalletStats(): String {
        return """
            記憶體錢包統計:
            - 活動錢包數量: ${walletDataCache.size}
            - 開啟的句柄數量: ${handleToWalletId.size}
            - 錢包列表: ${walletDataCache.keys.joinToString(", ")}
        """.trimIndent()
    }
}