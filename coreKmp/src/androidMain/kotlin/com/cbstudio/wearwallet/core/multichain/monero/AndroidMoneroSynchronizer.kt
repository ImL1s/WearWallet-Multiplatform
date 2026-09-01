package com.cbstudio.wearwallet.core.multichain.monero

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * Android 平台專用的 Monero 同步器
 * 使用 JNI wrapper 直接調用 native Monero 功能
 */
class AndroidMoneroSynchronizer(
    private val context: android.content.Context  // 添加 Context 參數
) {
    
    // 錢包句柄快取
    private val walletHandles = mutableMapOf<String, Long>()
    
    /**
     * 同步錢包並獲取餘額
     * @param walletId 錢包 ID
     * @param mnemonic 助記詞
     * @param daemonUrl 節點地址
     * @return 同步結果
     */
    suspend fun syncAndGetBalance(
        walletId: String,
        mnemonic: String,
        daemonUrl: String
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            println("🔄 AndroidMoneroSynchronizer: 開始同步錢包")
            println("   錢包 ID: $walletId")
            println("   節點: $daemonUrl")
            
            // 確保 native library 已載入
            if (!MonerujoJNIWrapper.isLibraryLoaded()) {
                println("📚 載入 Native Library...")
                MonerujoJNIWrapper.loadLibrary()
            }
            
            // 判斷網絡類型
            val wordCount = mnemonic.split(" ").size
            val networkType = when {
                // 25字助記詞且節點包含 stagenet 或端口 38081/38089/18089
                wordCount == 25 && (daemonUrl.contains("stagenet") ||
                                   daemonUrl.contains(":38081") ||
                                   daemonUrl.contains(":38089") ||
                                   daemonUrl.contains(":18089") ||
                                   daemonUrl.contains("54.153.251.193")) -> {
                    println("   網路: Stagenet (25字助記詞 + Stagenet 節點)")
                    MoneroNetworkType.STAGENET
                }
                // 25字助記詞且節點包含 testnet 或端口 28081
                wordCount == 25 && (daemonUrl.contains("testnet") ||
                                   daemonUrl.contains(":28081")) -> {
                    println("   網路: Testnet (25字助記詞 + Testnet 節點)")
                    MoneroNetworkType.TESTNET
                }
                // 13字助記詞是主網
                wordCount == 13 -> {
                    println("   網路: Mainnet (13字助記詞)")
                    MoneroNetworkType.MAINNET
                }
                // 預設：25字用 Stagenet，其他用 Mainnet
                wordCount == 25 -> {
                    println("   網路: Stagenet (預設 25字助記詞)")
                    MoneroNetworkType.STAGENET
                }
                else -> {
                    println("   網路: Mainnet (預設)")
                    MoneroNetworkType.MAINNET
                }
            }

            val isTestnet = networkType != MoneroNetworkType.MAINNET
            
            // 初始化 JNI 環境 - 使用正確的 Android 文件目錄
            val initSuccess = MonerujoJNIWrapper.initWithContext(context, isTestnet)
            if (!initSuccess) {
                println("❌ JNI 初始化失敗")
                return@withContext Result.Failure(Exception("Failed to initialize JNI"))
            }
            
            // 創建或獲取錢包句柄 - 使用帶 Context 和 NetworkType 的方法
            var walletHandle = walletHandles[walletId]
            if (walletHandle == null || walletHandle == 0L) {
                println("🔑 創建新錢包句柄...")
                println("   使用網絡類型: $networkType (${networkType.value})")
                
                // 使用正確的方法，傳遞 context 和 networkType
                walletHandle = MonerujoJNIWrapper.createWalletFromMnemonic(
                    mnemonic = mnemonic,
                    testnet = isTestnet,
                    context = context,
                    networkType = networkType  // 傳遞網絡類型
                )
                
                if (walletHandle == 0L) {
                    val error = MonerujoJNIWrapper.nativeGetLastError() ?: "Unknown error"
                    println("❌ 無法創建錢包: $error")
                    return@withContext Result.Failure(Exception("Failed to create wallet: $error"))
                }
                
                walletHandles[walletId] = walletHandle
                println("✅ 錢包句柄創建成功: $walletHandle")
            } else {
                println("♻️ 使用快取的錢包句柄: $walletHandle")
            }
            
            // 設置節點地址
            println("🌐 設置節點地址: $daemonUrl")
            val daemonSet = MonerujoJNIWrapper.nativeSetDaemonAddress(walletHandle, daemonUrl)
            if (!daemonSet) {
                println("❌ 無法設置節點地址")
                return@withContext Result.Failure(Exception("Failed to set daemon address"))
            }
            
            // 設置為受信任節點（stagenet 節點通常是受信任的）
            MonerujoJNIWrapper.nativeSetTrustedDaemon(walletHandle, true)
            
            // 開始同步前先執行一次 refresh 來初始化連接
            println("🔌 初始化節點連接...")
            MonerujoJNIWrapper.nativeRefresh(walletHandle)
            delay(2000) // 等待初始連接
            
            // 開始同步
            println("🔄 開始同步錢包...")
            val refreshStarted = MonerujoJNIWrapper.nativeStartRefresh(walletHandle)
            if (!refreshStarted) {
                println("❌ 無法開始同步")
                return@withContext Result.Failure(Exception("Failed to start refresh"))
            }
            
            // 等待同步完成（最多等待 60 秒）
            var syncAttempts = 0
            val maxAttempts = 60
            var lastHeight = 0L
            var stableHeightCount = 0
            var hasConnected = false
            
            while (syncAttempts < maxAttempts) {
                delay(1000) // 等待 1 秒
                
                val currentHeight = MonerujoJNIWrapper.nativeGetSyncHeight(walletHandle)
                val daemonHeight = MonerujoJNIWrapper.nativeGetDaemonHeight(walletHandle)
                val isSynced = MonerujoJNIWrapper.nativeIsSynced(walletHandle)
                
                // 檢查是否成功連接到節點
                if (!hasConnected && daemonHeight > 0) {
                    hasConnected = true
                    println("✅ 成功連接到節點，節點高度: $daemonHeight")
                }
                
                println("   同步進度: $currentHeight/$daemonHeight (已同步: $isSynced)")
                
                // 如果節點高度大於 0 且錢包高度接近節點高度，認為同步完成
                if (daemonHeight > 0 && currentHeight >= daemonHeight - 10) {
                    println("✅ 同步完成!")
                    break
                }
                
                // 如果 isSynced 為 true，也認為同步完成
                if (isSynced) {
                    println("✅ 同步狀態: 已同步")
                    break
                }
                
                // 如果高度長時間不變，可能已經同步完成
                if (currentHeight == lastHeight) {
                    stableHeightCount++
                    if (stableHeightCount >= 5 && currentHeight > 0) {
                        println("✅ 高度穩定，同步可能已完成")
                        break
                    }
                } else {
                    stableHeightCount = 0
                }
                
                lastHeight = currentHeight
                syncAttempts++
                
                // 每 10 秒執行一次手動 refresh
                if (syncAttempts % 10 == 0) {
                    println("🔄 執行手動 refresh...")
                    MonerujoJNIWrapper.nativeRefresh(walletHandle)
                }
            }
            
            // 如果超時但有一定高度，也繼續
            if (syncAttempts >= maxAttempts) {
                val finalHeight = MonerujoJNIWrapper.nativeGetSyncHeight(walletHandle)
                println("⚠️ 同步超時，當前高度: $finalHeight")
                
                // 如果從未連接到節點，返回錯誤
                if (!hasConnected) {
                    return@withContext Result.Failure(Exception("無法連接到節點: $daemonUrl"))
                }
            }
            
            // 停止同步
            MonerujoJNIWrapper.nativeStopRefresh(walletHandle)
            
            // 獲取餘額
            val balanceInfo = MonerujoJNIWrapper.getBalanceInfo(walletHandle, 0)

            println("💰 餘額資訊:")
            println("   總餘額: ${balanceInfo.totalXMR} XMR")
            println("   可用餘額: ${balanceInfo.unlockedXMR} XMR")
            println("   鎖定餘額: ${balanceInfo.lockedXMR} XMR")

            // 獲取地址
            val address = MonerujoJNIWrapper.nativeGetAddress(walletHandle, 0, 0)
            println("📍 地址: $address")
            
            // 驗證地址前綴是否符合預期網絡類型
            val expectedPrefix = when (networkType) {
                MoneroNetworkType.MAINNET -> "4"
                MoneroNetworkType.TESTNET -> "9"
                MoneroNetworkType.STAGENET -> "5"
            }
            
            val actualPrefix = address?.firstOrNull()?.toString() ?: "?"
            if (actualPrefix != expectedPrefix && expectedPrefix != "9") { // Testnet 可能是 9 或 A
                println("⚠️ 地址前綴不匹配! 預期: $expectedPrefix, 實際: $actualPrefix")
            }

            Result.Success(SyncResult(
                totalBalance = balanceInfo.total,
                unlockedBalance = balanceInfo.unlocked,
                totalXmr = balanceInfo.totalXMR,
                unlockedXmr = balanceInfo.unlockedXMR,
                address = address ?: "",
                syncHeight = MonerujoJNIWrapper.nativeGetSyncHeight(walletHandle),
                daemonHeight = MonerujoJNIWrapper.nativeGetDaemonHeight(walletHandle)
            ))
            
        } catch (e: Exception) {
            println("❌ 同步失敗: ${e.message}")
            e.printStackTrace()
            Result.Failure(e)
        }
    }
    
    /**
     * 創建交易
     */
    suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amountXmr: Double
    ): Result<TransactionResult> = withContext(Dispatchers.IO) {
        try {
            val walletHandle = walletHandles[walletId] 
                ?: return@withContext Result.Failure(Exception("錢包未初始化"))
            
            println("💸 創建交易:")
            println("   收款地址: $toAddress")
            println("   金額: $amountXmr XMR")
            
            // 轉換為 atomic units (1 XMR = 10^12 atomic units)
            val amountAtomic = (amountXmr * 1e12).toLong()
            
            // 創建交易
            val txHandle = MonerujoJNIWrapper.nativeCreateTransaction(
                walletHandle,
                toAddress,
                "", // payment ID
                amountAtomic,
                10, // mixin count
                2   // priority (NORMAL)
            )
            
            if (txHandle == 0L) {
                val error = MonerujoJNIWrapper.nativeGetLastError() ?: "Unknown error"
                println("❌ 無法創建交易: $error")
                return@withContext Result.Failure(Exception("Failed to create transaction: $error"))
            }
            
            // 獲取手續費
            val fee = MonerujoJNIWrapper.nativeGetTransactionFee(txHandle)
            println("   手續費: ${fee / 1e12} XMR")
            
            // 提交交易
            val committed = MonerujoJNIWrapper.nativeCommitTransaction(walletHandle, txHandle)
            if (!committed) {
                val error = MonerujoJNIWrapper.nativeGetLastError() ?: "Unknown error"
                println("❌ 無法提交交易: $error")
                return@withContext Result.Failure(Exception("Failed to commit transaction: $error"))
            }
            
            println("✅ 交易提交成功!")
            
            Result.Success(TransactionResult(
                txId = "tx_${System.currentTimeMillis()}", // 實際的 TX ID 需要從 JNI 獲取
                fee = fee,
                amount = amountAtomic
            ))
            
        } catch (e: Exception) {
            println("❌ 創建交易失敗: ${e.message}")
            e.printStackTrace()
            Result.Failure(e)
        }
    }
    
    /**
     * 清理資源
     */
    fun dispose() {
        walletHandles.forEach { (walletId, handle) ->
            if (handle != 0L) {
                println("🧹 關閉錢包句柄: $walletId")
                MonerujoJNIWrapper.nativeCloseWallet(handle)
            }
        }
        walletHandles.clear()
    }
    
    data class SyncResult(
        val totalBalance: Long,
        val unlockedBalance: Long,
        val totalXmr: Double,
        val unlockedXmr: Double,
        val address: String,
        val syncHeight: Long,
        val daemonHeight: Long
    )
    
    data class TransactionResult(
        val txId: String,
        val fee: Long,
        val amount: Long
    )
}