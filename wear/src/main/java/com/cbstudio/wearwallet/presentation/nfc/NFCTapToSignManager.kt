package com.cbstudio.wearwallet.presentation.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.*
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.nio.ByteBuffer
import org.koin.core.component.KoinComponent
import javax.inject.Singleton

/**
 * Simplified NFC Manager
 * 
 * Basic NFC functionality:
 * 1. Simple NFC tag reading
 * 2. Basic merchant detection
 * 3. Removed HCE dependencies for KMP compatibility
 */
@Singleton
class NFCTapToSignManager : DefaultLifecycleObserver {
    
    private var nfcAdapter: NfcAdapter? = null
    private var activity: Activity? = null
    private var pendingTransaction: PendingNFCTransaction? = null
    
    // NFC 通訊協議常量
    companion object {
        // APDU 命令
        private const val SELECT_AID_HEADER = "00A40400"
        private const val KEYSTONE_AID = "F0434253545544494F" // "CBSTUDIO"的十六進制
        
        // 自定義指令
        const val INS_GET_VERSION = 0x01.toByte()
        const val INS_SIGN_TRANSACTION = 0x02.toByte()
        const val INS_GET_ADDRESS = 0x03.toByte()
        const val INS_VERIFY_PIN = 0x04.toByte()
        
        // 狀態碼
        private const val SW_SUCCESS = 0x9000
        private const val SW_WRONG_PIN = 0x6982
        private const val SW_COMMAND_NOT_ALLOWED = 0x6986
        
        // NFC 動作
        const val ACTION_NFC_SIGN_REQUEST = "com.cbstudio.wearwallet.NFC_SIGN_REQUEST"
        const val ACTION_NFC_SIGN_RESPONSE = "com.cbstudio.wearwallet.NFC_SIGN_RESPONSE"
    }
    
    // 事件通道
    private val nfcEventChannel = Channel<NFCEvent>(Channel.BUFFERED)
    val nfcEvents: Flow<NFCEvent> = nfcEventChannel.receiveAsFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 初始化 NFC 管理器
     */
    fun initialize(activity: Activity) {
        this.activity = activity
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        
        if (nfcAdapter == null) {
            Timber.w("此設備不支援 NFC")
            nfcEventChannel.trySend(NFCEvent.NotSupported)
            return
        }
        
        if (!nfcAdapter!!.isEnabled) {
            Timber.w("NFC 未啟用")
            nfcEventChannel.trySend(NFCEvent.Disabled)
            return
        }
        
        Timber.d("NFC Tap-to-Sign 管理器初始化成功")
    }
    
    /**
     * 啟用 NFC 讀取模式（讀取 Keystone 硬體錢包）
     */
    fun enableReaderMode(transaction: PendingNFCTransaction) {
        val activity = this.activity ?: return
        val adapter = this.nfcAdapter ?: return
        
        pendingTransaction = transaction
        
        val flags = NfcAdapter.FLAG_READER_NFC_A or 
                   NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 100)
        }
        
        adapter.enableReaderMode(
            activity,
            { tag -> handleNfcTag(tag) },
            flags,
            options
        )
        
        nfcEventChannel.trySend(NFCEvent.ReaderModeEnabled)
        Timber.d("NFC 讀取模式已啟用，等待 Keystone 硬體錢包...")
    }
    
    /**
     * 停用 NFC 讀取模式
     */
    fun disableReaderMode() {
        val activity = this.activity ?: return
        val adapter = this.nfcAdapter ?: return
        
        adapter.disableReaderMode(activity)
        pendingTransaction = null
        
        nfcEventChannel.trySend(NFCEvent.ReaderModeDisabled)
        Timber.d("NFC 讀取模式已停用")
    }
    
    /**
     * 處理檢測到的 NFC 標籤
     */
    private fun handleNfcTag(tag: Tag) {
        coroutineScope.launch {
            try {
                nfcEventChannel.send(NFCEvent.TagDetected)
                
                // 嘗試使用 IsoDep 協議
                val isoDep = IsoDep.get(tag)
                if (isoDep != null) {
                    handleIsoDepCommunication(isoDep)
                } else {
                    // 回退到 NfcA
                    val nfcA = NfcA.get(tag)
                    if (nfcA != null) {
                        handleNfcACommunication(nfcA)
                    } else {
                        throw Exception("不支援的 NFC 標籤類型")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "處理 NFC 標籤時出錯")
                nfcEventChannel.send(NFCEvent.Error(e.message ?: "未知錯誤"))
            }
        }
    }
    
    /**
     * 處理 ISO-DEP 通訊（主要協議）
     */
    private suspend fun handleIsoDepCommunication(isoDep: IsoDep) {
        isoDep.connect()
        
        try {
            // 1. 選擇應用（SELECT AID）
            val selectCommand = hexStringToByteArray(SELECT_AID_HEADER + KEYSTONE_AID)
            val selectResponse = isoDep.transceive(selectCommand)
            
            if (!isResponseSuccess(selectResponse)) {
                throw Exception("無法選擇 Keystone 應用")
            }
            
            // 2. 獲取版本信息（可選）
            val versionCommand = byteArrayOf(0x00, INS_GET_VERSION, 0x00, 0x00)
            val versionResponse = isoDep.transceive(versionCommand)
            
            val version = parseVersion(versionResponse)
            Timber.d("Keystone 版本: $version")
            
            // 3. 發送交易進行簽名
            val transaction = pendingTransaction ?: throw Exception("無待簽名交易")
            val signedTx = signTransaction(isoDep, transaction)
            
            // 4. 發送簽名結果
            nfcEventChannel.send(NFCEvent.TransactionSigned(signedTx))
            
        } finally {
            isoDep.close()
        }
    }
    
    /**
     * 處理 NFC-A 通訊（備用協議）
     */
    private suspend fun handleNfcACommunication(nfcA: NfcA) {
        // 簡化的 NFC-A 協議實現
        nfcA.connect()
        
        try {
            // 實現基本的 NFC-A 通訊
            Timber.d("使用 NFC-A 協議")
            
            // TODO: 實現 NFC-A 協議細節
            
        } finally {
            nfcA.close()
        }
    }
    
    /**
     * 簽名交易
     */
    private suspend fun signTransaction(
        isoDep: IsoDep,
        transaction: PendingNFCTransaction
    ): String {
        // 1. 準備交易數據
        val txData = prepareTransactionData(transaction)
        
        // 2. 發送簽名命令
        val signCommand = ByteBuffer.allocate(5 + txData.size).apply {
            put(0x00) // CLA
            put(INS_SIGN_TRANSACTION) // INS
            put(0x00) // P1
            put(0x00) // P2
            put(txData.size.toByte()) // Lc
            put(txData)
        }.array()
        
        val signResponse = isoDep.transceive(signCommand)
        
        if (!isResponseSuccess(signResponse)) {
            throw Exception("交易簽名失敗")
        }
        
        // 3. 解析簽名結果
        return parseSignature(signResponse)
    }
    
    /**
     * 準備交易數據
     */
    private fun prepareTransactionData(transaction: PendingNFCTransaction): ByteArray {
        // 將交易數據序列化為字節數組
        return transaction.unsignedTxHex.hexToByteArray()
    }
    
    /**
     * 解析簽名
     */
    private fun parseSignature(response: ByteArray): String {
        // 移除狀態字節 (SW1 SW2)
        val signatureBytes = response.sliceArray(0 until response.size - 2)
        return signatureBytes.toHexString()
    }
    
    /**
     * 解析版本信息
     */
    private fun parseVersion(response: ByteArray): String {
        if (response.size < 3) return "Unknown"
        
        val major = response[0].toInt()
        val minor = response[1].toInt()
        val patch = response[2].toInt()
        
        return "$major.$minor.$patch"
    }
    
    /**
     * 檢查響應是否成功
     */
    private fun isResponseSuccess(response: ByteArray): Boolean {
        if (response.size < 2) return false
        
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        val status = (sw1 shl 8) or sw2
        
        return status == SW_SUCCESS
    }
    
    /**
     * Basic NFC tag detection - Removed HCE for KMP compatibility
     */
    fun detectBasicNFCTags() {
        Timber.d("Basic NFC detection enabled (HCE removed for KMP compatibility)")
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        disableReaderMode()
        coroutineScope.cancel()
        activity = null
        super.onDestroy(owner)
    }
    
    // 工具函數
    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                          Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
    
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
    
    private fun String.hexToByteArray(): ByteArray {
        return hexStringToByteArray(this)
    }
}

/**
 * 待簽名的 NFC 交易
 */
data class PendingNFCTransaction(
    val unsignedTxHex: String,
    val derivationPath: String,
    val chainId: Long,
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val gasPrice: String? = null,
    val gasLimit: String? = null
)

/**
 * NFC 事件
 */
sealed class NFCEvent {
    object NotSupported : NFCEvent()
    object Disabled : NFCEvent()
    object ReaderModeEnabled : NFCEvent()
    object ReaderModeDisabled : NFCEvent()
    object TagDetected : NFCEvent()
    data class TransactionSigned(val signedTx: String) : NFCEvent()
    data class Error(val message: String) : NFCEvent()
    data class Progress(val message: String) : NFCEvent()
}

// HCE Service removed for KMP compatibility
// Complex payment processing replaced with basic NFC detection
