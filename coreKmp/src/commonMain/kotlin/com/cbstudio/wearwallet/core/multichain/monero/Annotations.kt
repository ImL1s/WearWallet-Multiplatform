package com.cbstudio.wearwallet.core.multichain.monero

/**
 * KMP 相容的註解定義
 */

/**
 * 標記一個屬性為 volatile
 * 在 JVM 平台上會映射為真正的 @Volatile
 * 在其他平台上作為標記使用
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
expect annotation class Volatile()

/**
 * 同步區塊的跨平台實現
 */
expect inline fun <T> synchronized(lock: Any, block: () -> T): T