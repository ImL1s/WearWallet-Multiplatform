package com.cbstudio.wearwallet.data.repository

import android.content.Context
import com.google.android.gms.wearable.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Wear OS 通訊儲存庫
 * 處理手錶和手機之間的通訊
 */
class WearCommunicationRepository private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: WearCommunicationRepository? = null
        
        fun getInstance(context: Context): WearCommunicationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WearCommunicationRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
        
        // 路徑常量
        const val QR_SCAN_REQUEST_PATH = "/qr_scan_request"
        const val QR_SCAN_RESULT_PATH = "/qr_scan_result"
        const val SIGN_TX_REQUEST_PATH = "/sign_tx_request"
        const val SIGNED_TX_RESULT_PATH = "/signed_tx_result"
        const val KEYSTONE_CONNECT_REQUEST_PATH = "/keystone_connect_request"
        const val KEYSTONE_CONNECT_RESULT_PATH = "/keystone_connect_result"
    }
    
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    
    // QR 掃描結果流
    private val _qrScanResult = MutableSharedFlow<String>()
    val qrScanResult: SharedFlow<String> = _qrScanResult.asSharedFlow()
    
    // 簽名交易結果流
    private val _signedTxResult = MutableSharedFlow<String>()
    val signedTxResult: SharedFlow<String> = _signedTxResult.asSharedFlow()
    
    // Keystone 連接結果流
    private val _keystoneConnectResult = MutableSharedFlow<String>()
    val keystoneConnectResult: SharedFlow<String> = _keystoneConnectResult.asSharedFlow()
    
    // 錯誤訊息流
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()
    
    /**
     * 請求手機掃描 QR Code
     */
    suspend fun requestQRScan(type: QRScanType = QRScanType.ADDRESS): Boolean {
        return try {
            val connectedNodes = nodeClient.connectedNodes.await()
            if (connectedNodes.isEmpty()) {
                _errorMessage.emit("請確保手機已連接")
                return false
            }
            
            // 發送訊息到所有連接的節點（通常是手機）
            connectedNodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    QR_SCAN_REQUEST_PATH,
                    type.name.toByteArray()
                ).await()
            }
            
            Timber.d("已發送 QR 掃描請求到 ${connectedNodes.size} 個設備")
            true
        } catch (e: Exception) {
            Timber.e(e, "發送 QR 掃描請求失敗")
            _errorMessage.emit("無法發送掃描請求: ${e.message}")
            false
        }
    }
    
    /**
     * 請求簽名交易
     */
    suspend fun requestTransactionSign(txData: String): Boolean {
        return try {
            val connectedNodes = nodeClient.connectedNodes.await()
            if (connectedNodes.isEmpty()) {
                _errorMessage.emit("請確保手機已連接")
                return false
            }
            
            connectedNodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    SIGN_TX_REQUEST_PATH,
                    txData.toByteArray()
                ).await()
            }
            
            Timber.d("已發送交易簽名請求")
            true
        } catch (e: Exception) {
            Timber.e(e, "發送交易簽名請求失敗")
            _errorMessage.emit("無法發送簽名請求: ${e.message}")
            false
        }
    }
    
    /**
     * 請求 Keystone 連接
     */
    suspend fun requestKeystoneConnect(): Boolean {
        return try {
            val connectedNodes = nodeClient.connectedNodes.await()
            if (connectedNodes.isEmpty()) {
                _errorMessage.emit("請確保手機已連接")
                return false
            }
            
            connectedNodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    KEYSTONE_CONNECT_REQUEST_PATH,
                    ByteArray(0)
                ).await()
            }
            
            Timber.d("已發送 Keystone 連接請求")
            true
        } catch (e: Exception) {
            Timber.e(e, "發送 Keystone 連接請求失敗")
            _errorMessage.emit("無法發送連接請求: ${e.message}")
            false
        }
    }
    
    /**
     * 處理 QR 掃描結果
     */
    suspend fun onQRScanResult(result: String) {
        Timber.d("收到 QR 掃描結果: $result")
        _qrScanResult.emit(result)
    }
    
    /**
     * 處理簽名交易結果
     */
    suspend fun onSignedTxResult(result: String) {
        Timber.d("收到簽名交易結果")
        _signedTxResult.emit(result)
    }
    
    /**
     * 處理 Keystone 連接結果
     */
    suspend fun onKeystoneConnectResult(result: String) {
        Timber.d("收到 Keystone 連接結果")
        _keystoneConnectResult.emit(result)
    }
    
    /**
     * 處理地址簿同步
     */
    suspend fun onAddressBookSync(contactsJson: String) {
        Timber.d("收到地址簿同步數據")
        // 可以在這裡實作地址簿同步邏輯
    }
    
    /**
     * 處理地址簿新增
     */
    suspend fun onAddressBookAdd(contactJson: String) {
        Timber.d("收到地址簿新增聯絡人")
        // 可以在這裡實作新增聯絡人邏輯
    }
    
    /**
     * 處理地址簿更新
     */
    suspend fun onAddressBookUpdate(contactJson: String) {
        Timber.d("收到地址簿更新聯絡人")
        // 可以在這裡實作更新聯絡人邏輯
    }
    
    /**
     * 處理地址簿刪除
     */
    suspend fun onAddressBookDelete(contactId: String) {
        Timber.d("收到地址簿刪除聯絡人: $contactId")
        // 可以在這裡實作刪除聯絡人邏輯
    }
    
    /**
     * 檢查手機是否已連接
     */
    suspend fun isPhoneConnected(): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            nodes.isNotEmpty()
        } catch (e: Exception) {
            Timber.e(e, "檢查連接狀態失敗")
            false
        }
    }
    
    /**
     * 獲取連接的設備數量
     */
    suspend fun getConnectedDeviceCount(): Int {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            nodes.size
        } catch (e: Exception) {
            Timber.e(e, "獲取連接設備失敗")
            0
        }
    }
}

/**
 * QR 掃描類型
 */
enum class QRScanType {
    ADDRESS,          // 錢包地址
    TRANSACTION,      // 交易數據
    KEYSTONE_CONNECT, // Keystone 連接
    KEYSTONE_SIGN     // Keystone 簽名
}