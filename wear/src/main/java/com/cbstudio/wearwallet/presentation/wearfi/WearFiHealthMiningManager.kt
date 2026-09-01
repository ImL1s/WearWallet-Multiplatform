package com.cbstudio.wearwallet.presentation.wearfi

import kotlinx.coroutines.flow.*

/**
 * WearFi 健康挖礦管理器 - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - WearFi 組件維護模式修復
 */
class WearFiHealthMiningManager {
    
    // MAINTENANCE MODE: All health mining disabled
    private val _miningProgress = MutableStateFlow(0.0)
    val miningProgress: StateFlow<Double> = _miningProgress.asStateFlow()
    
    private val _earnedTokens = MutableStateFlow("0")
    val earnedTokens: StateFlow<String> = _earnedTokens.asStateFlow()
    
    private val _healthData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val healthData: StateFlow<Map<String, Any>> = _healthData.asStateFlow()
    
    fun startMining() {
        // MAINTENANCE MODE: Mining disabled
        _earnedTokens.value = "維護模式"
    }
    
    fun stopMining() {
        // MAINTENANCE MODE: Mining already disabled
    }
    
    fun collectHealthData() {
        // MAINTENANCE MODE: Health data collection disabled
        _healthData.value = mapOf("status" to "維護模式")
    }
    
    fun claimRewards() {
        // MAINTENANCE MODE: Rewards claiming disabled
        _earnedTokens.value = "維護模式：獎勵領取暫時停用"
    }
}