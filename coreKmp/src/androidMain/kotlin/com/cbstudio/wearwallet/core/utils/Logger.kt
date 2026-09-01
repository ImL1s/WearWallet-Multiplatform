package com.cbstudio.wearwallet.core.utils

import android.util.Log

/**
 * Android 平台的 Logger 實現 (含 JVM 單元測試防護)
 */
actual object Logger {
    actual fun d(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (e: Throwable) {
            println("DEBUG: [$tag] $message")
        }
    }
    
    actual fun i(tag: String, message: String) {
        try {
            Log.i(tag, message)
        } catch (e: Throwable) {
            println("INFO: [$tag] $message")
        }
    }
    
    actual fun w(tag: String, message: String) {
        try {
            Log.w(tag, message)
        } catch (e: Throwable) {
            println("WARN: [$tag] $message")
        }
    }
    
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        try {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } catch (e: Throwable) {
            println("ERROR: [$tag] $message ${throwable?.message ?: ""}")
        }
    }
}