package com.cbstudio.wearwallet.firebase

import android.content.Context
import com.cbstudio.wearwallet.BuildConfig
import com.cbstudio.wearwallet.shared.utils.Logger
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Firebase App Check 初始化器
 *
 * 保護 Firebase 後端服務（特別是 Vertex AI Gemini）免受濫用。
 * - Release 模式：使用 Play Integrity 進行硬體級設備認證
 * - Debug 模式：使用 Debug Provider + 預註冊 token
 *
 * 必須在 Firebase.initializeApp() 之後、任何 Firebase 服務呼叫之前調用。
 */
object AppCheckInitializer {

    private const val TAG = "AppCheckInitializer"

    /**
     * 初始化 App Check
     * @param context Application context
     */
    fun initialize(context: Context) {
        try {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()

            if (BuildConfig.DEBUG) {
                // Debug 模式：使用 Debug Provider
                // Debug token 會在 logcat 中輸出，需要在 Firebase Console 註冊
                Logger.d(TAG, "Initializing App Check with DEBUG provider")
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            } else {
                // Release 模式：使用 Play Integrity（硬體級認證）
                Logger.d(TAG, "Initializing App Check with Play Integrity provider")
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }

            // 監聽 token 更新事件（用於除錯）
            firebaseAppCheck.addAppCheckListener { token ->
                Logger.d(TAG, "App Check token refreshed, expires: ${token.expireTimeMillis}")
            }

            Logger.d(TAG, "App Check initialized successfully")

        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize App Check", e)
            // App Check 初始化失敗不應該阻止 app 啟動
            // 但 Firebase 服務在 ENFORCED 模式下會被拒絕
        }
    }
}
