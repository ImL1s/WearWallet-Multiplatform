# ===============================================
# Firebase Vertex AI 專用 ProGuard 規則
# ===============================================
# 用途：解決 Firebase Vertex AI 16.x 與 R8 混淆的相容性問題
# 創建日期：2025-10-28
# 維護者：WearWallet Team
# ===============================================

# 修復 javax.inject 重複定義問題
-dontwarn javax.inject.**
-dontnote javax.inject.**

# ===============================================
# R8 缺失類處理 (2025-10-28 自動生成)
# ===============================================
# Google API 客戶端（可選依賴）
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport

# Java 平台特定類（Android 不支援）
-dontwarn com.sun.activation.registries.LogSupport
-dontwarn com.sun.activation.registries.MailcapFile
-dontwarn com.sun.activation.registries.MimeTypeFile
-dontwarn java.awt.datatransfer.DataFlavor
-dontwarn java.awt.datatransfer.Transferable
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# 可選壓縮庫
-dontwarn org.brotli.dec.BrotliInputStream

# Kerberos/GSSAPI（Android 不支援）
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# Joda Time（可選依賴）
-dontwarn org.joda.time.Instant

# 保留 Firebase Vertex AI 核心類別
-keep class com.google.firebase.vertexai.** { *; }
-keepclassmembers class com.google.firebase.vertexai.** { *; }

# 保留 Generative AI 客戶端
-keep class com.google.ai.client.generativeai.** { *; }
-keepclassmembers class com.google.ai.client.generativeai.** { *; }

# 保留 Vertex AI 類型
-keep class com.google.firebase.vertexai.type.** { *; }
-keep interface com.google.firebase.vertexai.type.** { *; }

# 保留 Generative AI 類型
-keep class com.google.ai.client.generativeai.type.** { *; }
-keep interface com.google.ai.client.generativeai.type.** { *; }

# 保留 Content 相關類別（用於反序列化）
-keepclassmembers class com.google.firebase.vertexai.type.Content {
    <init>(...);
    <fields>;
}

# 保留 GenerateContentResponse 相關類別
-keepclassmembers class com.google.firebase.vertexai.type.GenerateContentResponse {
    <init>(...);
    <fields>;
}

# 保留所有帶 @SerializedName 註解的字段
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保留 Kotlin 序列化相關
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# 保留 Retrofit 相關（如果 Firebase AI 內部使用）
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# 保留 enum 類別（Vertex AI 可能使用）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable 實現（Android 組件通訊）
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# 保留 Coroutines（Firebase AI 使用 Kotlin Coroutines）
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ===============================================
# 除錯配置（生產環境應移除）
# ===============================================
# 取消註解以下行可在混淆時保留行號資訊
# -keepattributes SourceFile,LineNumberTable

# 取消註解以下行可在日誌中看到混淆前的類別名稱
# -printmapping build/outputs/mapping/release/mapping.txt
# -verbose
