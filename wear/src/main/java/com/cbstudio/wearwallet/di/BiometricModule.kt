package com.cbstudio.wearwallet.di

import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import android.content.Context
import com.cbstudio.wearwallet.domain.biometric.BehavioralBiometricEngine
import com.cbstudio.wearwallet.domain.biometric.BiometricAuthService
import com.cbstudio.wearwallet.domain.biometric.MotionSensorCollector
/**
 * 生物識別模組 DI 配置
 */
val biometricModule = module {
    
    single { MotionSensorCollector(androidContext()) }
    
    single { BehavioralBiometricEngine(get()) }
    
    single { BiometricAuthService() }
}
