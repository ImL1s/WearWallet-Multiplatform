package com.cbstudio.wearwallet.core.multichain.monero

/**
 * Android 平台的註解實現
 */

/**
 * 在 Android/JVM 平台上映射到真正的 @Volatile
 */
actual typealias Volatile = kotlin.jvm.Volatile

/**
 * 在 Android/JVM 平台上使用內建的 synchronized
 */
actual inline fun <T> synchronized(lock: Any, block: () -> T): T {
    return kotlin.synchronized(lock, block)
}