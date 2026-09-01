package com.cbstudio.wearwallet.core.utils

import platform.Foundation.NSLog

/**
 * watchOS 平台的日誌實現
 * 使用 NSLog 輸出到系統日誌
 */
actual object Logger {
    actual fun d(tag: String, message: String) {
        NSLog("[$tag] DEBUG: $message")
    }
    
    actual fun i(tag: String, message: String) {
        NSLog("[$tag] INFO: $message")
    }
    
    actual fun w(tag: String, message: String) {
        NSLog("[$tag] WARN: $message")
    }
    
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        NSLog("[$tag] ERROR: $message")
        throwable?.let {
            NSLog("[$tag] ERROR: ${it.message}")
            NSLog("[$tag] ERROR: ${it.stackTraceToString()}")
        }
    }
}