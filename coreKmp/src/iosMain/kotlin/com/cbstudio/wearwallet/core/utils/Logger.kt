package com.cbstudio.wearwallet.core.utils

import platform.Foundation.NSLog

/**
 * iOS 平台的 Logger 實現
 */
actual object Logger {
    actual fun d(tag: String, message: String) {
        NSLog("[DEBUG] $tag: $message")
    }
    
    actual fun i(tag: String, message: String) {
        NSLog("[INFO] $tag: $message")
    }
    
    actual fun w(tag: String, message: String) {
        NSLog("[WARN] $tag: $message")
    }
    
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            NSLog("[ERROR] $tag: $message\n${throwable.message}")
        } else {
            NSLog("[ERROR] $tag: $message")
        }
    }
}