package io.github.iml1s.caip.core

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * watchOS implementation of currentTimeMillis
 */
internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
