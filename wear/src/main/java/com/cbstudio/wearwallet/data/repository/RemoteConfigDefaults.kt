package com.cbstudio.wearwallet.data.repository

/**
 * Remote Configuration Default Values
 * Holds default configuration values for the application
 * 
 * TODO: This is a temporary stub implementation
 * The actual implementation should be restored after KMP migration
 */
data class RemoteConfigDefaults(
    val isAIAssistantEnabled: Boolean = true,
    val isWearSyncEnabled: Boolean = true,
    val maxTransactionRetries: Int = 3,
    val syncIntervalMinutes: Long = 15,
    val isDebugModeEnabled: Boolean = false,
    val apiTimeout: Long = 30000,
    val maxCachedTransactions: Int = 100,
    val isAdvancedFeaturesEnabled: Boolean = false
) {
    /**
     * Convert to map for easy usage
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "is_ai_assistant_enabled" to isAIAssistantEnabled,
            "is_wear_sync_enabled" to isWearSyncEnabled,
            "max_transaction_retries" to maxTransactionRetries,
            "sync_interval_minutes" to syncIntervalMinutes,
            "is_debug_mode_enabled" to isDebugModeEnabled,
            "api_timeout" to apiTimeout,
            "max_cached_transactions" to maxCachedTransactions,
            "is_advanced_features_enabled" to isAdvancedFeaturesEnabled
        )
    }
}
