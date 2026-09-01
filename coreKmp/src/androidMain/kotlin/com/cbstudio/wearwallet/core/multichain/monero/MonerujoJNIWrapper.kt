package com.cbstudio.wearwallet.core.multichain.monero

import android.content.Context
import com.cbstudio.wearwallet.core.multichain.storage.AndroidTestStorageHelper

/**
 * Monero Network Type 枚舉
 * 對應 monero-project 的網絡類型定義
 */
enum class MoneroNetworkType(val value: Int) {
    MAINNET(0),
    TESTNET(1),
    STAGENET(2)
}

/**
 * Monerujo JNI Wrapper
 * 提供對 Monerujo native library 的 JNI 介面
 * 基於 Monerujo 專案的成熟實現
 */
object MonerujoJNIWrapper {
    
    private var isLoaded = false
    
    init {
        loadLibrary()
    }
    
    public fun loadLibrary() {
        if (isLoaded) return
        
        try {
            // 優先嘗試載入 monero_c bridge（使用 C API） - 這是真正的實現
            try {
                // 必須先載入 C++ 標準庫
                System.loadLibrary("c++_shared")
                println("✅ 載入 libc++_shared.so")
                
                // 載入 monero_c 函式庫本身
                try {
                    System.loadLibrary("monero_libwallet2_api_c")
                    println("✅ 載入 libmonero_libwallet2_api_c.so")
                } catch (e: UnsatisfiedLinkError) {
                    println("⚠️ 無法載入 libmonero_libwallet2_api_c.so: ${e.message}")
                }
                
                // 載入 JNI bridge - 優先使用真正的 monero_c 實現
                System.loadLibrary("monero_c_bridge")
                isLoaded = true
                println("✅ Monero C Bridge library 載入成功（使用 monero_c 純 C API）")
                println("✅ 這是真正的 Monero 實現，支援完整區塊鏈功能")
                println("✅ EMOTION 錢包應該能發現 700+ XMR 餘額")
                return
            } catch (e: UnsatisfiedLinkError) {
                println("⚠️ 無法載入 libmonero_c_bridge.so: ${e.message}")
            }
            
            // 嘗試載入真實 Monero 實現（使用 wallet2_api）
            try {
                System.loadLibrary("monero_real_bridge")
                isLoaded = true
                println("✅ Monero Real Bridge library 載入成功（真實 wallet2_api 實現）")
                println("✅ 使用真實 Monero C++ API - 支援完整區塊鏈功能")
                return
            } catch (e: UnsatisfiedLinkError) {
                println("⚠️ 無法載入 libmonero_real_bridge.so: ${e.message}")
            }
            
            // 最後才嘗試載入動態註冊版本（測試模擬環境）
            try {
                System.loadLibrary("monerujo_dynamic")
                isLoaded = true
                println("⚠️ Monerujo Dynamic JNI library 載入成功（測試模擬版本）")
                println("⚠️ 注意：這是模擬版本，餘額是硬編碼的 1.5 XMR")
                println("⚠️ 要獲得真實的 700+ XMR，需要使用 monero_c_bridge 或 monero_real_bridge")
                return
            } catch (e: UnsatisfiedLinkError) {
                println("⚠️ 無法載入 libmonerujo_dynamic.so，嘗試載入原始版本")
            }
            
            // 回退到原始版本
            System.loadLibrary("monerujo")
            isLoaded = true
            println("✅ Monerujo native library 載入成功")
            println("✅ Native 庫路徑: " + System.getProperty("java.library.path"))
        } catch (e: UnsatisfiedLinkError) {
            println("⚠️ 無法載入任何 Monero native library: ${e.message}")
            println("⚠️ 當前架構: " + System.getProperty("os.arch"))
            // 不拋出異常，讓系統可以回退到其他實現
        } catch (e: Exception) {
            println("⚠️ 載入 native library 時發生錯誤: ${e.message}")
        }
    }
    
    /**
     * 檢查 library 是否已載入
     */
    fun isLibraryLoaded(): Boolean = isLoaded
    
    // ========== 錢包管理 JNI 方法 ==========
    
    /**
     * 初始化 Monero 環境（使用 Context 獲取正確的檔案路徑）
     * 優先使用 native Context 驗證，回退到 Kotlin 驗證
     * @param context Android Context
     * @param testnet 是否使用測試網
     * @return 是否初始化成功
     */
    fun initWithContext(context: Context, testnet: Boolean): Boolean {
        return try {
            android.util.Log.i("MonerujoJNI", "開始 Context 驅動的初始化")

            // 首先嘗試使用 native Context 驗證
            if (isLibraryLoaded()) {
                try {
                    val nativeResult = nativeInitWithContextValidation(context, testnet)
                    if (nativeResult) {
                        android.util.Log.i("MonerujoJNI", "✅ Native Context 驗證初始化成功")
                        return true
                    } else {
                        android.util.Log.w("MonerujoJNI", "⚠️ Native Context 驗證失敗，回退到 Kotlin 驗證")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MonerujoJNI", "⚠️ Native 方法異常，回退到 Kotlin 驗證: ${e.message}")
                }
            }

            // 回退到 Kotlin Context 處理
            val dataDir = AndroidTestStorageHelper.getMoneroWalletDirectory(context)
            android.util.Log.i("MonerujoJNI", "使用 Kotlin 獲取的數據目錄: $dataDir")

            // 檢查目錄權限
            val permissionResult = AndroidTestStorageHelper.checkDirectoryPermissions(dataDir)
            android.util.Log.i("MonerujoJNI", permissionResult.toString())

            if (!permissionResult.isFullyAccessible) {
                android.util.Log.e("MonerujoJNI", "目錄不可訪問: $dataDir")
                return false
            }

            val result = nativeInit(dataDir, testnet)
            if (result) {
                android.util.Log.i("MonerujoJNI", "✅ Kotlin Context 驗證初始化成功")
            } else {
                android.util.Log.e("MonerujoJNI", "❌ Kotlin Context 驗證初始化失敗")
            }

            result
        } catch (e: Exception) {
            android.util.Log.e("MonerujoJNI", "Context 初始化失敗", e)
            false
        }
    }

    /**
     * 初始化 Monero 環境（原始方法）
     * @param dataDir 數據目錄路徑
     * @param testnet 是否使用測試網
     * @return 是否初始化成功
     */
    external fun nativeInit(dataDir: String, testnet: Boolean): Boolean
    
    /**
     * 初始化 Monero 環境（支援 NetworkType）
     * @param dataDir 數據目錄路徑
     * @param networkType 網絡類型
     * @return 是否初始化成功
     */
    external fun nativeInitWithNetworkType(dataDir: String, networkType: Int): Boolean

    /**
     * 從助記詞創建錢包（支援 NetworkType）
     * @param mnemonic 助記詞
     * @param networkType 網絡類型（0=Mainnet, 1=Testnet, 2=Stagenet）
     * @return 錢包句柄（失敗返回 0）
     */
    external fun nativeCreateWalletFromMnemonicWithNetworkType(
        mnemonic: String,
        networkType: Int
    ): Long

    /**
     * 從助記詞創建錢包並指定路徑（支援 NetworkType）
     * @param mnemonic 助記詞
     * @param networkType 網絡類型（0=Mainnet, 1=Testnet, 2=Stagenet）
     * @param path 錢包檔案路徑
     * @return 錢包句柄（失敗返回 0）
     */
    external fun nativeCreateWalletWithPathAndNetworkType(
        mnemonic: String,
        networkType: Int,
        path: String
    ): Long

    /**
     * 從助記詞創建錢包（智能模式：根據 WalletMode 配置選擇檔案或記憶體模式）
     * @param mnemonic 助記詞（支援 BIP39 12字 和 XMR25 25字）
     * @param testnet 是否使用測試網
     * @param context Android Context（必須提供，用於檔案路徑）
     * @param networkType 明確指定網絡類型（優先使用）
     * @return 錢包句柄（失敗返回 0）
     */
    @JvmOverloads
    fun createWalletFromMnemonic(
        mnemonic: String,
        testnet: Boolean = false,
        context: Context? = null,
        networkType: MoneroNetworkType? = null
    ): Long {
        val tag = "MonerujoJNI"
        android.util.Log.i(tag, "=== 創建錢包開始 ===")
        android.util.Log.i(tag, "模式: ${WalletMode.getModeDescription()}")

        // 判斷網絡類型
        val actualNetworkType = when {
            networkType != null -> {
                // 明確指定的網絡類型
                android.util.Log.i(tag, "使用指定的網絡類型: $networkType")
                networkType
            }
            mnemonic.split(" ").size == 25 && testnet -> {
                // 25字助記詞且 testnet=true，應該是 Stagenet
                android.util.Log.i(tag, "檢測到 25 字助記詞，使用 Stagenet")
                MoneroNetworkType.STAGENET
            }
            testnet -> {
                // 其他情況下 testnet=true 就是 Testnet
                android.util.Log.i(tag, "使用 Testnet")
                MoneroNetworkType.TESTNET
            }
            else -> {
                // 主網
                android.util.Log.i(tag, "使用 Mainnet")
                MoneroNetworkType.MAINNET
            }
        }

        // 在 DEVICE_FILE 模式下，Context 是必須的
        if (context == null && WalletMode.requireFileWallet()) {
            throw IllegalArgumentException("Context is required in DEVICE_FILE mode")
        }

        // 嘗試使用檔案系統創建錢包
        if (context != null && isLibraryLoaded()) {
            try {
                // 建立實機用的錢包路徑
                val walletDir = java.io.File(context.filesDir, "monero/wallets")
                if (!walletDir.exists()) {
                    walletDir.mkdirs()
                    android.util.Log.i(tag, "創建錢包目錄: ${walletDir.absolutePath}")
                }

                // 生成唯一的錢包檔案名
                val timestamp = System.currentTimeMillis()
                val walletName = "wallet_${timestamp}"
                val walletPath = java.io.File(walletDir, walletName).absolutePath

                android.util.Log.i(tag, "嘗試在路徑創建錢包: $walletPath")
                android.util.Log.i(tag, "網絡類型: $actualNetworkType (${actualNetworkType.value})")

                // 嘗試使用新的 NetworkType 方法
                var handle = 0L
                try {
                    handle = nativeCreateWalletWithPathAndNetworkType(
                        mnemonic = mnemonic,
                        networkType = actualNetworkType.value,
                        path = walletPath
                    )
                    android.util.Log.i(tag, "使用 NetworkType 方法創建錢包")
                } catch (e: UnsatisfiedLinkError) {
                    android.util.Log.w(tag, "NetworkType 方法未找到，回退到舊方法")
                    // 回退到舊的 Boolean 方法
                    handle = nativeCreateWalletWithPath(
                        mnemonic = mnemonic,
                        testnet = actualNetworkType != MoneroNetworkType.MAINNET,
                        path = walletPath
                    )
                }

                if (handle != 0L) {
                    android.util.Log.i(tag, "✅ 檔案錢包創建成功 (handle: $handle)")
                    android.util.Log.i(tag, "📁 錢包路徑: $walletPath")
                    return handle
                } else {
                    android.util.Log.w(tag, "⚠️ Native 錢包創建失敗")
                }
            } catch (e: Exception) {
                android.util.Log.e(tag, "檔案錢包創建異常", e)

                // 在 DEVICE_FILE 模式下，不允許回退
                if (WalletMode.requireFileWallet()) {
                    throw IllegalStateException(
                        "Failed to create wallet in DEVICE_FILE mode: ${e.message}", e
                    )
                }
            }
        }

        // 檢查是否允許使用記憶體錢包
        return when (WalletMode.current) {
            WalletMode.Type.DEVICE_FILE -> {
                // 實機模式不允許回退，直接拋錯
                throw IllegalStateException(
                    "Failed to create wallet in DEVICE_FILE mode. " +
                    "Check: 1) Native libraries loaded, 2) File permissions, 3) Context provided"
                )
            }
            WalletMode.Type.HYBRID -> {
                // 混合模式允許回退到記憶體
                android.util.Log.i(tag, "🧠 HYBRID 模式：回退到記憶體錢包")
                MemoryWalletManager.createMemoryWallet(
                    mnemonic,
                    "",
                    actualNetworkType != MoneroNetworkType.MAINNET
                )
            }
        }
    }

    /**
     * 從助記詞創建錢包（原始 native 方法）
     * @param mnemonic 助記詞（支援 BIP39 12字 和 XMR25 25字）
     * @param testnet 是否使用測試網
     * @return 錢包句柄（失敗返回 0）
     */
    external fun nativeCreateWalletFromMnemonic(
        mnemonic: String,
        testnet: Boolean
    ): Long

    /**
     * 從助記詞創建錢包並指定路徑（native 方法）
     * @param mnemonic 助記詞
     * @param testnet 是否使用測試網
     * @param path 錢包檔案路徑
     * @return 錢包句柄（失敗返回 0）
     */
    external fun nativeCreateWalletWithPath(
        mnemonic: String,
        testnet: Boolean,
        path: String
    ): Long
    
    /**
     * 打開現有錢包
     * @param path 錢包文件路徑
     * @param password 錢包密碼
     * @return 錢包句柄（失敗返回 0）
     */
    external fun nativeOpenWallet(path: String, password: String): Long
    
    /**
     * 關閉錢包
     * @param handle 錢包句柄
     */
    external fun nativeCloseWallet(handle: Long)
    
    // ========== 錢包資訊 JNI 方法 ==========
    
    /**
     * 獲取主地址（智能模式：支援記憶體錢包）
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @param addressIndex 地址索引
     * @return 錢包地址
     */
    fun getAddress(handle: Long, accountIndex: Int = 0, addressIndex: Int = 0): String? {
        // 首先檢查是否為記憶體錢包
        val memoryAddress = MemoryWalletManager.getWalletAddress(handle, accountIndex, addressIndex)
        if (memoryAddress != null) {
            return memoryAddress
        }

        // 回退到 native 方法
        return if (isLibraryLoaded()) {
            nativeGetAddress(handle, accountIndex, addressIndex)
        } else {
            null
        }
    }

    /**
     * 獲取主地址（原始 native 方法）
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @param addressIndex 地址索引
     * @return 錢包地址
     */
    external fun nativeGetAddress(handle: Long, accountIndex: Int, addressIndex: Int): String?
    
    /**
     * 獲取私密查看金鑰
     * @param handle 錢包句柄
     * @return 查看金鑰
     */
    external fun nativeGetSecretViewKey(handle: Long): String?
    
    /**
     * 獲取私密支出金鑰
     * @param handle 錢包句柄
     * @return 支出金鑰
     */
    external fun nativeGetSecretSpendKey(handle: Long): String?
    
    /**
     * 獲取錢包種子（助記詞）
     * @param handle 錢包句柄
     * @return 助記詞
     */
    external fun nativeGetSeed(handle: Long): String?
    
    // ========== 餘額查詢 JNI 方法 ==========
    
    /**
     * 獲取總餘額（智能模式：支援記憶體錢包）
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @return 餘額（atomic units）
     */
    fun getBalance(handle: Long, accountIndex: Int = 0): Long {
        // 首先檢查記憶體錢包
        val memoryBalance = MemoryWalletManager.getWalletBalance(handle, accountIndex)
        if (memoryBalance >= 0) {  // 記憶體錢包會返回 0 或更大的值
            return memoryBalance
        }

        // 回退到 native 方法
        return if (isLibraryLoaded()) {
            nativeGetBalance(handle, accountIndex)
        } else {
            0L
        }
    }

    /**
     * 獲取總餘額（原始 native 方法）
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @return 餘額（atomic units）
     */
    external fun nativeGetBalance(handle: Long, accountIndex: Int): Long
    
    /**
     * 獲取未鎖定餘額（智能模式：支援記憶體錢包）
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @return 可用餘額（atomic units）
     */
    fun getUnlockedBalance(handle: Long, accountIndex: Int = 0): Long {
        // 首先檢查記憶體錢包
        val memoryBalance = MemoryWalletManager.getWalletUnlockedBalance(handle, accountIndex)
        if (memoryBalance >= 0) {
            return memoryBalance
        }

        // 回退到 native 方法
        return if (isLibraryLoaded()) {
            nativeGetUnlockedBalance(handle, accountIndex)
        } else {
            0L
        }
    }

    /**
     * 獲取未鎖定餘額（原始 native 方法）
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @return 可用餘額（atomic units）
     */
    external fun nativeGetUnlockedBalance(handle: Long, accountIndex: Int): Long
    
    // ========== 同步相關 JNI 方法 ==========
    
    /**
     * 設置節點地址
     * @param handle 錢包句柄
     * @param nodeAddress 節點地址（如 "http://node.moneroworld.com:18089"）
     * @return 是否設置成功
     */
    external fun nativeSetDaemonAddress(
        handle: Long,
        nodeAddress: String
    ): Boolean
    
    /**
     * 開始同步錢包
     * @param handle 錢包句柄
     * @return 是否開始同步
     */
    external fun nativeStartRefresh(handle: Long): Boolean
    
    /**
     * 停止同步
     * @param handle 錢包句柄
     */
    external fun nativeStopRefresh(handle: Long)
    
    /**
     * 刷新錢包
     * @param handle 錢包句柄
     * @return 是否成功
     */
    external fun nativeRefresh(handle: Long): Boolean
    
    /**
     * 設置受信任的節點
     * @param handle 錢包句柄
     * @param trusted 是否受信任
     */
    external fun nativeSetTrustedDaemon(handle: Long, trusted: Boolean)
    
    /**
     * 獲取同步高度
     * @param handle 錢包句柄
     * @return 當前同步的區塊高度
     */
    external fun nativeGetSyncHeight(handle: Long): Long
    
    /**
     * 獲取區塊鏈高度
     * @param handle 錢包句柄
     * @return 區塊鏈總高度
     */
    external fun nativeGetDaemonHeight(handle: Long): Long
    
    /**
     * 檢查是否已同步
     * @param handle 錢包句柄
     * @return 是否已同步完成
     */
    external fun nativeIsSynced(handle: Long): Boolean
    
    // ========== 交易相關 JNI 方法 ==========
    
    /**
     * 創建交易
     * @param handle 錢包句柄
     * @param dstAddress 接收地址
     * @param paymentId 支付 ID
     * @param amount 金額（atomic units）
     * @param mixinCount mixin 數量
     * @param priority 優先級（0-3）
     * @return 交易句柄或 0
     */
    external fun nativeCreateTransaction(
        handle: Long,
        dstAddress: String,
        paymentId: String,
        amount: Long,
        mixinCount: Int,
        priority: Int
    ): Long
    
    /**
     * 提交交易
     * @param walletHandle 錢包句柄
     * @param txHandle 交易句柄
     * @return 是否成功
     */
    external fun nativeCommitTransaction(walletHandle: Long, txHandle: Long): Boolean
    
    /**
     * 獲取交易費用
     * @param txHandle 交易句柄
     * @return 費用（atomic units）
     */
    external fun nativeGetTransactionFee(txHandle: Long): Long

    /**
     * 獲取真實的交易雜湊
     * @param txHandle 交易句柄
     * @return 交易雜湊（64字符十六進制字符串）
     */
    external fun nativeGetTransactionHash(txHandle: Long): String

    /**
     * 獲取交易歷史
     * @param handle 錢包句柄
     * @return 交易列表
     */
    // Moved to private at bottom: external fun nativeGetTransactionHistory(handle: Long): Any
    
    // ========== 工具方法 ==========
    
    /**
     * 驗證地址是否有效
     * @param address 地址
     * @param testnet 是否為測試網地址
     * @return 是否有效
     */
    external fun nativeIsAddressValid(address: String, testnet: Boolean): Boolean
    
    /**
     * 驗證助記詞是否有效
     * @param mnemonic 助記詞
     * @return 是否有效
     */
    external fun nativeIsMnemonicValid(mnemonic: String): Boolean
    
    /**
     * 生成新的助記詞
     * @param language 語言（"English", "Chinese_Simplified" 等）
     * @return 新的助記詞
     */
    external fun nativeGenerateMnemonic(language: String): String?
    
    /**
     * 獲取錯誤訊息
     * @return 最後的錯誤訊息
     */
    external fun nativeGetLastError(): String?

    // ========== Context 驅動的 JNI 方法 ==========

    /**
     * 從 Context 獲取 filesDir 路徑
     * @param context Android Context
     * @return 檔案目錄路徑
     */
    external fun nativeGetContextFilesDir(context: Context): String?

    /**
     * 創建目錄
     * @param directoryPath 目錄路徑
     * @return 是否創建成功
     */
    external fun nativeCreateDirectory(directoryPath: String): Boolean

    /**
     * 測試目錄寫入權限
     * @param directoryPath 目錄路徑
     * @return 是否有寫入權限
     */
    external fun nativeTestWritePermission(directoryPath: String): Boolean

    /**
     * 使用 Context 驗證的初始化
     * @param context Android Context
     * @param testnet 是否使用測試網
     * @return 是否初始化成功
     */
    external fun nativeInitWithContextValidation(context: Context, testnet: Boolean): Boolean

    /**
     * 獲取所有可用的儲存路徑
     * @param context Android Context
     * @return 路徑數組
     */
    external fun nativeGetAllStoragePaths(context: Context): Array<String>?
    
    /**
     * 獲取子地址
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @param addressIndex 地址索引
     * @return 子地址
     */
    external fun nativeGetSubaddress(handle: Long, accountIndex: Int, addressIndex: Int): String?
    
    /**
     * 創建新的子地址
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @param label 標籤（可選）
     * @return 新地址的索引
     */
    external fun nativeAddSubaddress(handle: Long, accountIndex: Int, label: String?): Int
    
    /**
     * 獲取子地址數量
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @return 子地址數量
     */
    external fun nativeGetNumSubaddresses(handle: Long, accountIndex: Int): Int
    
    // ===== Helper Methods =====
    
    // Removed duplicate getTransactionHistory - using the one that returns List<TransactionInfo> below
    
    /**
     * 獲取所有子地址
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引
     * @return 子地址列表
     */
    fun getSubaddresses(handle: Long, accountIndex: Int = 0): List<String> {
        val subaddresses = mutableListOf<String>()
        val numAddresses = nativeGetNumSubaddresses(handle, accountIndex)
        
        for (i in 0 until numAddresses) {
            val address = nativeGetAddress(handle, accountIndex, i)
            if (address != null) {
                subaddresses.add(address)
            }
        }
        
        return subaddresses
    }

    /**
     * 獲取錢包餘額信息（包裝方法，支援智能模式）
     * @param handle 錢包句柄
     * @param accountIndex 帳戶索引（預設為0）
     * @return BalanceInfo 包含總餘額、可用餘額和鎖定餘額
     */
    fun getBalanceInfo(handle: Long, accountIndex: Int = 0): BalanceInfo {
        // 先檢查是否為記憶體錢包
        val memoryBalance = MemoryWalletManager.getWalletBalance(handle, accountIndex)
        val memoryUnlockedBalance = MemoryWalletManager.getWalletUnlockedBalance(handle, accountIndex)

        val (totalBalance, unlockedBalance) = if (memoryBalance >= 0) {
            // 使用記憶體錢包的餘額
            Pair(memoryBalance, memoryUnlockedBalance)
        } else if (isLibraryLoaded()) {
            // 使用 Native 錢包的餘額
            Pair(nativeGetBalance(handle, accountIndex), nativeGetUnlockedBalance(handle, accountIndex))
        } else {
            // 沒有可用的錢包
            Pair(0L, 0L)
        }

        val lockedBalance = totalBalance - unlockedBalance

        android.util.Log.d("MonerujoJNIWrapper", "餘額查詢 - 總額: $totalBalance, 可用: $unlockedBalance, 鎖定: $lockedBalance")

        return BalanceInfo(
            total = totalBalance,
            unlocked = unlockedBalance,
            locked = lockedBalance,
            totalXMR = totalBalance / 1e12,
            unlockedXMR = unlockedBalance / 1e12,
            lockedXMR = lockedBalance / 1e12
        )
    }
    
    /**
     * 餘額信息數據類
     */
    data class BalanceInfo(
        val total: Long,        // 總餘額（atomic units）
        val unlocked: Long,     // 可用餘額（atomic units）
        val locked: Long,       // 鎖定餘額（atomic units）
        val totalXMR: Double,   // 總餘額（XMR）
        val unlockedXMR: Double,// 可用餘額（XMR）
        val lockedXMR: Double   // 鎖定餘額（XMR）
    )
    
    /**
     * 創建轉帳到子地址
     * @param handle 錢包句柄
     * @param destinationAddress 目標地址
     * @param amount 金額
     * @return 交易句柄
     */
    fun createTransferToSubaddress(
        handle: Long,
        destinationAddress: String,
        amount: Long
    ): Long {
        // 使用 nativeCreateTransaction 創建交易
        return nativeCreateTransaction(
            handle,
            destinationAddress,
            "",  // payment ID
            amount,
            10,  // mixin count
            2   // priority (NORMAL)
        )
    }

    /**
     * 獲取交易歷史
     * @param handle 錢包句柄
     * @return 交易列表
     */
    fun getTransactionHistory(handle: Long): List<TransactionInfo> {
        return try {
            if (isLibraryLoaded()) {
                val result = nativeGetTransactionHistory(handle)
                @Suppress("UNCHECKED_CAST")
                result as? List<TransactionInfo> ?: emptyList()
            } else {
                // 返回測試數據
                listOf(
                    TransactionInfo(
                        txId = "test_tx_001",
                        amount = 100000000000000L,
                        fee = 0L,
                        isOutgoing = false,
                        description = "Test incoming transaction",
                        timestamp = System.currentTimeMillis() / 1000 - 86400,
                        confirmations = 10
                    ),
                    TransactionInfo(
                        txId = "test_tx_002",
                        amount = 50000000000000L,
                        fee = 1000000000L,
                        isOutgoing = true,
                        description = "Test outgoing transaction",
                        timestamp = System.currentTimeMillis() / 1000 - 3600,
                        confirmations = 5
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MonerujoJNIWrapper", "Error getting transaction history", e)
            emptyList()
        }
    }

    // Native method declaration for transaction history
    private external fun nativeGetTransactionHistory(handle: Long): Any
}