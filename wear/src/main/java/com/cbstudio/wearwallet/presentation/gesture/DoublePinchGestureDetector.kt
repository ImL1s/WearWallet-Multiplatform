package com.cbstudio.wearwallet.presentation.gesture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 雙重捏合手勢檢測器
 * 
 * 創新功能：
 * 1. 使用加速度計和陀螺儀檢測手腕捏合動作
 * 2. 支援上下文感知的手勢處理
 * 3. 提供觸覺反饋確認
 * 4. 防誤觸機制
 */
@Singleton
class DoublePinchGestureDetector constructor(
    private val context: Context
) : SensorEventListener {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    private val handler = Handler(Looper.getMainLooper())
    
    // 手勢檢測參數
    private val PINCH_THRESHOLD_ACCEL = 15.0f  // 加速度閾值
    private val PINCH_THRESHOLD_GYRO = 3.0f   // 陀螺儀閾值
    private val DOUBLE_PINCH_TIMEOUT = 500L   // 雙擊超時（毫秒）
    private val GESTURE_COOLDOWN = 1000L      // 手勢冷卻時間
    
    // 狀態追蹤
    private var lastPinchTime = 0L
    private var pinchCount = 0
    private var lastGestureTime = 0L
    private var isMonitoring = false
    
    // 加速度和陀螺儀數據緩衝
    private val accelBuffer = FloatArray(3)
    private val gyroBuffer = FloatArray(3)
    private var lastAccelMagnitude = 0f
    private var lastGyroMagnitude = 0f
    
    // 手勢事件通道
    private val gestureChannel = Channel<GestureEvent>(Channel.BUFFERED)
    val gestureEvents: Flow<GestureEvent> = gestureChannel.receiveAsFlow()
    
    // 上下文感知
    private var currentContext: GestureContext = GestureContext.GENERAL
    
    /**
     * 開始監聽手勢
     */
    fun startMonitoring(context: GestureContext = GestureContext.GENERAL) {
        if (isMonitoring) return
        
        currentContext = context
        isMonitoring = true
        
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI)
        
        Timber.d("開始監聽雙重捏合手勢 - 上下文: $context")
    }
    
    /**
     * 停止監聽手勢
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        sensorManager.unregisterListener(this)
        
        Timber.d("停止監聽雙重捏合手勢")
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelBuffer, 0, 3)
                processAccelerometerData()
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, gyroBuffer, 0, 3)
                processGyroscopeData()
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要處理精度變化
    }
    
    /**
     * 處理加速度計數據
     */
    private fun processAccelerometerData() {
        // 計算加速度向量的幅度
        val magnitude = sqrt(
            accelBuffer[0] * accelBuffer[0] +
            accelBuffer[1] * accelBuffer[1] +
            accelBuffer[2] * accelBuffer[2]
        )
        
        // 檢測急劇的加速度變化（可能是捏合動作）
        val deltaMagnitude = abs(magnitude - lastAccelMagnitude)
        
        if (deltaMagnitude > PINCH_THRESHOLD_ACCEL) {
            checkForPinchGesture()
        }
        
        lastAccelMagnitude = magnitude
    }
    
    /**
     * 處理陀螺儀數據
     */
    private fun processGyroscopeData() {
        // 計算旋轉速度的幅度
        val magnitude = sqrt(
            gyroBuffer[0] * gyroBuffer[0] +
            gyroBuffer[1] * gyroBuffer[1] +
            gyroBuffer[2] * gyroBuffer[2]
        )
        
        // 檢測快速旋轉（配合加速度變化可能是捏合）
        if (magnitude > PINCH_THRESHOLD_GYRO) {
            // 增強捏合檢測的可信度
            lastGyroMagnitude = magnitude
        }
    }
    
    /**
     * 檢查是否為有效的捏合手勢
     */
    private fun checkForPinchGesture() {
        val currentTime = System.currentTimeMillis()
        
        // 防止過於頻繁的手勢觸發
        if (currentTime - lastGestureTime < GESTURE_COOLDOWN) {
            return
        }
        
        // 檢查是否為雙重捏合
        if (currentTime - lastPinchTime < DOUBLE_PINCH_TIMEOUT) {
            pinchCount++
            
            if (pinchCount >= 2) {
                // 檢測到雙重捏合！
                onDoublePinchDetected()
                pinchCount = 0
                lastGestureTime = currentTime
            }
        } else {
            // 重置計數器
            pinchCount = 1
        }
        
        lastPinchTime = currentTime
    }
    
    /**
     * 處理檢測到的雙重捏合手勢
     */
    private fun onDoublePinchDetected() {
        Timber.d("檢測到雙重捏合手勢！上下文: $currentContext")
        
        // 觸覺反饋
        provideHapticFeedback()
        
        // 發送手勢事件
        val event = GestureEvent(
            type = GestureType.DOUBLE_PINCH,
            context = currentContext,
            timestamp = System.currentTimeMillis()
        )
        
        gestureChannel.trySend(event)
    }
    
    /**
     * 提供觸覺反饋
     */
    private fun provideHapticFeedback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
    
    /**
     * 設置手勢上下文
     */
    fun setContext(context: GestureContext) {
        currentContext = context
    }
}

/**
 * 手勢事件
 */
data class GestureEvent(
    val type: GestureType,
    val context: GestureContext,
    val timestamp: Long
)

/**
 * 手勢類型
 */
enum class GestureType {
    DOUBLE_PINCH,
    TRIPLE_PINCH,  // 未來擴展
    WRIST_FLICK,   // 未來擴展
    CUSTOM
}

/**
 * 手勢上下文
 */
enum class GestureContext {
    GENERAL,                    // 一般情況
    TRANSACTION_CONFIRM,        // 交易確認
    QUICK_RECEIVE,             // 快速收款
    PORTFOLIO_CHECK,           // 查看投資組合
    EMERGENCY_CANCEL,          // 緊急取消
    AI_ASSISTANT,              // AI 助手
    NAVIGATION                 // 導航
}

/**
 * Composable 函數：使用雙重捏合手勢
 */
@Composable
fun rememberDoublePinchGestureDetector(
    gestureContext: GestureContext = GestureContext.GENERAL,
    onGesture: (GestureEvent) -> Unit
): DoublePinchGestureDetector {
    val androidContext = LocalContext.current
    val detector = remember { DoublePinchGestureDetector(androidContext) }
    
    DisposableEffect(detector, gestureContext) {
        detector.startMonitoring(gestureContext)
        
        onDispose {
            detector.stopMonitoring()
        }
    }
    
    LaunchedEffect(detector) {
        detector.gestureEvents.collect { event ->
            onGesture(event)
        }
    }
    
    return detector
}

/**
 * 手勢處理器接口
 */
interface GestureHandler {
    fun handleGesture(event: GestureEvent)
}

/**
 * 默認手勢處理器實現
 */
class DefaultGestureHandler : GestureHandler {
    override fun handleGesture(event: GestureEvent) {
        when (event.context) {
            GestureContext.TRANSACTION_CONFIRM -> {
                // 確認交易
                Timber.d("手勢確認交易")
            }
            GestureContext.QUICK_RECEIVE -> {
                // 顯示收款碼
                Timber.d("手勢顯示收款碼")
            }
            GestureContext.PORTFOLIO_CHECK -> {
                // 顯示餘額
                Timber.d("手勢查看餘額")
            }
            GestureContext.EMERGENCY_CANCEL -> {
                // 緊急取消
                Timber.d("手勢緊急取消")
            }
            else -> {
                Timber.d("未處理的手勢上下文: ${event.context}")
            }
        }
    }
}
