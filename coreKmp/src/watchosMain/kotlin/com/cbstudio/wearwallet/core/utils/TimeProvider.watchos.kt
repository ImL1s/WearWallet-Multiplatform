package com.cbstudio.wearwallet.core.utils

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * watchOS 平台時間提供器實現
 */

actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}

actual fun currentTimeSeconds(): Long {
    return NSDate().timeIntervalSince1970.toLong()
}
