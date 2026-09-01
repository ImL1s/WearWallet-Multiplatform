package com.cbstudio.wearwallet.presentation.service

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class WearCommunicationService : WearableListenerService() {

    companion object {
        const val QR_SCAN_REQUEST_PATH = "/qr_scan_request"
        const val QR_SCAN_RESULT_PATH = "/qr_scan_result"
        const val SIGN_TX_SCAN_REQUEST_PATH = "/sign_tx_scan_request"
        const val SIGNED_TX_RESULT_PATH = "/signed_tx_result"
        
        // Keystone 連接相關路徑
        const val KEYSTONE_CONNECT_SCAN_REQUEST_PATH = "/keystone_connect_scan_request"
        const val KEYSTONE_CONNECT_RESULT_PATH = "/keystone_connect_result"
        
        // 地址簿同步相關路徑
        const val ADDRESS_BOOK_SYNC_PATH = "/address_book_sync"
        const val ADDRESS_BOOK_ADD_PATH = "/address_book_add"
        const val ADDRESS_BOOK_UPDATE_PATH = "/address_book_update"
        const val ADDRESS_BOOK_DELETE_PATH = "/address_book_delete"
        
        private const val TAG = "WearCommunicationService"    }

    private lateinit var communicationRepository: WearCommunicationRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        communicationRepository = WearCommunicationRepository.getInstance()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                when (event.dataItem.uri.path) {
                    QR_SCAN_RESULT_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val address = dataMapItem.dataMap.getString("address")
                        if (address != null) {
                            serviceScope.launch {
                                communicationRepository.onQRScanResult(address)
                            }
                        }
                    }
                    SIGNED_TX_RESULT_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val tx = dataMapItem.dataMap.getString("address")
                        if (tx != null) {
                            serviceScope.launch {
                                communicationRepository.onSignedTxResult(tx)
                            }
                        }
                    }
                    KEYSTONE_CONNECT_RESULT_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val urData = dataMapItem.dataMap.getString("address") // 使用相同的 key "address"
                        if (urData != null) {
                            serviceScope.launch {
                                communicationRepository.onKeystoneConnectResult(urData)
                            }
                        }
                    }
                    ADDRESS_BOOK_SYNC_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val contactsJson = dataMapItem.dataMap.getString("contacts")
                        if (contactsJson != null) {
                            serviceScope.launch {
                                communicationRepository.onAddressBookSync(contactsJson)
                            }
                        }
                    }
                    ADDRESS_BOOK_ADD_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val contactJson = dataMapItem.dataMap.getString("contact")
                        if (contactJson != null) {
                            serviceScope.launch {
                                communicationRepository.onAddressBookAdd(contactJson)
                            }
                        }
                    }
                    ADDRESS_BOOK_UPDATE_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val contactJson = dataMapItem.dataMap.getString("contact")
                        if (contactJson != null) {
                            serviceScope.launch {
                                communicationRepository.onAddressBookUpdate(contactJson)
                            }
                        }
                    }
                    ADDRESS_BOOK_DELETE_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val contactId = dataMapItem.dataMap.getString("contactId")
                        if (contactId != null) {
                            serviceScope.launch {
                                communicationRepository.onAddressBookDelete(contactId)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        
        when (messageEvent.path) {
            QR_SCAN_REQUEST_PATH -> {
                // 目前手表端不需要處理此消息，因為是手表發送給手機的
                // 但為了完整性和未來擴展，保留此方法
                Timber.tag(TAG).d("收到消息: ${messageEvent.path}")
            }
            SIGN_TX_SCAN_REQUEST_PATH -> {
                Timber.tag(TAG).d("收到消息: ${messageEvent.path}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

class WearCommunicationRepository {
    
    companion object {
        @Volatile
        private var INSTANCE: WearCommunicationRepository? = null
        
        fun getInstance(): WearCommunicationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WearCommunicationRepository().also { INSTANCE = it }
            }
        }    }
    
    private val _qrScanResults = MutableSharedFlow<String>()
    val qrScanResults = _qrScanResults.asSharedFlow()
    private val _signedTxResults = MutableSharedFlow<String>()
    val signedTxResults = _signedTxResults.asSharedFlow()
    private val _keystoneConnectResults = MutableSharedFlow<String>()
    val keystoneConnectResults = _keystoneConnectResults.asSharedFlow()
    
    // 地址簿同步相關的 Flow
    private val _addressBookSync = MutableSharedFlow<String>()
    val addressBookSync = _addressBookSync.asSharedFlow()
    private val _addressBookAdd = MutableSharedFlow<String>()
    val addressBookAdd = _addressBookAdd.asSharedFlow()
    private val _addressBookUpdate = MutableSharedFlow<String>()
    val addressBookUpdate = _addressBookUpdate.asSharedFlow()
    private val _addressBookDelete = MutableSharedFlow<String>()
    val addressBookDelete = _addressBookDelete.asSharedFlow()

    suspend fun requestQRScan(context: Context): Boolean {
        return try {
            val nodes = getConnectedNodes(context)
            if (nodes.isEmpty()) {
                Timber.tag("WearComm").w("沒有連接的節點")
                return false
            }

            val messageClient = Wearable.getMessageClient(context)
            
            // 向所有連接的節點發送掃描請求
            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    WearCommunicationService.QR_SCAN_REQUEST_PATH,
                    ByteArray(0)
                ).await()
                Timber.tag("WearComm").d("發送 QR 掃描請求到節點: ${node.displayName}")
            }
              true
        } catch (e: Exception) {
            Timber.tag("WearComm").e(e, "發送 QR 掃描請求失敗")
            false
        }
    }

    suspend fun requestSignedTxScan(context: Context): Boolean {
        return try {
            val nodes = getConnectedNodes(context)
            if (nodes.isEmpty()) {
                Timber.tag("WearComm").w("沒有連接的節點")
                return false
            }

            val messageClient = Wearable.getMessageClient(context)

            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    WearCommunicationService.SIGN_TX_SCAN_REQUEST_PATH,
                    ByteArray(0)
                ).await()
                Timber.tag("WearComm").d("發送簽名掃描請求到節點: ${node.displayName}")
            }
            true
        } catch (e: Exception) {
            Timber.tag("WearComm").e(e, "發送簽名掃描請求失敗")
            false
        }
    }

    suspend fun requestKeystoneConnectScan(context: Context): Boolean {
        return try {
            val nodes = getConnectedNodes(context)
            if (nodes.isEmpty()) {
                Timber.tag("WearComm").w("沒有連接的節點")
                return false
            }

            val messageClient = Wearable.getMessageClient(context)

            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    WearCommunicationService.KEYSTONE_CONNECT_SCAN_REQUEST_PATH,
                    ByteArray(0)
                ).await()
                Timber.tag("WearComm").d("發送 Keystone 連接掃描請求到節點: ${node.displayName}")
            }
            true
        } catch (e: Exception) {
            Timber.tag("WearComm").e(e, "發送 Keystone 連接掃描請求失敗")
            false
        }
    }

    private suspend fun getConnectedNodes(context: Context): List<Node> {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val connectedNodes = nodeClient.connectedNodes.await()
            connectedNodes.toList()
        } catch (e: Exception) {
            Timber.tag("WearComm").e(e, "獲取連接節點失敗")
            emptyList()
        }
    }

    suspend fun onQRScanResult(address: String) {
        _qrScanResults.emit(address)
        Timber.tag("WearComm").d("收到 QR 掃描結果: $address")
    }

    suspend fun onSignedTxResult(tx: String) {
        _signedTxResults.emit(tx)
        Timber.tag("WearComm").d("收到簽名交易結果")
    }

    suspend fun onKeystoneConnectResult(urData: String) {
        _keystoneConnectResults.emit(urData)
        Timber.tag("WearComm").d("收到 Keystone 連接結果: ${urData.take(50)}...")
    }

    // 地址簿同步相關方法
    suspend fun onAddressBookSync(contactsJson: String) {
        _addressBookSync.emit(contactsJson)
        Timber.tag("WearComm").d("收到地址簿同步數據")
    }

    suspend fun onAddressBookAdd(contactJson: String) {
        _addressBookAdd.emit(contactJson)
        Timber.tag("WearComm").d("收到地址簿新增聯絡人")
    }

    suspend fun onAddressBookUpdate(contactJson: String) {
        _addressBookUpdate.emit(contactJson)
        Timber.tag("WearComm").d("收到地址簿更新聯絡人")
    }

    suspend fun onAddressBookDelete(contactId: String) {
        _addressBookDelete.emit(contactId)
        Timber.tag("WearComm").d("收到地址簿刪除聯絡人: $contactId")
    }

    suspend fun syncAddressBookToMobile(context: Context, contactsJson: String): Boolean {
        return try {
            val nodes = getConnectedNodes(context)
            if (nodes.isEmpty()) {
                Timber.tag("WearComm").w("沒有連接的節點")
                return false
            }

            val dataClient = Wearable.getDataClient(context)
            val request = PutDataMapRequest.create(WearCommunicationService.ADDRESS_BOOK_SYNC_PATH).apply {
                dataMap.putString("contacts", contactsJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            Timber.tag("WearComm").d("地址簿同步到手機成功")
            true
        } catch (e: Exception) {
            Timber.tag("WearComm").e(e, "地址簿同步到手機失敗")
            false
        }
    }
}
