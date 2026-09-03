package com.cbstudio.wearwallet.presentation.voice.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 市場分析語音 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 語音介面維護模式修復
 */
class MarketAnalysisVoiceViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All market analysis disabled
    private val _voiceResult = MutableStateFlow("")
    val voiceResult: StateFlow<String> = _voiceResult.asStateFlow()
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _analysisResult = MutableStateFlow("維護模式")
    val analysisResult: StateFlow<String> = _analysisResult.asStateFlow()
    
    private val _topGainers = MutableStateFlow<Map<String, String>>(emptyMap())
    val topGainers: StateFlow<Map<String, String>> = _topGainers.asStateFlow()
    
    private val _topLosers = MutableStateFlow<Map<String, String>>(emptyMap())
    val topLosers: StateFlow<Map<String, String>> = _topLosers.asStateFlow()
    
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
        _voiceResult.value = "維護模式：市場分析暫時停用"
    }
    
    fun analyzeMarket() {
        // MAINTENANCE MODE: Market analysis disabled
        _analysisResult.value = "維護模式：市場分析功能暫時停用"
    }
}