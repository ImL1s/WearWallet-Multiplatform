package com.cbstudio.wearwallet.presentation.biometric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.domain.biometric.BiometricAuthService
import com.cbstudio.wearwallet.core.domain.model.RiskLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 生物識別認證 ViewModel
 * 使用 coreKmp 架構 - 當前為基礎實現
 */
class BiometricAuthViewModel : ViewModel(), KoinComponent {
    
    private val biometricAuthService: BiometricAuthService by inject()
    
    private val _uiState = MutableStateFlow(BiometricAuthUiState())
    val uiState: StateFlow<BiometricAuthUiState> = _uiState.asStateFlow()
    
    init {
        // 檢查生物識別支援
        checkBiometricSupport()
    }
    
    /**
     * 檢查生物識別支援
     */
    private fun checkBiometricSupport() {
        val isSupported = biometricAuthService.isSupported()
        _uiState.update { 
            it.copy(
                sensorStatus = mapOf(
                    "生物識別" to isSupported
                ),
                statusMessage = if (isSupported) {
                    "生物識別可用"
                } else {
                    "生物識別不可用"
                }
            )
        }
    }
    
    /**
     * 開始認證
     */
    fun startAuthentication() {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        authState = AuthState.COLLECTING,
                        statusMessage = "請自然地移動手腕...",
                        progress = 0f
                    )
                }
                
                // 檢查生物識別支援
                if (!biometricAuthService.isSupported()) {
                    _uiState.update { 
                        it.copy(
                            authState = AuthState.COMPLETED,
                            authResult = BiometricAuthResult(
                                isAuthenticated = false,
                                confidence = 0f,
                                matchScore = 0f,
                                anomalyScore = 1f,
                                riskLevel = RiskLevel.HIGH,
                                recommendations = listOf("此設備不支援生物識別認證")
                            ),
                            statusMessage = "生物識別不可用",
                            progress = 0f
                        )
                    }
                    return@launch
                }
                
                // 模擬數據收集進度（未來將替換為真實生物識別）
                repeat(10) { i ->
                    delay(300)
                    _uiState.update { 
                        it.copy(
                            progress = (i + 1) / 10f,
                            statusMessage = "收集生物特徵中... ${(i + 1) * 10}%"
                        )
                    }
                }
                
                // 切換到分析狀態
                _uiState.update { 
                    it.copy(
                        authState = AuthState.ANALYZING,
                        statusMessage = "分析生物特徵...",
                        progress = 0f
                    )
                }
                
                // 執行認證
                val isAuthenticated = biometricAuthService.authenticate()
                
                // 根據認證結果建立回應
                val result = if (isAuthenticated) {
                    BiometricAuthResult(
                        isAuthenticated = true,
                        confidence = 0.95f,
                        matchScore = 0.92f,
                        anomalyScore = 0.08f,
                        riskLevel = RiskLevel.LOW,
                        recommendations = emptyList()
                    )
                } else {
                    BiometricAuthResult(
                        isAuthenticated = false,
                        confidence = 0.2f,
                        matchScore = 0.3f,
                        anomalyScore = 0.7f,
                        riskLevel = RiskLevel.MEDIUM,
                        recommendations = listOf(
                            "生物識別失敗，請再試一次",
                            "確保感應器清潔",
                            "保持手指穩定"
                        )
                    )
                }
                
                // 模擬分析進度
                repeat(5) { i ->
                    delay(200)
                    _uiState.update { 
                        it.copy(
                            progress = (i + 1) / 5f,
                            statusMessage = "驗證中... ${(i + 1) * 20}%"
                        )
                    }
                }
                
                // 更新結果
                _uiState.update { 
                    it.copy(
                        authState = AuthState.COMPLETED,
                        authResult = result,
                        statusMessage = if (result.isAuthenticated) {
                            "身份驗證成功"
                        } else {
                            "身份驗證失敗: ${result.recommendations.firstOrNull() ?: "請重試"}"
                        },
                        riskLevel = result.riskLevel,
                        progress = 1f
                    )
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Authentication failed")
                _uiState.update { 
                    it.copy(
                        authState = AuthState.COMPLETED,
                        authResult = BiometricAuthResult(
                            isAuthenticated = false,
                            confidence = 0f,
                            matchScore = 0f,
                            anomalyScore = 1f,
                            riskLevel = RiskLevel.HIGH,
                            recommendations = listOf("認證過程發生錯誤，請重試")
                        ),
                        statusMessage = "認證失敗",
                        progress = 0f
                    )
                }
            } finally {
                // 重置進度
                delay(2000) // 給用戶時間看到結果
                _uiState.update { 
                    it.copy(
                        progress = 0f,
                        dataPointsCollected = 0,
                        isSessionActive = false
                    )
                }
            }
        }
    }
    
    /**
     * 重試認證
     */
    fun retryAuthentication() {
        _uiState.value = BiometricAuthUiState(
            sensorStatus = _uiState.value.sensorStatus
        )
        startAuthentication()
    }
    
    /**
     * 清除認證結果
     */
    fun clearAuthResult() {
        _uiState.update { 
            it.copy(
                authState = AuthState.IDLE,
                authResult = null,
                statusMessage = "",
                progress = 0f
            )
        }
    }
    
    /**
     * 取消認證
     */
    fun cancelAuthentication() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    authState = AuthState.IDLE,
                    statusMessage = "認證已取消",
                    progress = 0f
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 清理資源
        clearAuthResult()
    }
}

/**
 * UI 狀態
 */
data class BiometricAuthUiState(
    val authState: AuthState = AuthState.IDLE,
    val authResult: BiometricAuthResult? = null,
    val statusMessage: String = "",
    val progress: Float = 0f,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val sensorStatus: Map<String, Boolean> = emptyMap(),
    val isSessionActive: Boolean = false,
    val dataPointsCollected: Int = 0
)

data class BiometricAuthResult(
    val isAuthenticated: Boolean,
    val confidence: Float,
    val matchScore: Float,
    val anomalyScore: Float,
    val riskLevel: RiskLevel,
    val recommendations: List<String>
)
