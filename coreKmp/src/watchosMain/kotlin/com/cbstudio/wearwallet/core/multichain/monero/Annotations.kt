package com.cbstudio.wearwallet.core.multichain.monero

/**
 * watchOS 平台的註解實現
 */

/**
 * Volatile 註解 - watchOS Native 平台實現
 */
actual annotation class Volatile

/**
 * synchronized 函數 - watchOS Native 平台實現
 * 在 Native 平台上，我們簡化同步機制
 */
actual inline fun <T> synchronized(lock: Any, block: () -> T): T {
    // 在 watchOS 上簡化同步邏輯
    // 由於 watchOS 單線程特性，直接執行
    return block()
}