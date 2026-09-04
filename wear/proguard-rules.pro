# ProGuard rules optimized for WearWallet KMP project (2025 best practices)
# Enhanced R8/ProGuard configuration for maximum optimization and performance

# === 基本配置 ===
# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 優化配置
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# 移除不必要的代碼
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 移除調試信息
-assumenosideeffects class timber.log.Timber* {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** tag(...);
}

# WearOS specific
-keep class androidx.wear.** { *; }
-keep class com.google.android.wearable.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keepclassmembers class * {
    @dagger.hilt.* <fields>;
    @javax.inject.* <fields>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ULTRATHINK 清理：移除平台特定區塊鏈依賴規則
# KMP 架構統一管理所有區塊鏈功能

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson/Moshi
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
    @com.google.gson.annotations.* <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract ** *Dao();
}

# SQLCipher (CRITICAL: Used for encrypted database in KMP module)
# Keep all SQLCipher classes and their native-accessed fields
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# Keep native-accessed fields for JNI (prevents NoSuchFieldError)
-keepclassmembers class net.sqlcipher.database.SQLiteDatabase {
    long mNativeHandle;
    <fields>;
}
-keepclassmembers class net.sqlcipher.database.* {
    long mNativeHandle;
    <fields>;
}

# SQLDelight Android Driver (uses SQLCipher)
-keep class app.cash.sqldelight.** { *; }
-keep class app.cash.sqldelight.driver.android.** { *; }
-dontwarn app.cash.sqldelight.**

# Data models
-keep class com.cbstudio.wearwallet.shared.domain.model.** { *; }
-keep class com.cbstudio.wearwallet.shared.data.entity.** { *; }
-keep class com.cbstudio.wearwallet.shared.models.** { *; }

# Complications
-keep class com.cbstudio.wearwallet.presentation.complication.** { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep custom exceptions
-keep class * extends java.lang.Exception

# Enum
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Coin enum fields to prevent NoSuchFieldError
-keep class com.cbstudio.wearwallet.domain.model.Coin {
    public static final com.cbstudio.wearwallet.domain.model.Coin *;
    public static com.cbstudio.wearwallet.domain.model.Coin[] values();
    public static com.cbstudio.wearwallet.domain.model.Coin valueOf(java.lang.String);
    <fields>;
    <methods>;
}

# Keep all enum values
-keepclassmembers enum com.cbstudio.wearwallet.domain.model.Coin {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Ktor client (coreKmp 依賴 — 必須保留)
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class io.ktor.client.plugins.** { *; }
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.serialization.** { *; }

# Firebase AI 基本保留規則
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.vertexai.** { *; }
-dontwarn com.google.firebase.ai.**
-dontwarn com.google.firebase.vertexai.**

# === Monero JNI Configuration ===
# Keep all Monero JNI wrapper classes and methods
-keep class com.cbstudio.wearwallet.core.multichain.monero.MonerujoJNIWrapper { *; }
-keep class com.cbstudio.wearwallet.core.multichain.monero.MemoryWalletManager { *; }
-keep class com.cbstudio.wearwallet.core.multichain.monero.WalletMode { *; }
-keep class com.cbstudio.wearwallet.core.multichain.monero.AndroidTestStorageHelper { *; }

# Keep all native methods for JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Monerujo JNI classes
-keep class com.m2049r.xmrwallet.model.** { *; }
-keep class com.m2049r.xmrwallet.ledger.** { *; }
-keep class com.m2049r.xmrwallet.util.** { *; }

# Keep debug test Activity
-keep class com.cbstudio.wearwallet.presentation.debug.MoneroDeviceTestActivity { *; }

# Keep JNI-related annotations
-keepattributes *Annotation*
-keep @interface com.cbstudio.wearwallet.core.multichain.monero.* { *; }

# Prevent stripping of native libraries
-keep class * {
    @com.cbstudio.wearwallet.core.multichain.monero.* <fields>;
    @com.cbstudio.wearwallet.core.multichain.monero.* <methods>;
}