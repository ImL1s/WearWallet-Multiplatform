package com.cbstudio.wearwallet.presentation.voice.gas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Gas 優化語音 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 語音介面維護模式修復
 */
class GasOptimizationVoiceViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All voice processing disabled
    private val _voiceResult = MutableStateFlow("")
    val voiceResult: StateFlow<String> = _voiceResult.asStateFlow()
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _recommendedGasPrice = MutableStateFlow("0")
    val recommendedGasPrice: StateFlow<String> = _recommendedGasPrice.asStateFlow()
    
    fun startListening() {
        // MAINTENANCE MODE: Voice recognition disabled
        _isListening.value = true
        
        viewModelScope.launch {
            _voiceResult.value = "維護模式：語音功能暫時停用"
            _isListening.value = false
        }
    }
    
    fun stopListening() {
        _isListening.value = false
    }
    
    fun processVoiceCommand(command: String) {
        // MAINTENANCE MODE: Command processing disabled
        _voiceResult.value = "維護模式：指令處理暫時停用"
    }
    
    fun optimizeGas() {
        // MAINTENANCE MODE: Gas optimization disabled
        _recommendedGasPrice.value = "維護模式"
    }
}