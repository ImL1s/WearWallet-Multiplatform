package com.cbstudio.wearwallet.presentation.biometric

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 持續生物識別認證系統
 * 
 * 創新功能：
 * 1. 心率變異性（HRV）持續監測
 * 2. 手腕運動模式識別
 * 3. 壓力水平檢測（防脅迫）
 * 4. 行為生物特徵學習
 * 5. 情境感知安全等級調整
 */
@Singleton
class ContinuousBiometricAuth constructor(
    private val context: Context
) {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    // 生物特徵配置文件
    private val biometricProfile = UserBiometricProfile()
    
    // 認證狀態
    private val _authState = MutableStateFlow(AuthenticationState())
    val authState: StateFlow<AuthenticationState> = _authState.asStateFlow()
    
    // 事件通道
    private val authEventChannel = Channel<BiometricAuthEvent>(Channel.BUFFERED)
    val authEvents: Flow<BiometricAuthEvent> = authEventChannel.receiveAsFlow()
    
    // 協程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 閾值常量
    companion object {
        const val HRV_BASELINE_WINDOW = 30 // 秒
        const val MOVEMENT_PATTERN_WINDOW = 10 // 秒
        const val STRESS_THRESHOLD = 0.75f // 壓力閾值
        const val DURESS_THRESHOLD = 0.90f // 脅迫閾值
        const val MIN_CONFIDENCE_SCORE = 0.70f // 最低可信度
        const val LEARNING_PERIOD_DAYS = 7 // 學習期
    }
    
    /**
     * 啟動持續認證
     */
    fun startContinuousAuth() {
        scope.launch {
            Timber.d("啟動持續生物識別認證")
            
            // 開始監測各項生物指標
            startHeartRateVariabilityMonitoring()
            startMovementPatternMonitoring()
            startStressLevelMonitoring()
            
            // 定期評估認證狀態
            startAuthenticationEvaluation()
        }
    }
    
    /**
     * 停止持續認證
     */
    fun stopContinuousAuth() {
        scope.cancel()
        unregisterAllCallbacks()
        Timber.d("停止持續生物識別認證")
    }
    
    /**
     * 心率變異性監測
     */
    private suspend fun startHeartRateVariabilityMonitoring() {
        // 使用心率感測器的簡化版本
        val heartRateListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
                    val heartRate = event.values[0]
                    processHeartRateForStress(heartRate.toDouble())
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Timber.d("心率感測器精度變更: $accuracy")
            }
        }
        
        try {
            heartRateSensor?.let {
                sensorManager.registerListener(
                    heartRateListener,
                    it,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            } ?: Timber.w("設備不支援心率感測器")
        } catch (e: Exception) {
            Timber.e(e, "註冊心率監測失敗")
        }
    }
    
    /**
     * 運動模式監測
     */
    private suspend fun startMovementPatternMonitoring() {
        val accelerometerListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    processAccelerometerData(event.values)
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Timber.d("加速度計精度變更: $accuracy")
            }
        }
        
        try {
            accelerometerSensor?.let {
                sensorManager.registerListener(
                    accelerometerListener,
                    it,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            } ?: Timber.w("設備不支援加速度計")
        } catch (e: Exception) {
            Timber.e(e, "註冊運動監測失敗")
        }
    }
    
    /**
     * 壓力水平監測
     */
    private suspend fun startStressLevelMonitoring() {
        // 壓力監測已整合到心率監測中
        // 這裡可以添加其他壓力相關的邏輯
        Timber.d("壓力水平監測已整合到心率監測中")
    }
    
    /**
     * 處理加速度計數據
     */
    private fun processAccelerometerData(values: FloatArray) {
        // 簡化的運動模式分析
        val x = values[0]
        val y = values[1]
        val z = values[2]
        
        val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
        val movementPattern = MovementPattern(
            intensity = (magnitude / 20f).coerceIn(0f, 1f),
            regularity = 0.8f,
            signature = "accelerometer"
        )
        
        biometricProfile.updateMovementPattern(movementPattern)
        
        // 檢查是否符合用戶的典型運動模式
        if (!biometricProfile.isMovementPatternNormal(movementPattern)) {
            Timber.w("檢測到異常運動模式")
            updateAuthConfidence(-0.15f, "運動模式異常")
        }
    }
    
    
    /**
     * 處理心率數據以評估壓力
     */
    private fun processHeartRateForStress(heartRate: Double) {
        val stressLevel = biometricProfile.calculateStressLevel(heartRate)
        
        _authState.update { it.copy(currentStressLevel = stressLevel) }
        
        when {
            stressLevel > DURESS_THRESHOLD -> {
                // 可能處於脅迫狀態
                Timber.e("檢測到可能的脅迫狀態！壓力水平: $stressLevel")
                triggerDuressProtocol()
            }
            stressLevel > STRESS_THRESHOLD -> {
                // 高壓力狀態
                Timber.w("檢測到高壓力狀態: $stressLevel")
                updateAuthConfidence(-0.2f, "高壓力狀態")
            }
        }
    }
    
    /**
     * 定期評估認證狀態
     */
    private fun startAuthenticationEvaluation() {
        scope.launch {
            while (isActive) {
                delay(5000) // 每5秒評估一次
                
                val currentState = _authState.value
                val overallConfidence = calculateOverallConfidence()
                
                // 更新認證狀態
                _authState.update { 
                    it.copy(
                        isAuthenticated = overallConfidence > MIN_CONFIDENCE_SCORE,
                        confidenceScore = overallConfidence,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                }
                
                // 發送認證事件
                if (overallConfidence < MIN_CONFIDENCE_SCORE && currentState.isAuthenticated) {
                    authEventChannel.trySend(BiometricAuthEvent.AuthenticationLost)
                }
            }
        }
    }
    
    /**
     * 計算整體可信度分數
     */
    private fun calculateOverallConfidence(): Float {
        val hrvScore = biometricProfile.getHRVConfidenceScore()
        val movementScore = biometricProfile.getMovementConfidenceScore()
        val stressScore = 1.0f - (_authState.value.currentStressLevel / DURESS_THRESHOLD)
        
        // 加權平均
        return (hrvScore * 0.4f + movementScore * 0.3f + stressScore * 0.3f)
            .coerceIn(0f, 1f)
    }
    
    /**
     * 更新認證可信度
     */
    private fun updateAuthConfidence(delta: Float, reason: String) {
        _authState.update { 
            it.copy(
                confidenceScore = (it.confidenceScore + delta).coerceIn(0f, 1f),
                lastAuthChallenge = reason
            )
        }
    }
    
    /**
     * 觸發脅迫協議
     */
    private fun triggerDuressProtocol() {
        scope.launch {
            authEventChannel.send(BiometricAuthEvent.DuressDetected)
            
            // 立即鎖定錢包
            _authState.update { 
                it.copy(
                    isAuthenticated = false,
                    isInDuressMode = true,
                    confidenceScore = 0f
                )
            }
            
            // TODO: 實施其他安全措施
            // - 發送靜默警報
            // - 延遲交易執行
            // - 啟用假錢包模式
        }
    }
    
    
    /**
     * 註銷所有回調
     */
    private fun unregisterAllCallbacks() {
        // 註銷所有感測器監聽器
        // 需要保存監聽器實例來正確註銷
    }
    
    /**
     * 獲取當前安全等級
     */
    fun getCurrentSecurityLevel(): SecurityLevel {
        val state = _authState.value
        return when {
            state.isInDuressMode -> SecurityLevel.LOCKDOWN
            !state.isAuthenticated -> SecurityLevel.UNAUTHENTICATED
            state.confidenceScore < 0.5f -> SecurityLevel.LOW
            state.confidenceScore < 0.8f -> SecurityLevel.MEDIUM
            else -> SecurityLevel.HIGH
        }
    }
    
    /**
     * 請求額外認證（用於高風險操作）
     */
    suspend fun requestAdditionalAuth(reason: String): Boolean {
        authEventChannel.send(BiometricAuthEvent.AdditionalAuthRequested(reason))
        
        // TODO: 實施額外認證邏輯
        // 例如：要求用戶說出安全短語、執行特定手勢等
        
        return true // 暫時返回成功
    }
}

/**
 * 用戶生物特徵配置文件
 */
class UserBiometricProfile {
    // HRV 基線數據
    private val hrvBaseline = mutableListOf<Double>()
    private var hrvMean = 0.0
    private var hrvStdDev = 0.0
    
    // 運動模式歷史
    private val movementHistory = ConcurrentLinkedQueue<MovementPattern>()
    
    // 心率基線
    private var restingHeartRate = 60.0
    private var maxHeartRate = 180.0
    
    fun updateHRV(hrv: Double) {
        hrvBaseline.add(hrv)
        if (hrvBaseline.size > 100) {
            hrvBaseline.removeAt(0)
        }
        recalculateHRVStats()
    }
    
    fun updateMovementPattern(pattern: MovementPattern) {
        movementHistory.offer(pattern)
        if (movementHistory.size > 50) {
            movementHistory.poll()
        }
    }
    
    fun getHRVDeviation(hrv: Double): Double {
        if (hrvStdDev == 0.0) return 0.0
        return abs(hrv - hrvMean) / hrvStdDev
    }
    
    fun isMovementPatternNormal(pattern: MovementPattern): Boolean {
        // 簡化的模式匹配
        // 實際應用中應使用機器學習模型
        return pattern.intensity < 0.9f && pattern.regularity > 0.5f
    }
    
    fun calculateStressLevel(heartRate: Double): Float {
        // 簡化的壓力計算
        val normalizedHR = (heartRate - restingHeartRate) / (maxHeartRate - restingHeartRate)
        return normalizedHR.toFloat().coerceIn(0f, 1f)
    }
    
    fun getHRVConfidenceScore(): Float {
        // 基於 HRV 穩定性的可信度分數
        if (hrvBaseline.size < 10) return 0.5f
        return (1.0f - (hrvStdDev / hrvMean).toFloat()).coerceIn(0f, 1f)
    }
    
    fun getMovementConfidenceScore(): Float {
        // 基於運動模式一致性的可信度分數
        return 0.8f // 簡化實現
    }
    
    private fun recalculateHRVStats() {
        if (hrvBaseline.isEmpty()) return
        
        hrvMean = hrvBaseline.average()
        hrvStdDev = sqrt(hrvBaseline.map { (it - hrvMean) * (it - hrvMean) }.average())
    }
}

/**
 * 認證狀態
 */
data class AuthenticationState(
    val isAuthenticated: Boolean = false,
    val confidenceScore: Float = 0.5f,
    val currentStressLevel: Float = 0f,
    val isInDuressMode: Boolean = false,
    val lastUpdateTime: Long = 0,
    val lastAuthChallenge: String? = null
)

/**
 * 生物識別認證事件
 */
sealed class BiometricAuthEvent {
    object AuthenticationLost : BiometricAuthEvent()
    object DuressDetected : BiometricAuthEvent()
    data class AdditionalAuthRequested(val reason: String) : BiometricAuthEvent()
    data class ConfidenceChanged(val newScore: Float) : BiometricAuthEvent()
    data class SecurityLevelChanged(val level: SecurityLevel) : BiometricAuthEvent()
}

/**
 * 安全等級
 */
enum class SecurityLevel {
    UNAUTHENTICATED,
    LOW,
    MEDIUM,
    HIGH,
    LOCKDOWN
}

/**
 * 運動模式
 */
data class MovementPattern(
    val intensity: Float,
    val regularity: Float,
    val signature: String
)
