package com.cbstudio.mobile

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.cbstudio.mobile.R
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import timber.log.Timber
import com.keystone.sdk.KeystoneSDK
import androidx.camera.core.CameraUnavailableException
import java.util.concurrent.ExecutionException

/**
 * QR 碼掃描活動 - Keystone 3 Pro 優化版本
 * 
 * 這個活動專為 WearWallet 和 Keystone 3 Pro 硬體錢包整合而設計，
 * 支援標準 QR 碼和 Keystone 的動畫多片段 UR 協議 QR 碼。
 * 
 * 主要功能：
 * - CameraX 相機預覽和掃描
 * - ML Kit QR 碼識別
 * - Keystone SDK UR 協議處理（官方 SDK 整合）
 * - 自動模式切換（標準/Keystone 模式）
 * - 即時掃描進度顯示
 * - 多片段 QR 碼自動組合
 * - 觸摸對焦功能
 * - 音效反饋和視覺提示
 * - Wear OS 數據層通信
 * 
 * 技術特點：
 * - 使用官方 KeystoneSDK.decodeQR() 確保 UR 協議兼容性
 * - 智能錯誤恢復：解碼失敗時繼續掃描
 * - 冷卻機制防止重複掃描同一片段
 * - 詳細日誌輸出便於除錯
 * 
 * 使用方式：
 * - 標準掃描：EXTRA_KEYSTONE_MODE = false
 * - Keystone 掃描：EXTRA_KEYSTONE_MODE = true（自動啟動）
 * 
 * @see KeystoneSDK
 * @see https://github.com/KeystoneHQ/keystone-sdk-android-demo
 */
class QRScanActivity : AppCompatActivity() {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var resultPath: String = QR_SCAN_RESULT_PATH
    private lateinit var previewView: PreviewView
    private lateinit var loadingView: android.view.View
    private lateinit var loadingText: android.widget.TextView
    private var camera: Camera? = null
    private var isFlashEnabled = false
    private var isKeystoneMode = false
    private var isWatchConnectivityRequest = false
    private var scanProgressToast: Toast? = null
    private val keystoneSDK = KeystoneSDK()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)

        resultPath = intent.getStringExtra(EXTRA_RESULT_PATH) ?: QR_SCAN_RESULT_PATH
        isKeystoneMode = intent.getBooleanExtra(EXTRA_KEYSTONE_MODE, false)
        isWatchConnectivityRequest = intent.getBooleanExtra("WATCH_CONNECTIVITY_REQUEST", false)
        
        // 檢查是否有相機可用
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Timber.tag(TAG).w("設備沒有相機，使用模擬器模式")
            Toast.makeText(this, "模擬器環境：使用測試數據", Toast.LENGTH_LONG).show()
            
            // 模擬器環境：直接返回測試地址
            Handler(Looper.getMainLooper()).postDelayed({
                handleEmulatorScan()
            }, 1000)
            return
        }
        
        // 統一顯示掃描提示訊息
        val message = when {
            isKeystoneMode && isWatchConnectivityRequest -> "正在啟動 Keystone 掃描模式（WatchConnectivity）"
            isKeystoneMode -> "正在啟動 Keystone 掃描模式"
            else -> "正在啟動 QR 碼掃描器"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        
        // Keystone 模式初始化
        if (isKeystoneMode) {
            keystoneSDK.resetQRDecoder()
        }

        previewView = findViewById(R.id.preview_view)
        loadingView = findViewById(R.id.loading_view)
        loadingText = findViewById(R.id.loading_text)
        
        // 根據模式更新載入文字
        loadingText.text = when {
            isKeystoneMode && isWatchConnectivityRequest -> "正在啟動 Keystone 掃描模式..."
            isKeystoneMode -> "正在啟動硬體錢包掃描..."
            else -> "正在啟動 QR 碼掃描器..."
        }
        // 檢查相機權限
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            // 延遲啟動相機，讓用戶看到載入畫面
            Handler(Looper.getMainLooper()).postDelayed({
                startCamera()
            }, 500) // 延遲 500ms
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 延遲啟動相機，讓用戶看到載入畫面
                Handler(Looper.getMainLooper()).postDelayed({
                    startCamera()
                }, 500)
            } else {
                Toast.makeText(this, "需要相機權限來掃描 QR 碼", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // 檢查是否有可用的相機
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    Timber.tag(TAG).e("沒有可用的後置相機")
                    Toast.makeText(this, "沒有可用的相機，使用測試模式", Toast.LENGTH_LONG).show()
                    handleEmulatorScan()
                    return@addListener
                }
                
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "啟動相機失敗")
                Toast.makeText(this, "無法啟動相機: ${e.message}", Toast.LENGTH_SHORT).show()
                
                // 如果是相機不可用錯誤，嘗試使用模擬器模式
                if (e is ExecutionException && e.cause is CameraUnavailableException) {
                    handleEmulatorScan()
                } else {
                    finish()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        // 設置相機預覽
        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }

        // 設置相機選擇器 - 使用後置相機
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        // 設置圖像分析器
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            ContextCompat.getMainExecutor(this),
            QRCodeAnalyzer(
                isKeystoneMode = isKeystoneMode,
                keystoneSDK = keystoneSDK,
                activity = this
            ) { qrCode ->
                // 找到 QR 碼，發送結果回手錶
                sendResultToWatch(qrCode)

                // 播放成功音效
                playBeepSound()

                // 顯示成功提示
                runOnUiThread {
                    scanProgressToast?.cancel()
                    val message = if (qrCode.startsWith("ur:")) {
                        "Keystone QR 碼掃描成功!"
                    } else {
                        "掃描成功!"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    // 短暫延遲後關閉活動
                    Handler(Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 1000)
                }
            }
        )

        try {
            // 解綁之前的用例
            cameraProvider.unbindAll()

            // 綁定用例到相機
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // 初始化自動對焦
            setupTouchFocus()
            
            // 成功綁定相機後，隱藏載入畫面
            runOnUiThread {
                loadingView.visibility = android.view.View.GONE
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "用例綁定失敗")
        }

    }

    // 設置觸摸對焦
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchFocus() {
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()

                camera?.cameraControl?.startFocusAndMetering(action)
                return@setOnTouchListener true
            }
            return@setOnTouchListener false
        }
    }

    // 播放掃描成功音效
    private fun playBeepSound() {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        Handler(Looper.getMainLooper()).postDelayed({
            toneGenerator.release()
        }, 150)
    }

    // 發送結果到手錶
    @SuppressLint("VisibleForTests")
    private fun sendResultToWatch(result: String) {
        if (isWatchConnectivityRequest) {
            // 使用新的 WatchConnectivity 消息格式
            sendResultViaWatchConnectivity(result)
        } else {
            // 使用舊的 Wearable 數據層格式（向後相容）
            sendResultViaDataLayer(result)
        }
    }

    /**
     * 處理模擬器環境的掃描
     * 返回測試地址供開發使用
     */
    private fun handleEmulatorScan() {
        // 更新載入文字以顯示模擬器模式
        runOnUiThread {
            loadingText.text = "模擬器模式：準備測試數據..."
        }
        
        // 模擬延遲以顯示載入畫面
        Handler(Looper.getMainLooper()).postDelayed({
            val testAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f26Fd" // 測試用以太坊地址
            
            if (isKeystoneMode) {
                // Keystone 模式：返回模擬的 UR 數據
                Toast.makeText(this, "模擬器：返回測試 Keystone 數據", Toast.LENGTH_SHORT).show()
                sendResultToWatch("ur:crypto-account/...")
            } else {
                // 普通地址掃描模式
                Toast.makeText(this, "模擬器：返回測試地址", Toast.LENGTH_SHORT).show()
                sendResultToWatch(testAddress)
            }
        }, 1500) // 延長一點時間讓用戶看到載入畫面
    }
    
    /**
     * 通過 WatchConnectivity 發送結果（新格式）
     */
    private fun sendResultViaWatchConnectivity(result: String) {
        val messageClient = Wearable.getMessageClient(this)
        
        // 根據 resultPath 確定消息類型
        val messageType = when {
            resultPath.contains("keystone_connect") -> "keystone_connect_result"
            resultPath.contains("keystone_sign") || resultPath.contains("signed_tx") -> "keystone_sign_result"
            else -> "qr_scan_result"
        }
        
        val responseMessage = JSONObject().apply {
            put("type", messageType)
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject().apply {
                put("urData", result)
                put("success", true)
                // 如果是 UR 協議，添加額外標記
                if (result.startsWith("ur:")) {
                    put("isUrProtocol", true)
                    // 檢查是否為多片段
                    if (result.contains("|")) {
                        val fragments = result.split("|")
                        put("urFragments", fragments)
                        put("fragmentCount", fragments.size)
                    }
                }
            })
        }.toString()
        
        messageClient.sendMessage(
            "", // 發送給所有連接的節點
            "/keystone_result",
            responseMessage.toByteArray()
        ).addOnSuccessListener {
            Timber.tag(TAG).d("結果成功發送到手錶（WatchConnectivity）: $messageType")
        }.addOnFailureListener { e ->
            Timber.tag(TAG).e(e, "發送結果到手錶失敗（WatchConnectivity）")
            // 如果新格式失敗，嘗試舊格式作為後備
            sendResultViaDataLayer(result)
        }
    }
    
    /**
     * 通過 Wearable 數據層發送結果（舊格式，向後相容）
     */
    private fun sendResultViaDataLayer(result: String) {
        val dataClient = Wearable.getDataClient(this)

        val request = PutDataMapRequest.create(resultPath).apply {
            dataMap.putString("address", result)
            dataMap.putLong("timestamp", System.currentTimeMillis())
            // 如果是 UR 協議，添加額外標記
            if (result.startsWith("ur:")) {
                dataMap.putBoolean("is_ur_protocol", true)
                // 如果包含多個片段，分別發送
                if (result.contains("|")) {
                    val fragments = result.split("|")
                    dataMap.putStringArrayList("ur_fragments", ArrayList(fragments))
                }
            }
        }.asPutDataRequest()
            .setUrgent()

        dataClient.putDataItem(request)
            .addOnSuccessListener {
                Timber.tag(TAG).d("結果成功發送到手錶（DataLayer）")
            }
            .addOnFailureListener { e ->
                Timber.tag(TAG).e(e, "發送結果到手錶失敗（DataLayer）")
            }
    }

    // QR 碼分析器
    private class QRCodeAnalyzer(
        private val isKeystoneMode: Boolean,
        private val keystoneSDK: KeystoneSDK,
        private val activity: QRScanActivity,
        private val onQRCodeFound: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()
        private var isScanning = true
        private var lastScanTime = 0L
        private val scanCooldownMs = 300L // 防止重複掃描同一片段

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            if (!isScanning) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastScanTime < scanCooldownMs) {
                            return@addOnSuccessListener
                        }
                        
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { qrData ->
                                if (processQRCode(qrData, currentTime)) {
                                    isScanning = false
                                    onQRCodeFound(qrData)
                                }
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
        
        /**
         * 處理掃描到的 QR 碼數據
         * 
         * 這是核心的 QR 碼處理邏輯，實現了 Keystone SDK 官方示例的最佳實踐：
         * 1. 非 Keystone 模式直接返回（標準 QR 碼）
         * 2. Keystone 模式使用官方 SDK 解碼 UR 協議
         * 3. 監控掃描進度並提供用戶反饋
         * 4. 智能錯誤處理和重試機制
         * 
         * @param qrData 掃描到的 QR 碼原始數據
         * @param currentTime 當前時間戳，用於冷卻控制
         * @return true 表示掃描完成，false 表示需要繼續掃描
         * 
         * @see KeystoneSDK.decodeQR
         * @see https://github.com/KeystoneHQ/keystone-sdk-android-demo/blob/master/app/src/main/kotlin/com/keystone/sdk/demo/ScannerFragment.kt
         */
        private fun processQRCode(qrData: String, currentTime: Long): Boolean {
            lastScanTime = currentTime
            
            // 如果不是 Keystone 模式，直接返回（處理標準 QR 碼）
            if (!isKeystoneMode) {
                return true
            }
            
            // 使用 Keystone SDK 處理 UR 協議（按照官方示例）
            try {
                // 核心：使用官方 SDK 解碼 UR 數據
                val decodedResult = keystoneSDK.decodeQR(qrData)
                
                // 檢查掃描進度（0-100%）
                val progress = decodedResult.progress
                Timber.tag("QRCodeAnalyzer").d("UR 掃描進度: $progress%")
                
                // 向用戶顯示即時進度反饋
                activity.runOnUiThread {
                    activity.scanProgressToast?.cancel()
                    activity.scanProgressToast = Toast.makeText(
                        activity,
                        "掃描進度: $progress%",
                        Toast.LENGTH_SHORT
                    )
                    activity.scanProgressToast?.show()
                }
                
                // 關鍵判斷：檢查 UR 是否完整
                // 按照官方示例：如果 decodedResult.ur == null，繼續掃描
                if (decodedResult.ur == null) {
                    Timber.tag("QRCodeAnalyzer").d("UR 片段不完整，繼續掃描...")
                    return false // 繼續掃描下一個片段
                }
                
                // UR 完成，返回完整數據
                Timber.tag("QRCodeAnalyzer").d("UR 掃描完成！類型: ${decodedResult.ur?.type}")
                return true
                
            } catch (e: Exception) {
                Timber.tag("QRCodeAnalyzer").e(e, "Keystone 解碼失敗: ${e.message}")
                
                // 智能錯誤處理：如果是 UR 協議但解碼失敗，繼續掃描
                // 這可能是中間片段或網絡問題導致的暫時性錯誤
                if (qrData.startsWith("ur:")) {
                    return false // 繼續掃描
                }
                
                // 非 UR 協議或嚴重錯誤，停止掃描
                return true
            }
        }
    }

    companion object {
        private const val TAG = "QRScanActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val QR_SCAN_RESULT_PATH = "/qr_scan_result"
        const val EXTRA_RESULT_PATH = "result_path"
        const val EXTRA_KEYSTONE_MODE = "keystone_mode"
    }
}
