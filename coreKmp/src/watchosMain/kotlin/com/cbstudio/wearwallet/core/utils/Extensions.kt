package com.cbstudio.wearwallet.core.utils

/**
 * watchOS 平台的工具擴展函數實現（簡化版本）
 */

/**
 * 安全的格式化方法
 */
actual fun String.formatNative(vararg args: Any?): String {
    return try {
        // watchOS 簡化實現，直接替換占位符
        var result = this
        args.forEachIndexed { index, arg ->
            result = result.replace("%${index + 1}", arg.toString())
            result = result.replace("%s", arg.toString())
            result = result.replace("%d", arg.toString())
        }
        result
    } catch (e: Exception) {
        this
    }
}

/**
 * 格式化貨幣
 */
fun Double.formatCurrency(currencyCode: String = "USD"): String {
    return try {
        "$${"%.2f".format(this)}"
    } catch (e: Exception) {
        this.toString()
    }
}

/**
 * 格式化百分比
 */
fun Double.formatPercentage(decimalPlaces: Int = 2): String {
    return try {
        "${"%.${decimalPlaces}f".format(this * 100)}%"
    } catch (e: Exception) {
        "${this * 100}%"
    }
}

/**
 * 安全的數學運算
 */
fun Double.safeDivide(divisor: Double, defaultValue: Double = 0.0): Double {
    return if (divisor != 0.0 && !divisor.isNaN() && !divisor.isInfinite()) {
        this / divisor
    } else {
        defaultValue
    }
}

/**
 * 格式化文件大小
 */
fun Long.formatFileSize(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = this.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "${"%.2f".format(size)} ${units[unitIndex]}"
}

/**
 * 時間格式化
 */
fun Long.formatDuration(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "${days}天${(hours % 24)}小時"
        hours > 0 -> "${hours}小時${(minutes % 60)}分鐘"
        minutes > 0 -> "${minutes}分鐘${(seconds % 60)}秒"
        else -> "${seconds}秒"
    }
}