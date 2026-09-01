package com.cbstudio.wearwallet.presentation.qrscanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.data.repository.QRScanType
import com.cbstudio.wearwallet.data.repository.WearCommunicationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * QR 掃描器 ViewModel - 透過手機掃描
 * 使用 WearCommunicationRepository 與手機通訊
 */
class QrScannerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val communicationRepository = WearCommunicationRepository.getInstance(application)
    
    data class QrScannerUiState(
        val isRequestingPhone: Boolean = false,
        val isWaitingForResult: Boolean = false,
        val scanResult: String? = null,
        val errorMessage: String? = null,
        val scanType: QRScanType = QRScanType.ADDRESS
    )
    
    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()
    
    init {
        // 監聽掃描結果
        viewModelScope.launch {
            communicationRepository.qrScanResult.collect { result ->
                Timber.d("收到 QR 掃描結果: $result")
                _uiState.update { 
                    it.copy(
                        scanResult = result,
                        isWaitingForResult = false,
                        isRequestingPhone = false
                    )
                }
            }
        }
        
        // 監聽錯誤訊息
        viewModelScope.launch {
            communicationRepository.errorMessage.collect { error ->
                if (error != null) {
                    _uiState.update { 
                        it.copy(
                            errorMessage = error,
                            isWaitingForResult = false,
                            isRequestingPhone = false
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 請求手機掃描 QR Code
     */
    fun requestPhoneScan(type: QRScanType = QRScanType.ADDRESS) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isRequestingPhone = true,
                    scanType = type,
                    errorMessage = null,
                    scanResult = null
                )
            }
            
            // 檢查手機是否連接
            if (!communicationRepository.isPhoneConnected()) {
                _uiState.update { 
                    it.copy(
                        errorMessage = getApplication<Application>().getString(com.cbstudio.wearwallet.R.string.error_ensure_phone_connected),
                        isRequestingPhone = false
                    )
                }
                return@launch
            }
            
            // 發送掃描請求
            val success = communicationRepository.requestQRScan(type)
            
            if (success) {
                _uiState.update { 
                    it.copy(
                        isWaitingForResult = true,
                        isRequestingPhone = false
                    )
                }
            } else {
                _uiState.update { 
                    it.copy(
                        errorMessage = getApplication<Application>().getString(com.cbstudio.wearwallet.R.string.error_send_scan_request_failed),
                        isRequestingPhone = false
                    )
                }
            }
        }
    }
    
    /**
     * 設置掃描類型
     */
    fun setScanType(type: QRScanType) {
        _uiState.update { it.copy(scanType = type) }
    }
    
    /**
     * 清除掃描結果
     */
    fun clearScanResult() {
        _uiState.update { 
            it.copy(
                scanResult = null,
                errorMessage = null
            )
        }
    }
    
    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * 重試掃描
     */
    fun retry() {
        clearScanResult()
        requestPhoneScan(_uiState.value.scanType)
    }
}