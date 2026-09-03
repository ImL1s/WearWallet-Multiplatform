package com.cbstudio.wearwallet.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.cbstudio.wearwallet.shared.utils.Logger
import com.cbstudio.wearwallet.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.*

/**
 * 行為生物識別引擎
 * 
 * 實現多層次安全驗證系統：
 * 1. 靜態生物識別（指紋、面部）
 * 2. 動態行為分析（手腕動作、使用模式）
 * 3. 環境感知（位置、時間、設備配對）
 * 
 * 使用機器學習持續驗證用戶身份，提供無縫安全體驗
 */
class BehavioralBiometricEngine : SensorEventListener, KoinComponent {
    
    private val context: Context by inject<Context>()
    
    companion object {
        private const val TAG = "BehavioralBiometricEngine"
        
        // 安全等級閾值
        const val HIGH_SECURITY_THRESHOLD = 0.85f
        const val MEDIUM_SECURITY_THRESHOLD = 0.65f
        const val LOW_SECURITY_THRESHOLD = 0.45f
        
        // 行為特徵權重
        const val WEIGHT_STATIC_BIOMETRIC = 0.3f
        const val WEIGHT_MOVEMENT_PATTERN = 0.25f
        const val WEIGHT_USAGE_PATTERN = 0.2f
        const val WEIGHT_ENVIRONMENT = 0.15f
        const val WEIGHT_DEVICE_PAIRING = 0.1f
        
        // 時間窗口
        const val BEHAVIOR_WINDOW_SIZE = 30_000L // 30 秒
        const val PATTERN_LEARNING_PERIOD = 7 * 24 * 60 * 60 * 1000L // 7 天
        
        // 行為特徵類型
        enum class BehaviorType {
            WRIST_MOVEMENT,      // 手腕動作
            TOUCH_PATTERN,       // 觸控模式
            APP_USAGE,          // 應用使用
            TRANSACTION_PATTERN, // 交易模式
            TIME_PATTERN,       // 時間模式
            LOCATION_PATTERN    // 位置模式
        }
        
        // 認證結果
        sealed class AuthenticationResult {
            data class Success(
                val score: Float,
                val confidence: Float,
                val factors: Map<String, Float>
            ) : AuthenticationResult()
            
            data class Failed(
                val reason: String,
                val anomalies: List<Anomaly>
            ) : AuthenticationResult()
            
            data class RequireAdditionalVerification(
                val requiredFactors: List<String>,
                val currentScore: Float
            ) : AuthenticationResult()
        }
        
        // 異常檢測
        data class Anomaly(
            val type: AnomalyType,
            val severity: Float,
            val description: String,
            val timestamp: Long
        )
        
        enum class AnomalyType {
            UNUSUAL_MOVEMENT,      // 異常動作
            UNUSUAL_LOCATION,      // 異常位置
            UNUSUAL_TIME,         // 異常時間
            UNUSUAL_TRANSACTION,   // 異常交易
            DEVICE_MISMATCH,      // 設備不匹配
            RAPID_ATTEMPTS        // 快速嘗試
        }
    }
    
    // 感測器管理
    private val sensorManager: SensorManager = 
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    // 生物識別管理
    private val biometricManager = BiometricManager.from(context)
    
    // 行為數據流
    private val _behaviorData = MutableSharedFlow<BehaviorData>()
    val behaviorData: SharedFlow<BehaviorData> = _behaviorData.asSharedFlow()
    
    // 認證分數流
    private val _authScore = MutableStateFlow(0.5f)
    val authScore: StateFlow<Float> = _authScore.asStateFlow()
    
    // 異常檢測流
    private val _anomalies = MutableSharedFlow<Anomaly>()
    val anomalies: SharedFlow<Anomaly> = _anomalies.asSharedFlow()
    
    // 行為模式儲存
    private val behaviorPatterns = ConcurrentHashMap<String, BehaviorPattern>()
    private val movementBuffer = mutableListOf<MovementData>()
    private val touchPatterns = mutableListOf<TouchPattern>()
    private val usageHistory = mutableListOf<UsageRecord>()
    
    // 機器學習模型（簡化版）
    private val mlModel = SimpleBehaviorModel()
    
    // 協程作用域
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // 初始化感測器
        initializeSensors()
        
        // 載入歷史行為模式
        loadBehaviorPatterns()
        
        // 開始持續認證
        startContinuousAuthentication()
    }
    
    /**
     * 初始化感測器
     */
    private fun initializeSensors() {
        try {
            // 加速度計
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
                sensorManager.registerListener(
                    this, 
                    sensor, 
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
            
            // 陀螺儀
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensor ->
                sensorManager.registerListener(
                    this, 
                    sensor, 
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
            
            // 心率感測器（如果可用）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let { sensor ->
                    sensorManager.registerListener(
                        this, 
                        sensor, 
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                }
            }
            
            Logger.d(TAG, "Sensors initialized successfully")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize sensors", e)
        }
    }
    
    /**
     * 載入歷史行為模式
     */
    private fun loadBehaviorPatterns() {
        engineScope.launch {
            try {
                // TODO: 從加密存儲載入用戶行為模式
                // 這裡使用模擬數據
                behaviorPatterns["movement"] = BehaviorPattern(
                    type = BehaviorType.WRIST_MOVEMENT,
                    features = generateDefaultMovementPattern(),
                    confidence = 0.8f
                )
                
                behaviorPatterns["usage"] = BehaviorPattern(
                    type = BehaviorType.APP_USAGE,
                    features = generateDefaultUsagePattern(),
                    confidence = 0.75f
                )
                
                Logger.d(TAG, "Behavior patterns loaded: ${behaviorPatterns.size}")
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load behavior patterns", e)
            }
        }
    }
    
    /**
     * 開始持續認證
     */
    private fun startContinuousAuthentication() {
        engineScope.launch {
            // 收集行為數據並計算認證分數  
            val dataBuffer = mutableListOf<BehaviorData>()
            
            behaviorData.collect { data ->
                dataBuffer.add(data)
                
                // 當緩衝區達到指定大小時處理
                if (dataBuffer.size >= 10) {
                    val dataList = dataBuffer.toList()
                    dataBuffer.clear()
                    
                    val score = calculateAuthenticationScore(dataList)
                    _authScore.value = score
                    
                    // 檢測異常
                    detectAnomalies(dataList, score)
                    
                    // 更新學習模型
                    updateBehaviorModel(dataList)
                }
            }
        }
        
        // 定期更新環境因素
        engineScope.launch {
            while (isActive) {
                updateEnvironmentFactors()
                delay(60_000) // 每分鐘更新
            }
        }
    }
    
    /**
     * 執行生物識別認證
     */
    suspend fun performBiometricAuthentication(): AuthenticationResult {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val canAuthenticate = when (biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                    )) {
                        BiometricManager.BIOMETRIC_SUCCESS -> true
                        else -> false
                    }
                    
                    if (!canAuthenticate) {
                        continuation.resume(
                            AuthenticationResult.Failed(
                                reason = context.getString(R.string.biometric_unavailable),
                                anomalies = emptyList()
                            )
                        )
                        return@suspendCancellableCoroutine
                    }
                    
                    // 這裡簡化處理，實際應該使用 BiometricPrompt
                    val staticScore = 0.9f // 假設生物識別成功
                    
                    // 結合動態行為分數
                    val behaviorScore = _authScore.value
                    val environmentScore = calculateEnvironmentScore()
                    
                    val totalScore = calculateWeightedScore(
                        mapOf(
                            "static" to staticScore,
                            "behavior" to behaviorScore,
                            "environment" to environmentScore
                        )
                    )
                    
                    continuation.resume(
                        AuthenticationResult.Success(
                            score = totalScore,
                            confidence = calculateConfidence(totalScore),
                            factors = mapOf(
                                context.getString(R.string.biometric_factor_static) to staticScore,
                                context.getString(R.string.biometric_factor_behavior) to behaviorScore,
                                context.getString(R.string.biometric_factor_environment) to environmentScore
                            )
                        )
                    )
                    
                } catch (e: Exception) {
                    Logger.e(TAG, "Biometric authentication failed", e)
                    continuation.resume(
                        AuthenticationResult.Failed(
                            reason = e.message ?: context.getString(R.string.error_unknown),
                            anomalies = emptyList()
                        )
                    )
                }
            }
        }
    }
    
    /**
     * 驗證交易
     */
    suspend fun verifyTransaction(
        amount: Double,
        recipient: String,
        transactionType: String
    ): AuthenticationResult {
        return withContext(Dispatchers.Default) {
            try {
                // 檢查交易模式
                val isNormalTransaction = checkTransactionPattern(amount, recipient, transactionType)
                
                // 獲取當前認證分數
                val currentScore = _authScore.value
                
                // 根據交易金額調整所需安全等級
                val requiredScore = when {
                    amount > 1000 -> HIGH_SECURITY_THRESHOLD
                    amount > 100 -> MEDIUM_SECURITY_THRESHOLD
                    else -> LOW_SECURITY_THRESHOLD
                }
                
                if (!isNormalTransaction) {
                    // 檢測到異常交易
                    val anomaly = Anomaly(
                        type = AnomalyType.UNUSUAL_TRANSACTION,
                        severity = 0.8f,
                        description = context.getString(R.string.anomaly_unusual_transaction),
                        timestamp = System.currentTimeMillis()
                    )
                    _anomalies.emit(anomaly)
                    
                    return@withContext AuthenticationResult.RequireAdditionalVerification(
                        requiredFactors = listOf(
                            context.getString(R.string.biometric_factor_static),
                            context.getString(R.string.biometric_factor_security_question)
                        ),
                        currentScore = currentScore
                    )
                }
                
                if (currentScore >= requiredScore) {
                    AuthenticationResult.Success(
                        score = currentScore,
                        confidence = calculateConfidence(currentScore),
                        factors = getCurrentFactors()
                    )
                } else {
                    AuthenticationResult.RequireAdditionalVerification(
                        requiredFactors = determineRequiredFactors(currentScore, requiredScore),
                        currentScore = currentScore
                    )
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Transaction verification failed", e)
                AuthenticationResult.Failed(
                    reason = context.getString(R.string.error_transaction_verification_failed),
                    anomalies = emptyList()
                )
            }
        }
    }
    
    /**
     * 記錄觸控模式
     */
    fun recordTouchPattern(
        x: Float,
        y: Float,
        pressure: Float,
        duration: Long
    ) {
        engineScope.launch {
            val pattern = TouchPattern(
                x = x,
                y = y,
                pressure = pressure,
                duration = duration,
                timestamp = System.currentTimeMillis()
            )
            
            touchPatterns.add(pattern)
            
            // 保持緩衝區大小
            if (touchPatterns.size > 100) {
                touchPatterns.removeAt(0)
            }
            
            // 分析觸控模式
            analyzeTouchPattern(pattern)
        }
    }
    
    /**
     * 記錄應用使用
     */
    fun recordAppUsage(
        appName: String,
        action: String,
        duration: Long
    ) {
        engineScope.launch {
            val record = UsageRecord(
                appName = appName,
                action = action,
                duration = duration,
                timestamp = System.currentTimeMillis()
            )
            
            usageHistory.add(record)
            
            // 保持歷史記錄大小
            if (usageHistory.size > 1000) {
                usageHistory.removeAt(0)
            }
            
            // 更新使用模式
            updateUsagePattern(record)
        }
    }
    
    // === 感測器事件處理 ===
    
    override fun onSensorChanged(event: SensorEvent) {
        engineScope.launch {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    handleAccelerometerData(event.values)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    handleGyroscopeData(event.values)
                }
                Sensor.TYPE_HEART_RATE -> {
                    handleHeartRateData(event.values[0])
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 處理感測器精度變化
    }
    
    // === 私有輔助方法 ===
    
    private suspend fun handleAccelerometerData(values: FloatArray) {
        val movement = MovementData(
            x = values[0],
            y = values[1],
            z = values[2],
            timestamp = System.currentTimeMillis()
        )
        
        movementBuffer.add(movement)
        
        // 保持緩衝區大小
        if (movementBuffer.size > 100) {
            movementBuffer.removeAt(0)
        }
        
        // 分析手腕動作
        analyzeWristMovement(movement)
    }
    
    private suspend fun handleGyroscopeData(values: FloatArray) {
        val rotation = RotationData(
            x = values[0],
            y = values[1],
            z = values[2],
            timestamp = System.currentTimeMillis()
        )
        
        // 分析旋轉模式
        analyzeRotationPattern(rotation)
    }
    
    private suspend fun handleHeartRateData(heartRate: Float) {
        // 心率可以作為壓力或活動水平的指標
        val behaviorData = BehaviorData(
            type = BehaviorType.WRIST_MOVEMENT,
            features = mapOf("heartRate" to heartRate),
            timestamp = System.currentTimeMillis()
        )
        
        _behaviorData.emit(behaviorData)
    }
    
    private suspend fun analyzeWristMovement(movement: MovementData) {
        // 計算移動特徵
        val magnitude = sqrt(
            movement.x * movement.x + 
            movement.y * movement.y + 
            movement.z * movement.z
        )
        
        // 檢測特定手勢（例如：抬腕查看）
        if (magnitude > 10f && movement.z > 5f) {
            val behaviorData = BehaviorData(
                type = BehaviorType.WRIST_MOVEMENT,
                features = mapOf(
                    "gesture" to 1f, // 抬腕手勢
                    "magnitude" to magnitude
                ),
                timestamp = movement.timestamp
            )
            
            _behaviorData.emit(behaviorData)
        }
    }
    
    private suspend fun analyzeRotationPattern(rotation: RotationData) {
        // 分析旋轉模式以識別用戶特定的手腕動作
        val pattern = extractRotationFeatures(rotation)
        
        val behaviorData = BehaviorData(
            type = BehaviorType.WRIST_MOVEMENT,
            features = pattern,
            timestamp = rotation.timestamp
        )
        
        _behaviorData.emit(behaviorData)
    }
    
    private suspend fun analyzeTouchPattern(pattern: TouchPattern) {
        // 分析觸控特徵
        val features = mapOf(
            "pressure" to pattern.pressure,
            "duration" to pattern.duration.toFloat(),
            "location_x" to pattern.x,
            "location_y" to pattern.y
        )
        
        val behaviorData = BehaviorData(
            type = BehaviorType.TOUCH_PATTERN,
            features = features,
            timestamp = pattern.timestamp
        )
        
        _behaviorData.emit(behaviorData)
    }
    
    private suspend fun updateUsagePattern(record: UsageRecord) {
        // 更新應用使用模式
        val hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        
        val features = mapOf(
            "hour" to hourOfDay.toFloat(),
            "dayOfWeek" to dayOfWeek.toFloat(),
            "duration" to record.duration.toFloat()
        )
        
        val behaviorData = BehaviorData(
            type = BehaviorType.APP_USAGE,
            features = features,
            timestamp = record.timestamp
        )
        
        _behaviorData.emit(behaviorData)
    }
    
    private suspend fun updateEnvironmentFactors() {
        try {
            // 獲取當前位置（需要權限）
            // 這裡簡化處理
            val locationScore = 0.8f // 假設在常用位置
            
            // 檢查配對設備
            val deviceScore = checkPairedDevices()
            
            // 時間模式
            val timeScore = checkTimePattern()
            
            val environmentData = BehaviorData(
                type = BehaviorType.LOCATION_PATTERN,
                features = mapOf(
                    "location" to locationScore,
                    "device" to deviceScore,
                    "time" to timeScore
                ),
                timestamp = System.currentTimeMillis()
            )
            
            _behaviorData.emit(environmentData)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update environment factors", e)
        }
    }
    
    private fun calculateAuthenticationScore(dataList: List<BehaviorData>): Float {
        // 使用簡化的機器學習模型計算分數
        return mlModel.predict(dataList)
    }
    
    private suspend fun detectAnomalies(dataList: List<BehaviorData>, score: Float) {
        // 檢測異常行為
        if (score < LOW_SECURITY_THRESHOLD) {
            val anomaly = Anomaly(
                type = AnomalyType.UNUSUAL_MOVEMENT,
                severity = 1f - score,
                description = context.getString(R.string.anomaly_unusual_behavior),
                timestamp = System.currentTimeMillis()
            )
            _anomalies.emit(anomaly)
        }
    }
    
    private fun updateBehaviorModel(dataList: List<BehaviorData>) {
        // 更新機器學習模型
        mlModel.update(dataList)
    }
    
    private fun calculateEnvironmentScore(): Float {
        // 計算環境分數
        return 0.75f // 簡化實現
    }
    
    private fun calculateWeightedScore(scores: Map<String, Float>): Float {
        val weights = mapOf(
            "static" to WEIGHT_STATIC_BIOMETRIC,
            "behavior" to WEIGHT_MOVEMENT_PATTERN + WEIGHT_USAGE_PATTERN,
            "environment" to WEIGHT_ENVIRONMENT + WEIGHT_DEVICE_PAIRING
        )
        
        return scores.entries.map { (key, value) ->
            (weights[key] ?: 0f) * value
        }.sum()
    }
    
    private fun calculateConfidence(score: Float): Float {
        // 基於分數計算置信度
        return when {
            score > HIGH_SECURITY_THRESHOLD -> 0.95f
            score > MEDIUM_SECURITY_THRESHOLD -> 0.75f
            score > LOW_SECURITY_THRESHOLD -> 0.55f
            else -> 0.3f
        }
    }
    
    private fun checkTransactionPattern(
        amount: Double,
        recipient: String,
        type: String
    ): Boolean {
        // 檢查交易是否符合正常模式
        // 簡化實現
        return amount < 5000 // 假設 5000 以下為正常
    }
    
    private fun getCurrentFactors(): Map<String, Float> {
        return mapOf(
            context.getString(R.string.biometric_factor_behavior) to _authScore.value,
            context.getString(R.string.biometric_factor_environment) to calculateEnvironmentScore(),
            context.getString(R.string.biometric_factor_device_pairing) to checkPairedDevices()
        )
    }
    
    private fun determineRequiredFactors(
        currentScore: Float,
        requiredScore: Float
    ): List<String> {
        val factors = mutableListOf<String>()
        
        val gap = requiredScore - currentScore
        
        if (gap > 0.3f) {
            factors.add(context.getString(R.string.biometric_factor_static))
            factors.add(context.getString(R.string.biometric_factor_security_question))
        } else if (gap > 0.15f) {
            factors.add(context.getString(R.string.biometric_factor_static))
        } else {
            factors.add(context.getString(R.string.biometric_factor_pin))
        }
        
        return factors
    }
    
    private fun checkPairedDevices(): Float {
        // 檢查配對設備
        // 簡化實現
        return 0.8f
    }
    
    private fun checkTimePattern(): Float {
        // 檢查時間模式
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        // 假設用戶通常在 6-23 點使用
        return if (hour in 6..23) 1.0f else 0.5f
    }
    
    private fun extractRotationFeatures(rotation: RotationData): Map<String, Float> {
        return mapOf(
            "rotation_x" to rotation.x,
            "rotation_y" to rotation.y,
            "rotation_z" to rotation.z,
            "magnitude" to sqrt(rotation.x * rotation.x + rotation.y * rotation.y + rotation.z * rotation.z)
        )
    }
    
    private fun generateDefaultMovementPattern(): Map<String, Float> {
        return mapOf(
            "avg_magnitude" to 8.5f,
            "std_deviation" to 2.3f,
            "peak_frequency" to 1.2f
        )
    }
    
    private fun generateDefaultUsagePattern(): Map<String, Float> {
        return mapOf(
            "morning_usage" to 0.3f,
            "afternoon_usage" to 0.4f,
            "evening_usage" to 0.3f,
            "avg_session_duration" to 120f // 秒
        )
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        sensorManager.unregisterListener(this)
        engineScope.cancel()
    }
}

// === 數據類 ===

data class BehaviorData(
    val type: BehavioralBiometricEngine.Companion.BehaviorType,
    val features: Map<String, Float>,
    val timestamp: Long
)

data class BehaviorPattern(
    val type: BehavioralBiometricEngine.Companion.BehaviorType,
    val features: Map<String, Float>,
    val confidence: Float,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class MovementData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long
)

data class RotationData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long
)

data class TouchPattern(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val duration: Long,
    val timestamp: Long
)

data class UsageRecord(
    val appName: String,
    val action: String,
    val duration: Long,
    val timestamp: Long
)

/**
 * 簡化的行為模型
 */
class SimpleBehaviorModel {
    private var baselineScore = 0.7f
    private val featureWeights = mutableMapOf<String, Float>()
    
    fun predict(dataList: List<BehaviorData>): Float {
        if (dataList.isEmpty()) return baselineScore
        
        // 簡單的加權平均
        val scores = dataList.map { data ->
            data.features.entries.sumOf { (key, value) ->
                val weight = featureWeights.getOrDefault(key, 0.5f)
                (weight * normalizeValue(value)).toDouble()
            }.toFloat() / data.features.size
        }
        
        return scores.average().toFloat().coerceIn(0f, 1f)
    }
    
    fun update(dataList: List<BehaviorData>) {
        // 簡單的權重更新
        dataList.forEach { data ->
            data.features.forEach { (key, value) ->
                val currentWeight = featureWeights.getOrDefault(key, 0.5f)
                // 漸進式更新
                featureWeights[key] = currentWeight * 0.9f + normalizeValue(value) * 0.1f
            }
        }
    }
    
    private fun normalizeValue(value: Float): Float {
        // 簡單的正規化
        return (value / 100f).coerceIn(0f, 1f)
    }
}
