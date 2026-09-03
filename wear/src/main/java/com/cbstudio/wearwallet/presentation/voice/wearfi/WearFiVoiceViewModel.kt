package com.cbstudio.wearwallet.presentation.voice.wearfi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * WearFi 語音 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 語音介面維護模式修復
 */
class WearFiVoiceViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All WearFi voice operations disabled
    private val _voiceResult = MutableStateFlow("")
    val voiceResult: StateFlow<String> = _voiceResult.asStateFlow()
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _wearfiData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val wearfiData: StateFlow<Map<String, Any>> = _wearfiData.asStateFlow()
    
    fun startListening() {
        // MAINTENANCE MODE: Voice recognition disabled
        _isListening.value = true
        
        viewModelScope.launch {
            _voiceResult.value = "維護模式：WearFi 語音功能暫時停用"
            _isListening.value = false
        }
    }
    
    fun stopListening() {
        _isListening.value = false
    }
    
    fun processVoiceCommand(command: String) {
        // MAINTENANCE MODE: Command processing disabled
        _voiceResult.value = "維護模式：WearFi 指令處理暫時停用"
    }
    
    fun getWearFiStatus() {
        // MAINTENANCE MODE: WearFi status disabled
        _wearfiData.value = mapOf("status" to "維護模式")
    }
}