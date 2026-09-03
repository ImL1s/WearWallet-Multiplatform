package com.cbstudio.wearwallet.presentation.screens.ai

internal enum class AiCommandKind {
    BALANCE,
    SEND,
    HISTORY,
    SWITCH,
    SCAN,
    UNKNOWN
}

internal fun classifyAiCommand(input: String): AiCommandKind {
    val t = input.lowercase()
    return when {
        t.contains("餘額") || t.contains("余额") || t.contains("balance") -> AiCommandKind.BALANCE
        t.contains("發送") || t.contains("发送") || t.contains("轉帳") || t.contains("转账") ||
            t.contains("send") -> AiCommandKind.SEND
        t.contains("交易") || t.contains("history") -> AiCommandKind.HISTORY
        t.contains("切換") || t.contains("切换") || t.contains("switch") -> AiCommandKind.SWITCH
        t.contains("掃描") || t.contains("扫描") || t.contains("scan") -> AiCommandKind.SCAN
        else -> AiCommandKind.UNKNOWN
    }
}
