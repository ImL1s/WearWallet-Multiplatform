package com.cbstudio.wearwallet.core.platform.ios

import platform.WatchConnectivity.*
import platform.Foundation.*
import platform.UIKit.*
import platform.CoreImage.*
import platform.darwin.NSObject
import platform.AVFoundation.*
import platform.CoreGraphics.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.cinterop.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import com.cbstudio.wearwallet.core.domain.service.*
import com.cbstudio.wearwallet.core.domain.protocol.*
import com.cbstudio.wearwallet.core.domain.model.keystone.*

/**
 * iOS 端 Keystone 橋接處理器
 * 
 * 負責處理來自 watchOS 的 Keystone 請求
 * 實際執行 QR Code 掃描和生成操作
 * 
 * 架構職責：
 * 1. 接收 watchOS 的簽名請求
 * 2. 生成動態 QR Code 顯示給 Keystone
 * 3. 掃描 Keystone 返回的簽名結果
 * 4. 將結果回傳給 watchOS
 * 
 * 安全考量：
 * - 驗證所有請求的完整性
 * - 加密敏感數據
 * - 實施請求超時機制
 */
// 全局實例
private var _keystoneBridgeHandler: KeystoneBridgeHandler? = null

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName(swiftName = "KeystoneBridgeHandler", name = "KeystoneBridgeHandler", exact = true)
class KeystoneBridgeHandler : NSObject() {
    
    companion object {
        val shared: KeystoneBridgeHandler
            get() {
                if (_keystoneBridgeHandler == null) {
                    _keystoneBridgeHandler = KeystoneBridgeHandler()
                }
                return _keystoneBridgeHandler!!
            }
    }
    
    private val urProtocol = URProtocol()
    private val keystoneService = KeystoneService()
    private val scope = MainScope()
    
    // 用於管理進行中的請求
    private val activeRequests = mutableMapOf<String, BridgeRequestContext>()
    
    /**
     * 處理來自 watchOS 的 Keystone 簽名請求
     */
    suspend fun handleSignRequest(
        request: KeystoneBridgeRequest
    ): KeystoneBridgeResponse = coroutineScope {
        try {
            // 驗證請求
            validateRequest(request)
            
            // 解析交易數據
            val transaction = Json.decodeFromString<KeystoneTransaction>(request.payload)
            
            // 儲存請求上下文
            val context = BridgeRequestContext(
                request = request,
                transaction = transaction,
                startTime = NSDate().timeIntervalSince1970
            )
            activeRequests[request.requestId] = context
            
            // 生成簽名請求
            val signRequest = keystoneService.generateEthSignRequest(
                unsignedTxHex = transaction.data ?: "",
                derivationPath = "m/44'/60'/0'/0/0",
                masterFingerprint = "00000000",
                chainId = transaction.chainId.toLongOrNull() ?: 1L,
                requestId = request.requestId,
                fromAddress = transaction.to
            )
            
            // 將簽名請求編碼為 UR 格式
            val urEncoder = urProtocol.encodeToUR(
                data = signRequest.toByteArray(),
                type = "crypto-psbt",
                fragmentLen = 200
            )
            
            // 生成動態 QR Codes
            val qrCodes = generateAnimatedQRCodes(urEncoder)
            
            // 顯示 QR Code 給用戶（需要 UI 協調）
            showQRCodesToUser(qrCodes, request.requestId)
            
            // 等待用戶掃描 Keystone 返回的簽名
            val signedData = waitForKeystoneSignature(request.requestId)
            
            // 解析簽名結果
            val signResult = keystoneService.parseSignature(signedData)
            
            // 清理請求上下文
            activeRequests.remove(request.requestId)
            
            // 返回成功響應
            KeystoneBridgeResponse(
                requestId = request.requestId,
                success = true,
                result = Json.encodeToString(signResult),
                error = null,
                timestamp = NSDate().timeIntervalSince1970
            )
            
        } catch (e: Exception) {
            // 清理請求上下文
            activeRequests.remove(request.requestId)
            
            // 返回錯誤響應
            KeystoneBridgeResponse(
                requestId = request.requestId,
                success = false,
                result = null,
                error = KeystoneBridgeError(
                    code = "SIGN_FAILED",
                    message = e.message ?: "Unknown error",
                    details = e.stackTraceToString()
                ),
                timestamp = NSDate().timeIntervalSince1970
            )
        }
    }
    
    /**
     * 驗證請求的有效性
     */
    private fun validateRequest(request: KeystoneBridgeRequest) {
        // 檢查 nonce 防止重放攻擊
        if (request.nonce.isEmpty()) {
            throw IllegalArgumentException("Invalid nonce")
        }
        
        // 檢查時間戳（不能太舊）
        val now = NSDate().timeIntervalSince1970
        val age = (now - request.timestamp) * 1000
        if (age > request.timeout) {
            throw IllegalArgumentException("Request expired")
        }
        
        // 檢查請求類型
        if (request.requestType != BridgeRequestType.SIGN_TRANSACTION) {
            throw IllegalArgumentException("Invalid request type for sign operation")
        }
    }
    
    /**
     * 生成動態 QR Codes
     */
    private fun generateAnimatedQRCodes(encoder: UREncoder): List<UIImage> {
        val qrCodes = mutableListOf<UIImage>()
        
        while (!encoder.isComplete()) {
            val part = encoder.nextPart()
            val qrImage = generateQRCode(part)
            qrImage?.let { qrCodes.add(it) }
        }
        
        return qrCodes
    }
    
    /**
     * 生成單個 QR Code 圖像
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun generateQRCode(data: String): UIImage? {
        val filter = CIFilter.filterWithName("CIQRCodeGenerator")
        filter?.setValue(data.toNSData(), forKey = "inputMessage")
        filter?.setValue("H", forKey = "inputCorrectionLevel")
        
        val outputImage = filter?.outputImage ?: return null
        
        // 放大圖像
        val scale = 10.0
        val transform = CGAffineTransformMakeScale(scale, scale)
        val scaledImage = outputImage.imageByApplyingTransform(transform)
        
        // 轉換為 UIImage
        val context = CIContext()
        val cgImage = context.createCGImage(scaledImage, scaledImage.extent)
        
        return cgImage?.let { UIImage.imageWithCGImage(it) }
    }
    
    /**
     * 顯示 QR Codes 給用戶
     * 這需要與 UI 層協調
     */
    private fun showQRCodesToUser(qrCodes: List<UIImage>, requestId: String) {
        // 發送通知給 UI 層顯示 QR Codes
        NSNotificationCenter.defaultCenter.postNotificationName(
            "ShowKeystoneQRCodes",
            null,
            mapOf(
                "qrCodes" to qrCodes,
                "requestId" to requestId
            )
        )
    }
    
    /**
     * 等待 Keystone 簽名結果
     * 這需要與相機掃描協調
     */
    private suspend fun waitForKeystoneSignature(requestId: String): String = suspendCancellableCoroutine { cont ->
        // 註冊通知監聽器
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            "KeystoneSignatureReceived",
            null,
            NSOperationQueue.mainQueue
        ) { notification ->
            val userInfo = notification?.userInfo as? Map<*, *>
            val receivedRequestId = userInfo?.get("requestId") as? String
            val signature = userInfo?.get("signature") as? String
            
            if (receivedRequestId == requestId && signature != null) {
                cont.resume(signature)
            }
        }
        
        // 設定超時
        scope.launch {
            delay(60000) // 60秒超時
            if (cont.isActive) {
                NSNotificationCenter.defaultCenter.removeObserver(observer)
                cont.cancel(Exception("Signature timeout"))
            }
        }
        
        // 清理時移除監聽器
        cont.invokeOnCancellation {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }
    
    /**
     * 處理相機掃描的 QR Code
     * 由 UI 層調用
     */
    fun processScannedQRCode(qrData: String, requestId: String) {
        // 發送通知，包含簽名數據
        NSNotificationCenter.defaultCenter.postNotificationName(
            "KeystoneSignatureReceived",
            null,
            mapOf(
                "requestId" to requestId,
                "signature" to qrData
            )
        )
    }
    
    /**
     * 取消請求
     */
    fun cancelRequest(requestId: String) {
        activeRequests.remove(requestId)
        
        // 發送取消通知
        NSNotificationCenter.defaultCenter.postNotificationName(
            "KeystoneRequestCancelled",
            null,
            mapOf("requestId" to requestId)
        )
    }
    
    /**
     * 獲取活躍請求
     */
    fun getActiveRequest(requestId: String): BridgeRequestContext? {
        return activeRequests[requestId]
    }
    
    /**
     * 清理超時請求
     */
    fun cleanupTimeoutRequests() {
        val now = NSDate().timeIntervalSince1970
        val timeoutRequests = activeRequests.filter { (_, context) ->
            (now - context.startTime) * 1000 > context.request.timeout
        }
        
        timeoutRequests.forEach { (id, _) ->
            cancelRequest(id)
        }
    }
}

/**
 * 橋接請求上下文
 */
data class BridgeRequestContext(
    val request: KeystoneBridgeRequest,
    val transaction: KeystoneTransaction,
    val startTime: Double
)

// 擴展方法
@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData = memScoped {
    val nsString = NSString.create(string = this@toNSData)
    return nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: NSData()
}

@OptIn(ExperimentalSerializationApi::class)
private fun KeystoneSignRequest.toByteArray(): ByteArray {
    val json = Json.encodeToString(KeystoneSignRequest.serializer(), this)
    return json.encodeToByteArray()
}

/**
 * QR Code 掃描器協調器
 * 負責管理相機和 QR Code 掃描
 */
@OptIn(ExperimentalForeignApi::class)
class QRCodeScannerCoordinator : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    
    private var captureSession: AVCaptureSession? = null
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var urDecoder: URDecoder = SimpleURDecoder()
    private var currentRequestId: String? = null
    
    /**
     * 開始掃描 QR Code
     */
    fun startScanning(requestId: String, view: UIView) {
        currentRequestId = requestId
        
        // 設置相機捕獲會話
        setupCaptureSession()
        
        // 添加預覽層到視圖
        previewLayer?.let { layer ->
            layer.frame = view.bounds
            view.layer.addSublayer(layer)
        }
        
        // 開始捕獲
        captureSession?.startRunning()
    }
    
    /**
     * 停止掃描
     */
    fun stopScanning() {
        captureSession?.stopRunning()
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
        captureSession = null
        currentRequestId = null
        urDecoder.reset()
    }
    
    private fun setupCaptureSession() {
        val session = AVCaptureSession()
        
        // 設置相機輸入
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        device?.let { camera ->
            try {
                val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null)
                input?.let { deviceInput ->
                    if (session.canAddInput(deviceInput)) {
                        session.addInput(deviceInput)
                    }
                }
            } catch (e: Exception) {
                return
            }
        }
        
        // 設置元數據輸出
        val output = AVCaptureMetadataOutput()
        if (session.canAddOutput(output)) {
            session.addOutput(output)
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            output.setMetadataObjectsDelegate(this, NSOperationQueue.mainQueue())
        }
        
        // 設置預覽層
        previewLayer = AVCaptureVideoPreviewLayer(session = session)
        previewLayer?.videoGravity = AVLayerVideoGravityResizeAspectFill
        
        captureSession = session
    }
    
    // AVCaptureMetadataOutputObjectsDelegate 實現
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        didOutputMetadataObjects.forEach { metadataObject ->
            val readableObject = metadataObject as? AVMetadataMachineReadableCodeObject
            if (readableObject != null && readableObject.type == AVMetadataObjectTypeQRCode) {
                readableObject.stringValue?.let { qrString ->
                    processQRCode(qrString)
                }
            }
        }
    }
    
    private fun processQRCode(qrString: String) {
        // 處理 UR 格式的 QR Code
        val result = urDecoder.addPart(qrString)
        
        if (urDecoder.isComplete()) {
            urDecoder.getResult()?.let { data ->
                currentRequestId?.let { requestId ->
                    // 通知橋接處理器
                    KeystoneBridgeHandler.shared.processScannedQRCode(
                        qrData = data.decodeToString(),
                        requestId = requestId
                    )
                }
            }
            
            // 停止掃描
            stopScanning()
        }
    }
}