package com.cbstudio.wearwallet.domain.biometric

import android.content.Context

/**
 * Motion Sensor Collector - MAINTENANCE MODE
 * ULTRATHINK Phase 16 - 激進服務禁用策略
 * 
 * TODO: Complex motion sensor operations temporarily disabled for maintenance
 * - All biometric data collection functionality disabled  
 * - Keep service structure consistent for future implementation
 * - Focus on core compilation stability
 */
class MotionSensorCollector(private val context: Context) {
    // MAINTENANCE MODE: All motion sensor data collection disabled
    fun startCollecting() { /* disabled */ }
    fun stopCollecting() { /* disabled */ }
    fun isCollecting(): Boolean = false
}