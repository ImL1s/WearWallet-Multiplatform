package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

/**
 * 自適應餘額格式化 — 小餘額顯示更多位數
 *
 * 根據餘額大小自動選擇合適的小數位數：
 * - 1+ → 3 decimals (e.g., 1.234)
 * - 0.001+ → 4 decimals (e.g., 0.0371)
 * - 0.000001+ → 6 decimals (e.g., 0.000064)
 * - < 0.000001 → 8 decimals
 */
fun formatAdaptiveBalance(balance: Double): String {
    if (balance == 0.0) return "0.000"
    val abs = kotlin.math.abs(balance)
    return when {
        abs >= 1.0 -> "%.3f".format(balance)
        abs >= 0.001 -> "%.4f".format(balance)
        abs >= 0.000001 -> "%.6f".format(balance)
        else -> "%.8f".format(balance)
    }
}
