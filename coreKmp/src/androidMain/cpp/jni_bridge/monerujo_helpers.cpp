#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#define LOG_TAG "MonerujoHelpers"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Monerujo JNI Helper Functions
 * 
 * 這些函數提供了 Java 物件與 C++ 指標之間的橋接功能
 * 基於 Monerujo 專案的實作
 * 
 * 參考：https://github.com/m2049r/xmrwallet/blob/master/app/src/main/cpp/monerujo.h
 */

// Forward declarations for templates (must be outside extern "C")
template<typename T>
T* getHandle(JNIEnv* env, jobject obj);

template<typename T>
void setHandle(JNIEnv* env, jobject obj, T* ptr);

extern "C" {

/**
 * 獲取 handle 欄位的 field ID
 * @param env JNI 環境
 * @param clazz Java 類別
 * @param fieldName 欄位名稱（預設為 "handle"）
 * @return field ID
 */
jfieldID getHandleField(JNIEnv* env, jclass clazz, const char* fieldName = "handle") {
    return env->GetFieldID(clazz, fieldName, "J");
}

/**
 * 設定 Java 物件的 handle 欄位（使用 long 值）
 * @param env JNI 環境
 * @param obj Java 物件
 * @param value long 值
 */
void setHandleFromLong(JNIEnv* env, jobject obj, jlong value) {
    env->SetLongField(obj, getHandleField(env, env->GetObjectClass(obj)), value);
}

/**
 * 將 Java 字串轉換為 C++ 字串
 * @param env JNI 環境
 * @param javaString Java 字串
 * @return C++ 字串
 */
std::string jstringToString(JNIEnv* env, jstring javaString) {
    if (!javaString) return "";
    
    const char* chars = env->GetStringUTFChars(javaString, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(javaString, chars);
    return result;
}

/**
 * 將 C++ 字串轉換為 Java 字串
 * @param env JNI 環境
 * @param cppString C++ 字串
 * @return Java 字串
 */
jstring stringToJstring(JNIEnv* env, const std::string& cppString) {
    return env->NewStringUTF(cppString.c_str());
}

/**
 * 將 Java 字串陣列轉換為 C++ 字串向量
 * @param env JNI 環境
 * @param javaStringArray Java 字串陣列
 * @return C++ 字串向量
 */
std::vector<std::string> jstringArrayToStringVector(JNIEnv* env, jobjectArray javaStringArray) {
    std::vector<std::string> result;
    
    if (!javaStringArray) return result;
    
    jsize length = env->GetArrayLength(javaStringArray);
    result.reserve(length);
    
    for (jsize i = 0; i < length; i++) {
        jstring javaString = static_cast<jstring>(env->GetObjectArrayElement(javaStringArray, i));
        result.push_back(jstringToString(env, javaString));
        env->DeleteLocalRef(javaString);
    }
    
    return result;
}

/**
 * 將 C++ 字串向量轉換為 Java 字串陣列
 * @param env JNI 環境
 * @param stringVector C++ 字串向量
 * @return Java 字串陣列
 */
jobjectArray stringVectorToJstringArray(JNIEnv* env, const std::vector<std::string>& stringVector) {
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(stringVector.size(), stringClass, nullptr);
    
    for (size_t i = 0; i < stringVector.size(); i++) {
        jstring javaString = stringToJstring(env, stringVector[i]);
        env->SetObjectArrayElement(result, i, javaString);
        env->DeleteLocalRef(javaString);
    }
    
    env->DeleteLocalRef(stringClass);
    return result;
}

/**
 * 檢查 JNI 是否有異常發生
 * @param env JNI 環境
 * @return 是否有異常
 */
bool checkJNIException(JNIEnv* env) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return false;
}

/**
 * 安全地取得類別方法 ID
 * @param env JNI 環境
 * @param clazz 類別
 * @param methodName 方法名稱
 * @param signature 方法簽名
 * @return 方法 ID，如果失敗則為 nullptr
 */
jmethodID getSafeMethodID(JNIEnv* env, jclass clazz, const char* methodName, const char* signature) {
    jmethodID methodID = env->GetMethodID(clazz, methodName, signature);
    if (checkJNIException(env)) {
        LOGE("Failed to get method ID for %s with signature %s", methodName, signature);
        return nullptr;
    }
    return methodID;
}

/**
 * 安全地取得類別欄位 ID
 * @param env JNI 環境
 * @param clazz 類別
 * @param fieldName 欄位名稱
 * @param signature 欄位簽名
 * @return 欄位 ID，如果失敗則為 nullptr
 */
jfieldID getSafeFieldID(JNIEnv* env, jclass clazz, const char* fieldName, const char* signature) {
    jfieldID fieldID = env->GetFieldID(clazz, fieldName, signature);
    if (checkJNIException(env)) {
        LOGE("Failed to get field ID for %s with signature %s", fieldName, signature);
        return nullptr;
    }
    return fieldID;
}

/**
 * 記錄 JNI 調用的除錯資訊
 * @param functionName 函數名稱
 * @param params 參數描述
 */
void logJNICall(const char* functionName, const char* params = "") {
    LOGD("JNI Call: %s(%s)", functionName, params);
}

/**
 * 記錄 JNI 錯誤
 * @param functionName 函數名稱
 * @param error 錯誤訊息
 */
void logJNIError(const char* functionName, const char* error) {
    LOGE("JNI Error in %s: %s", functionName, error);
}

} // extern "C"

// Template implementations (must be outside extern "C")
/**
 * 從 Java 物件獲取 native 指標
 * @param env JNI 環境
 * @param obj Java 物件
 * @return native 指標
 */
template<typename T>
T* getHandle(JNIEnv* env, jobject obj) {
    jlong handle = env->GetLongField(obj, getHandleField(env, env->GetObjectClass(obj)));
    return reinterpret_cast<T*>(handle);
}

/**
 * 設定 Java 物件的 handle 欄位（使用 C++ 指標）
 * @param env JNI 環境
 * @param obj Java 物件
 * @param ptr C++ 指標
 */
template<typename T>
void setHandle(JNIEnv* env, jobject obj, T* ptr) {
    setHandleFromLong(env, obj, reinterpret_cast<jlong>(ptr));
}