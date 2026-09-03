package com.cbstudio.wearwallet.core.security

/**
 * 4-state key presence representation for KeyVault / Keystore.
 * Distinguishes between:
 * - Present: Key exists in the secure storage and is ready for use (KeyStore alias, ciphertext, IV, tag, metadata all present and consistent).
 * - Absent: Key definitely does not exist in the secure storage (safe for tombstone / deletion completion).
 * - Partial: Inconsistent state (e.g. KeyStore alias present but ciphertext absent, or vice versa).
 * - Unavailable: Hardware error, keystore unavailable, or transient failure. The key status is indeterminate
 *   and MUST NOT be assumed absent. Deletion/reconciliation operations MUST fail closed and preserve database state.
 */
sealed interface KeyPresence {
    data object Present : KeyPresence
    data object Absent : KeyPresence
    data class Partial(val details: String) : KeyPresence
    data class Unavailable(val cause: Throwable) : KeyPresence
}
