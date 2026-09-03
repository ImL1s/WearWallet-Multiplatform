package com.cbstudio.wearwallet.core.multichain.monero

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * iOS 平台的註解實現
 */

/**
 * 在 iOS 平台上作為標記註解
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
actual annotation class Volatile

// 用於同步的全局鎖映射
@PublishedApi
internal val locks = mutableMapOf<Any, ReentrantLock>()

/**
 * 在 iOS 平台上使用 ReentrantLock 實現同步
 */
actual inline fun <T> synchronized(lock: Any, block: () -> T): T {
    val actualLock = locks.getOrPut(lock) { ReentrantLock() }
    return actualLock.withLock(block)
}