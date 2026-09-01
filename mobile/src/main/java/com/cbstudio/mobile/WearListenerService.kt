package com.cbstudio.mobile

import android.content.Intent
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository
import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.utils.ContactJsonSerializer
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import org.koin.android.ext.android.inject

class WearListenerService : WearableListenerService() {
    
    private val contactRepository: ContactRepository by inject()
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // 原有路徑（向後相容）
        const val QR_SCAN_REQUEST_PATH = "/qr_scan_request"
        const val SIGN_TX_SCAN_REQUEST_PATH = "/sign_tx_scan_request"
        const val KEYSTONE_CONNECT_SCAN_REQUEST_PATH = "/keystone_connect_scan_request"
        
        // 新的 WatchConnectivity 消息類型
        const val KEYSTONE_CONNECT_REQUEST = "keystone_connect_request"
        const val KEYSTONE_CONNECT_RESULT = "keystone_connect_result"
        const val KEYSTONE_SIGN_REQUEST = "keystone_sign_request"
        const val KEYSTONE_SIGN_RESULT = "keystone_sign_result"
        
        // 地址簿同步相關路徑
        const val ADDRESS_BOOK_SYNC_PATH = "/address_book_sync"
        const val ADDRESS_BOOK_ADD_PATH = "/address_book_add"
        const val ADDRESS_BOOK_UPDATE_PATH = "/address_book_update"
        const val ADDRESS_BOOK_DELETE_PATH = "/address_book_delete"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag("WearListenerService").d("WearListenerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.tag("WearListenerService").d("WearListenerService destroyed")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.tag("WearListenerService").d("Received message: ${messageEvent.path}")
        
        // 首先檢查是否為新的 JSON 格式消息
        if (messageEvent.data != null) {
            try {
                val messageString = String(messageEvent.data)
                val messageJson = JSONObject(messageString)
                
                if (messageJson.has("type")) {
                    handleWatchConnectivityMessage(messageJson)
                    return
                }
            } catch (e: Exception) {
                Timber.tag("WearListenerService").d("不是 JSON 格式消息，使用舊格式處理")
            }
        }
        
        // 處理舊格式消息（向後相容）
        when (messageEvent.path) {
            QR_SCAN_REQUEST_PATH -> {
                val intent = Intent(this, QRScanActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            SIGN_TX_SCAN_REQUEST_PATH -> {
                val intent = Intent(this, QRScanActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(QRScanActivity.EXTRA_RESULT_PATH, "/signed_tx_result")
                    putExtra(QRScanActivity.EXTRA_KEYSTONE_MODE, true) // 簽名掃描使用 Keystone 模式
                }
                startActivity(intent)
            }
            KEYSTONE_CONNECT_SCAN_REQUEST_PATH -> {
                val intent = Intent(this, QRScanActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(QRScanActivity.EXTRA_RESULT_PATH, "/keystone_connect_result")
                    putExtra(QRScanActivity.EXTRA_KEYSTONE_MODE, true) // 連接掃描使用 Keystone 模式
                }
                startActivity(intent)
            }
        }
    }
    
    /**
     * 處理 WatchConnectivity 新格式消息
     */
    private fun handleWatchConnectivityMessage(messageJson: JSONObject) {
        val messageType = messageJson.getString("type")
        val timestamp = messageJson.optLong("timestamp", System.currentTimeMillis())
        
        Timber.tag("WearListenerService").d("處理 WatchConnectivity 消息: $messageType")
        
        when (messageType) {
            KEYSTONE_CONNECT_REQUEST -> {
                handleKeystoneConnectRequest(messageJson)
            }
            KEYSTONE_SIGN_REQUEST -> {
                handleKeystoneSignRequest(messageJson)
            }
            else -> {
                Timber.tag("WearListenerService").w("未知的消息類型: $messageType")
            }
        }
    }
    
    /**
     * 處理 Keystone 連接請求
     */
    private fun handleKeystoneConnectRequest(messageJson: JSONObject) {
        Timber.tag("WearListenerService").d("處理 Keystone 連接請求")
        
        val intent = Intent(this, QRScanActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(QRScanActivity.EXTRA_RESULT_PATH, "/keystone_connect_result")
            putExtra(QRScanActivity.EXTRA_KEYSTONE_MODE, true)
            // 添加額外資訊識別這是來自新 API 的請求
            putExtra("WATCH_CONNECTIVITY_REQUEST", true)
        }
        startActivity(intent)
        
        // 回應確認消息給手錶
        sendConfirmationToWatch(KEYSTONE_CONNECT_REQUEST)
    }
    
    /**
     * 處理 Keystone 簽名請求
     */
    private fun handleKeystoneSignRequest(messageJson: JSONObject) {
        Timber.tag("WearListenerService").d("處理 Keystone 簽名請求")
        
        val intent = Intent(this, QRScanActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(QRScanActivity.EXTRA_RESULT_PATH, "/keystone_sign_result")
            putExtra(QRScanActivity.EXTRA_KEYSTONE_MODE, true)
            // 添加額外資訊識別這是來自新 API 的請求
            putExtra("WATCH_CONNECTIVITY_REQUEST", true)
            
            // 如果有交易數據，傳遞給掃描活動
            if (messageJson.has("data")) {
                val data = messageJson.getJSONObject("data")
                putExtra("TRANSACTION_DATA", data.toString())
            }
        }
        startActivity(intent)
        
        // 回應確認消息給手錶
        sendConfirmationToWatch(KEYSTONE_SIGN_REQUEST)
    }
    
    /**
     * 發送確認消息給手錶
     */
    private fun sendConfirmationToWatch(requestType: String) {
        serviceScope.launch {
            try {
                val messageClient = Wearable.getMessageClient(this@WearListenerService)
                val response = JSONObject().apply {
                    put("status", "received")
                    put("requestType", requestType)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                messageClient.sendMessage(
                    "", // 所有連接的節點
                    "/confirmation_response",
                    response.toByteArray()
                ).addOnSuccessListener {
                    Timber.tag("WearListenerService").d("確認消息已發送: $requestType")
                }.addOnFailureListener { e ->
                    Timber.tag("WearListenerService").e("發送確認消息失敗: ${e.message}")
                }
            } catch (e: Exception) {
                Timber.tag("WearListenerService").e("發送確認消息異常: ${e.message}")
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        Timber.tag("WearListenerService").d("Data changed: ${dataEvents.toString()} events")
        
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                when (event.dataItem.uri.path) {
                    ADDRESS_BOOK_SYNC_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val contactsJson = dataMapItem.dataMap.getString("contacts")
                        if (contactsJson != null) {
                            handleAddressBookSync(contactsJson)
                        }
                    }
                    ADDRESS_BOOK_ADD_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val contactJson = dataMapItem.dataMap.getString("contact")
                        if (contactJson != null) {
                            handleAddressBookAdd(contactJson)
                        }
                    }
                    ADDRESS_BOOK_UPDATE_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val contactJson = dataMapItem.dataMap.getString("contact")
                        if (contactJson != null) {
                            handleAddressBookUpdate(contactJson)
                        }
                    }
                    ADDRESS_BOOK_DELETE_PATH -> {
                        val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                        val contactId = dataMapItem.dataMap.getString("contactId")
                        if (contactId != null) {
                            handleAddressBookDelete(contactId)
                        }
                    }
                }
            }
        }
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        super.onCapabilityChanged(p0)
        Timber.tag("WearListenerService").d("Capability changed: ${p0.name}")
    }

    // 地址簿同步處理方法
    private fun handleAddressBookSync(contactsJson: String) {
        Timber.tag("WearListenerService").d("處理地址簿同步: $contactsJson")
        serviceScope.launch {
            try {
                // 解析 JSON 並更新本地地址簿
                val contacts = ContactJsonSerializer.jsonToContacts(contactsJson)
                
                // 清空現有聯絡人並重新插入（完整同步）
                contactRepository.deleteAllContacts()
                contacts.forEach { contact ->
                    contactRepository.insertContact(contact)
                }
                
                Timber.tag("WearListenerService").d("地址簿同步完成，共同步 ${contacts.size} 個聯絡人")
            } catch (e: Exception) {
                Timber.tag("WearListenerService").e("地址簿同步失敗: ${e.message}")
            }
        }
    }

    private fun handleAddressBookAdd(contactJson: String) {
        Timber.tag("WearListenerService").d("處理地址簿新增聯絡人: $contactJson")
        serviceScope.launch {
            try {
                val contact = ContactJsonSerializer.jsonToContact(contactJson)
                contactRepository.insertContact(contact)
                Timber.tag("WearListenerService").d("新增聯絡人成功: ${contact.name}")
            } catch (e: Exception) {
                Timber.tag("WearListenerService").e("新增聯絡人失敗: ${e.message}")
            }
        }
    }

    private fun handleAddressBookUpdate(contactJson: String) {
        Timber.tag("WearListenerService").d("處理地址簿更新聯絡人: $contactJson")
        serviceScope.launch {
            try {
                val contact = ContactJsonSerializer.jsonToContact(contactJson)
                contactRepository.updateContact(contact)
                Timber.tag("WearListenerService").d("更新聯絡人成功: ${contact.name}")
            } catch (e: Exception) {
                Timber.tag("WearListenerService").e("更新聯絡人失敗: ${e.message}")
            }
        }
    }

    private fun handleAddressBookDelete(contactId: String) {
        Timber.tag("WearListenerService").d("處理地址簿刪除聯絡人: $contactId")
        serviceScope.launch {
            try {
                contactRepository.deleteContact(contactId)
                Timber.tag("WearListenerService").d("刪除聯絡人成功: $contactId")
            } catch (e: Exception) {
                Timber.tag("WearListenerService").e("刪除聯絡人失敗: ${e.message}")
            }
        }
    }
}
