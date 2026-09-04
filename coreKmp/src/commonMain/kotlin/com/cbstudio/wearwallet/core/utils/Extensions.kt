package com.cbstudio.wearwallet.core.utils

/**
 * 格式化 Double 數值到指定小數位數
 */
fun Double.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}

/**
 * 格式化字串
 */
fun String.format(vararg args: Any?): String {
    var result = this
    args.forEachIndexed { index, arg ->
        result = result.replace("%${index + 1}", arg.toString())
    }
    return result
}

/**
 * 安全的格式化方法
 */
expect fun String.formatNative(vararg args: Any?): String