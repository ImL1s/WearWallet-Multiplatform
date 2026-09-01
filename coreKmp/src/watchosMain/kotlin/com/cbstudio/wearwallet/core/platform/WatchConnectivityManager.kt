@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
package com.cbstudio.wearwallet.core.platform

import platform.WatchConnectivity.*
import platform.Foundation.*
import platform.Foundation.NSTimer
import platform.darwin.NSObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.cinterop.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * WatchConnectivity 管理器 - watchOS 端實現
 * 
 * 負責管理 watchOS 與 iPhone 之間的通訊
 * 用於 Keystone 硬體錢包的橋接通訊
 * 
 * 功能：
 * 1. 發送未簽名交易到 iPhone
 * 2. 接收簽名結果
 * 3. 同步錢包狀態
 * 4. 處理錯誤和超時
 * 
 * 安全考量：
 * - 所有數據傳輸都應加密
 * - 使用 nonce 防止重放攻擊
 * - 設定合理的超時時間
 */
@Serializable
data class WatchMessage(
    val id: String,
    val type: MessageType,
    val payload: String,
    val timestamp: Double,
    val nonce: String
)

@Serializable
enum class MessageType {
    // Keystone 相關
    KEYSTONE_SIGN_REQUEST,
    KEYSTONE_SIGN_RESPONSE,
    KEYSTONE_GET_ACCOUNTS,
    KEYSTONE_ACCOUNTS_RESPONSE,
    
    // 錢包同步
    WALLET_SYNC_REQUEST,
    WALLET_SYNC_RESPONSE,
    
    // 狀態更新
    CONNECTION_STATUS,
    ERROR_MESSAGE,
    
    // 通用
    PING,
    PONG
}

@Serializable
data class MessageResponse(
    val messageId: String,
    val success: Boolean,
    val result: String?,
    val error: String?
)

// 全局常數
private const val TIMEOUT_SECONDS = 60.0

// 單例實例
private var _sharedInstance: WatchConnectivityManager? = null

@OptIn(ExperimentalForeignApi::class)
class WatchConnectivityManager : NSObject(), WCSessionDelegateProtocol {
    
    companion object {
        val shared: WatchConnectivityManager
            get() {
                if (_sharedInstance == null) {
                    _sharedInstance = WatchConnectivityManager()
                }
                return _sharedInstance!!
            }
    }
    
    private val session: WCSession = WCSession.defaultSession()
    private val _isReachable = MutableStateFlow(false)
    val isReachable: StateFlow<Boolean> = _isReachable.asStateFlow()
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val pendingCallbacks = mutableMapOf<String, (Result<MessageResponse>) -> Unit>()
    private val messageChannel = Channel<WatchMessage>(Channel.BUFFERED)
    
    init {
        if (WCSession.isSupported()) {
            session.delegate = this
            session.activateSession()
        }
    }
    
    /**
     * 發送 Keystone 簽名請求到 iPhone
     */
    suspend fun sendKeystoneSignRequest(
        transactionData: ByteArray,
        chainId: Long,
        derivationPath: String
    ): Result<String> = suspendCoroutine { continuation ->
        val messageId = NSUUID().UUIDString()
        
        val payload = Json.encodeToString(
            KeystoneSignPayload(
                transactionData = transactionData.toNSData().base64Encoding() ?: "",
                chainId = chainId,
                derivationPath = derivationPath
            )
        )
        
        val message = WatchMessage(
            id = messageId,
            type = MessageType.KEYSTONE_SIGN_REQUEST,
            payload = payload,
            timestamp = NSDate().timeIntervalSince1970,
            nonce = NSUUID().UUIDString()
        )
        
        sendMessage(message) { result ->
            result.fold(
                onSuccess = { response ->
                    if (response.success) {
                        response.result?.let {
                            continuation.resume(Result.success(it))
                        } ?: continuation.resume(
                            Result.failure(Exception("No signature in response"))
                        )
                    } else {
                        continuation.resume(
                            Result.failure(Exception(response.error ?: "Unknown error"))
                        )
                    }
                },
                onFailure = { error ->
                    continuation.resume(Result.failure(error))
                }
            )
        }
    }
    
    /**
     * 發送消息到 iPhone
     */
    private fun sendMessage(
        message: WatchMessage,
        callback: ((Result<MessageResponse>) -> Unit)? = null
    ) {
        if (!session.isReachable()) {
            callback?.invoke(Result.failure(Exception("iPhone is not reachable")))
            return
        }
        
        callback?.let {
            pendingCallbacks[message.id] = it
            
            // 設定超時 - 使用延遲方式
            // TODO: 實現超時機制
        }
        
        val messageDict = message.toNSDictionary()
        
        session.sendMessage(
            messageDict,
            replyHandler = { reply ->
                @Suppress("UNCHECKED_CAST")
                handleReply(message.id, reply as? Map<Any?, Any?>)
            },
            errorHandler = { error ->
                pendingCallbacks.remove(message.id)?.let {
                    it(Result.failure(Exception(error?.localizedDescription ?: "Unknown error")))
                }
            }
        )
    }
    
    /**
     * 處理 iPhone 的回覆
     */
    private fun handleReply(messageId: String, reply: Map<Any?, Any?>?) {
        pendingCallbacks.remove(messageId)?.let { callback ->
            reply?.let { replyDict ->
                try {
                    val response = MessageResponse(
                        messageId = replyDict["messageId"] as? String ?: "",
                        success = replyDict["success"] as? Boolean ?: false,
                        result = replyDict["result"] as? String,
                        error = replyDict["error"] as? String
                    )
                    callback(Result.success(response))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            } ?: callback(Result.failure(Exception("Empty reply")))
        }
    }
    
    /**
     * 發送 Ping 測試連接
     */
    suspend fun ping(): Boolean = suspendCoroutine { continuation ->
        val message = WatchMessage(
            id = NSUUID().UUIDString(),
            type = MessageType.PING,
            payload = "{}",
            timestamp = NSDate().timeIntervalSince1970,
            nonce = NSUUID().UUIDString()
        )
        
        sendMessage(message) { result ->
            continuation.resume(result.isSuccess)
        }
    }
    
    /**
     * 同步錢包數據
     */
    suspend fun syncWalletData(): Result<WalletSyncData> = suspendCoroutine { continuation ->
        val message = WatchMessage(
            id = NSUUID().UUIDString(),
            type = MessageType.WALLET_SYNC_REQUEST,
            payload = "{}",
            timestamp = NSDate().timeIntervalSince1970,
            nonce = NSUUID().UUIDString()
        )
        
        sendMessage(message) { result ->
            result.fold(
                onSuccess = { response ->
                    response.result?.let { json ->
                        try {
                            val syncData = Json.decodeFromString<WalletSyncData>(json)
                            continuation.resume(Result.success(syncData))
                        } catch (e: Exception) {
                            continuation.resume(Result.failure(e))
                        }
                    } ?: continuation.resume(
                        Result.failure(Exception("No sync data in response"))
                    )
                },
                onFailure = { error ->
                    continuation.resume(Result.failure(error))
                }
            )
        }
    }
    
    // WCSessionDelegate 方法
    
    // watchOS 不需要這些方法，它們是 iOS 特有的
    
    override fun session(
        session: WCSession,
        activationDidCompleteWithState: WCSessionActivationState,
        error: NSError?
    ) {
        // WCSessionActivationState
        // 2 = Activated, 1 = Inactive, 0 = NotActivated
        // 使用 @OptIn(UnsafeNumber::class) 允許跨平台數字類型比較
        when (activationDidCompleteWithState) {
            WCSessionActivationStateActivated -> { // Activated
                _connectionState.value = ConnectionState.CONNECTED
                _isReachable.value = session.isReachable()
            }
            WCSessionActivationStateInactive -> { // Inactive
                _connectionState.value = ConnectionState.INACTIVE
            }
            WCSessionActivationStateNotActivated -> { // NotActivated
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            else -> {}
        }
    }
    
    override fun sessionReachabilityDidChange(session: WCSession) {
        _isReachable.value = session.isReachable()
        if (session.isReachable()) {
            _connectionState.value = ConnectionState.CONNECTED
        } else {
            _connectionState.value = ConnectionState.UNREACHABLE
        }
    }
    
    override fun session(session: WCSession, didReceiveMessage: Map<Any?, *>) {
        // 處理來自 iPhone 的主動消息
        @Suppress("UNCHECKED_CAST")
        handleIncomingMessage(didReceiveMessage as Map<Any?, Any?>)
    }
    
    override fun session(
        session: WCSession,
        didReceiveMessage: Map<Any?, *>,
        replyHandler: (Map<Any?, *>?) -> Unit
    ) {
        // 處理需要回覆的消息
        @Suppress("UNCHECKED_CAST")
        val response = handleIncomingMessage(didReceiveMessage as Map<Any?, Any?>)
        replyHandler(response.toNSDictionary())
    }
    
    private fun handleIncomingMessage(message: Map<Any?, Any?>): MessageResponse {
        // 解析並處理消息
        return try {
            val watchMessage = WatchMessage.fromNSDictionary(message)
            
            when (watchMessage.type) {
                MessageType.CONNECTION_STATUS -> {
                    // 更新連接狀態
                    MessageResponse(
                        messageId = watchMessage.id,
                        success = true,
                        result = null,
                        error = null
                    )
                }
                MessageType.PONG -> {
                    MessageResponse(
                        messageId = watchMessage.id,
                        success = true,
                        result = "PONG",
                        error = null
                    )
                }
                else -> {
                    MessageResponse(
                        messageId = watchMessage.id,
                        success = false,
                        result = null,
                        error = "Unsupported message type"
                    )
                }
            }
        } catch (e: Exception) {
            MessageResponse(
                messageId = "",
                success = false,
                result = null,
                error = e.message
            )
        }
    }
}

// 擴展方法  
@OptIn(ExperimentalForeignApi::class)
private fun WatchMessage.toNSDictionary(): Map<Any?, Any?> {
    return mapOf(
        "id" to id,
        "type" to type.name,
        "payload" to payload,
        "timestamp" to timestamp,
        "nonce" to nonce
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun WatchMessage.Companion.fromNSDictionary(dict: Map<Any?, Any?>): WatchMessage {
    return WatchMessage(
        id = dict["id"] as? String ?: "",
        type = MessageType.valueOf(dict["type"] as? String ?: "ERROR_MESSAGE"),
        payload = dict["payload"] as? String ?: "{}",
        timestamp = dict["timestamp"] as? Double ?: 0.0,
        nonce = dict["nonce"] as? String ?: ""
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun MessageResponse.toNSDictionary(): Map<Any?, Any?> {
    val dict = mutableMapOf<Any?, Any?>(
        "messageId" to messageId,
        "success" to success
    )
    result?.let { dict["result"] = it }
    error?.let { dict["error"] = it }
    return dict
}

private fun ByteArray.toNSData(): NSData = memScoped {
    // 使用 convert() 讓編譯器為每個平台選擇正確的類型（UInt 或 ULong）
    NSData.create(
        bytes = allocArrayOf(this@toNSData),
        length = this@toNSData.size.convert()
    )
}

// 連接狀態
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNREACHABLE,
    INACTIVE,
    ERROR
}

// Keystone 簽名請求 Payload
@Serializable
data class KeystoneSignPayload(
    val transactionData: String, // Base64 編碼
    val chainId: Long,
    val derivationPath: String
)

// 錢包同步數據
@Serializable
data class WalletSyncData(
    val wallets: List<WalletInfo>,
    val currentWalletId: String?,
    val lastSyncTime: Double
)

@Serializable
data class WalletInfo(
    val id: String,
    val name: String,
    val address: String,
    val type: String, // "hot" or "keystone"
    val balance: String?
)