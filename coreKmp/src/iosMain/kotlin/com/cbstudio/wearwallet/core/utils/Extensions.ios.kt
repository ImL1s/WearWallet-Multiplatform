package com.cbstudio.wearwallet.core.utils

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

actual fun String.formatNative(vararg args: Any?): String {
    // iOS 使用 NSString 的 stringWithFormat
    return when (args.size) {
        0 -> this
        1 -> NSString.stringWithFormat(this, args[0])
        2 -> NSString.stringWithFormat(this, args[0], args[1])
        3 -> NSString.stringWithFormat(this, args[0], args[1], args[2])
        else -> {
            // 對於更多參數，使用替換方式
            var result = this
            args.forEachIndexed { index, arg ->
                result = result.replace("%${index + 1}", arg.toString())
            }
            result
        }
    }
}