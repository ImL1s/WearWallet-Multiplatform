# MultiDex optimization rules for WearWallet
# Keeps essential classes in the main DEX for faster app startup

# === 應用核心類別 (必須保留在主 DEX) ===
-keep class com.cbstudio.wearwallet.WearWalletApplication
-keep class com.cbstudio.wearwallet.MainActivity
-keep class com.cbstudio.wearwallet.presentation.navigation.**
-keep class com.cbstudio.wearwallet.presentation.screens.main.**

# === Hilt/DI 核心類別 ===
-keep class dagger.hilt.android.HiltAndroidApp
-keep class dagger.hilt.components.SingletonComponent
-keep class com.cbstudio.wearwallet.di.ApplicationModule
-keep class com.cbstudio.wearwallet.di.DatabaseModule

# === Compose 核心類別 ===
-keep class androidx.compose.runtime.Composer
-keep class androidx.compose.runtime.ComposerKt
-keep class androidx.compose.ui.platform.AndroidComposition*
-keep class androidx.compose.ui.platform.ComposeView

# === Android 框架核心類別 ===
-keep class androidx.multidex.**
-keep class androidx.lifecycle.ProcessLifecycleOwner
-keep class androidx.work.WorkManager
-keep class androidx.room.Room

# === WearOS 核心類別 ===
-keep class androidx.wear.compose.material.**
-keep class androidx.wear.compose.foundation.**
-keep class com.google.android.wearable.intent.RemoteIntent

# === Firebase 核心類別 ===
-keep class com.google.firebase.FirebaseApp
-keep class com.google.firebase.analytics.FirebaseAnalytics
-keep class com.google.firebase.crashlytics.FirebaseCrashlytics

# === Firebase Vertex AI 核心類別 (ULTRATHINK Phase 8.4 修復) ===
-keep class com.google.firebase.vertexai.GenerativeModel
-keep class com.google.firebase.vertexai.type.**
-keep class io.ktor.client.HttpClient
-keep class io.ktor.client.plugins.HttpTimeout

# === 加密貨幣核心類別 ===
-keep class com.cbstudio.wearwallet.shared.domain.model.Wallet
-keep class com.cbstudio.wearwallet.shared.domain.model.NetworkConfig
-keep class com.cbstudio.wearwallet.shared.domain.repository.WalletRepository