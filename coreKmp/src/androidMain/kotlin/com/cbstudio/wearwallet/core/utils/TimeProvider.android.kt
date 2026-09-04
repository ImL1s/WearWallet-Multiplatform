package com.cbstudio.wearwallet.core.utils

/**
 * Android 平台時間提供器實現
 */

actual fun currentTimeMillis(): Long {
    return System.currentTimeMillis()
}

actual fun currentTimeSeconds(): Long {
    return System.currentTimeMillis() / 1000
}
