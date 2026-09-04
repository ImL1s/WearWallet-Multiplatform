package io.github.iml1s.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android implementation of PlatformContext
 */
actual class PlatformContext(val context: Context)

/**
 * Android implementation of SecureStorage using EncryptedSharedPreferences.
 */
actual class PlatformSecureStorage actual constructor(platformContext: PlatformContext) : SecureStorage {

    private val context = platformContext.context

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_wallet_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun put(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    override suspend fun get(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    override suspend fun delete(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    override suspend fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
