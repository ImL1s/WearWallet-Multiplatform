package com.cbstudio.wearwallet.core.blockchain.crypto

/**
 * 加密模組安全日誌系統
 *
 * 設計原則：
 * - 生產環境僅記錄錯誤碼，避免洩露敏感信息
 * - 開發環境可顯示詳細上下文信息
 * - 所有日誌都帶有統一標籤便於過濾
 *
 * 日誌級別：
 * - ERROR: 嚴重錯誤，影響功能正常運行
 * - WARN: 警告信息，不影響功能但需要注意
 * - INFO: 一般信息，記錄關鍵操作
 * - DEBUG: 調試信息，僅開發環境輸出
 */
internal object CryptoLogger {
    private const val TAG = "WearWallet.Crypto"

    /**
     * 判斷是否為調試模式
     * TODO: 整合 BuildConfig 或其他配置系統
     */
    private val isDebug: Boolean = true  // 暫時設為 true，後續整合 BuildConfig

    /**
     * 記錄錯誤
     * @param code 錯誤碼
     * @param context 可選的上下文信息（僅開發環境輸出）
     */
    fun error(code: CryptoErrorCode, context: String? = null) {
        if (isDebug && context != null) {
            println("[$TAG] ERROR $code: $context")
        } else {
            println("[$TAG] ERROR $code")
        }
        // TODO: 生產環境發送到遠程日誌系統（如 Firebase Crashlytics）
    }

    /**
     * 記錄警告
     * @param code 警告碼
     * @param context 可選的上下文信息（僅開發環境輸出）
     */
    fun warn(code: CryptoWarningCode, context: String? = null) {
        if (isDebug) {
            val message = context?.let { ": $it" } ?: ""
            println("[$TAG] WARN $code$message")
        }
    }

    /**
     * 記錄一般信息
     * @param message 信息內容（僅開發環境輸出）
     */
    fun info(message: String) {
        if (isDebug) {
            println("[$TAG] INFO: $message")
        }
    }

    /**
     * 記錄調試信息
     * @param message 調試信息（僅開發環境輸出）
     */
    fun debug(message: String) {
        if (isDebug) {
            println("[$TAG] DEBUG: $message")
        }
    }
}

/**
 * 加密操作錯誤碼枚舉
 *
 * 錯誤碼規則：
 * - E001-E099: 輸入驗證錯誤
 * - E100-E199: 簽名操作錯誤
 * - E200-E299: 驗證操作錯誤
 * - E300-E399: 系統/框架錯誤
 * - E900-E999: 通用/未知錯誤
 */
enum class CryptoErrorCode(val code: String, val description: String) {
    // 輸入驗證錯誤 (E001-E099)
    INVALID_KEY_LENGTH("E001", "私鑰或公鑰長度不正確"),
    INVALID_MESSAGE_FORMAT("E002", "消息格式不正確"),
    INVALID_SIGNATURE_FORMAT("E003", "簽名格式不正確"),
    INVALID_PRIVATE_KEY("E004", "私鑰數據無效"),
    INVALID_PUBLIC_KEY("E005", "公鑰數據無效"),
    INVALID_BLOCKHASH("E006", "區塊哈希無效"),

    // 簽名錯誤 (E100-E199)
    ED25519_SIGN_FAILED("E101", "Ed25519 簽名失敗"),
    ECDSA_SIGN_FAILED("E102", "ECDSA 簽名失敗"),
    HASH_FAILED("E103", "消息哈希計算失敗"),
    SIGNATURE_FORMAT_FAILED("E104", "簽名格式化失敗"),
    SOLANA_TX_SIGN_FAILED("E105", "Solana 交易簽名失敗"),
    ETHEREUM_TX_SIGN_FAILED("E106", "Ethereum 交易簽名失敗"),

    // 驗證錯誤 (E200-E299)
    SIGNATURE_VERIFICATION_FAILED("E201", "簽名驗證失敗"),
    PUBLIC_KEY_DERIVE_FAILED("E202", "公鑰派生失敗"),
    RECOVERY_ID_CALCULATION_FAILED("E203", "恢復 ID 計算失敗"),

    // 系統錯誤 (E300-E399)
    SECURITY_FRAMEWORK_ERROR("E301", "iOS Security Framework 錯誤"),
    SECKEY_CREATE_FAILED("E302", "SecKey 創建失敗"),
    SECKEY_OPERATION_FAILED("E303", "SecKey 操作失敗"),

    // 編碼錯誤 (E400-E499)
    BASE58_ENCODE_FAILED("E401", "Base58 編碼失敗"),
    BASE58_DECODE_FAILED("E402", "Base58 解碼失敗"),
    HEX_ENCODE_FAILED("E403", "十六進制編碼失敗"),
    HEX_DECODE_FAILED("E404", "十六進制解碼失敗"),
    DER_PARSE_FAILED("E405", "DER 格式解析失敗"),

    // 交易構建錯誤 (E500-E599)
    TX_BUILD_FAILED("E501", "交易構建失敗"),
    TX_HASH_GENERATION_FAILED("E502", "交易哈希生成失敗"),
    INSTRUCTION_COMPILE_FAILED("E503", "指令編譯失敗"),

    // 通用錯誤 (E900-E999)
    UNKNOWN_ERROR("E999", "未知錯誤");

    override fun toString() = code
}

/**
 * 加密操作警告碼枚舉
 */
enum class CryptoWarningCode(val code: String, val description: String) {
    DEPRECATED_API("W001", "使用已棄用的 API"),
    PERFORMANCE_ISSUE("W002", "性能問題"),
    FALLBACK_TO_STUB("W003", "降級到 stub 實現");

    override fun toString() = code
}