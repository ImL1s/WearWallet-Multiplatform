package com.cbstudio.wearwallet.core.security

/**
 * 跨平台認證物件抽象 (Expect Class PlatformCryptoObject)
 *
 * Android: androidx.biometric.BiometricPrompt.CryptoObject
 * iOS: platform.LocalAuthentication.LAContext
 * watchOS: platform.LocalAuthentication.LAContext
 */
expect class PlatformCryptoObject
