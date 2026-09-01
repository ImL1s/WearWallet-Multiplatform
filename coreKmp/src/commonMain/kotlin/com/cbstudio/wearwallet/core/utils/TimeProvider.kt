package com.cbstudio.wearwallet.core.utils

import kotlinx.datetime.Instant

/**
 * 跨平台時間提供器
 *
 * 提供統一的時間 API，解決 KMP 中無法直接使用 System.currentTimeMillis()、
 * Clock.System.now() 等平台特定時間函數的問題。
 *
 * 使用方式：
 * ```kotlin
 * val now = currentTimeMillis()
 * val seconds = currentTimeSeconds()
 * val instant = currentInstant()
 * ```
 */

/**
 * 獲取當前時間的毫秒時間戳（自 1970-01-01 00:00:00 UTC）
 *
 * @return 當前時間的毫秒數
 */
expect fun currentTimeMillis(): Long

/**
 * 獲取當前時間的秒時間戳（自 1970-01-01 00:00:00 UTC）
 *
 * @return 當前時間的秒數
 */
expect fun currentTimeSeconds(): Long

/**
 * 獲取當前時間的 Instant 對象
 *
 * @return 當前時間的 Instant
 */
fun currentInstant(): Instant {
    return Instant.fromEpochMilliseconds(currentTimeMillis())
}
