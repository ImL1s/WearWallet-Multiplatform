/**
 * Monero-Java to Monerujo JNI Bridge
 * 
 * 這個橋接層將 monero-java 的 JNI 調用映射到 Monerujo 的實作。
 * 透過動態註冊 JNI 方法，實現兩個不同 JNI 介面的兼容。
 */

#include <jni.h>
#include <string>
#include <android/log.h>
#include <dlfcn.h>

#define LOG_TAG "MoneroJavaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Monerujo JNI 函數指針類型定義
typedef jlong (*CreateWalletFunc)(JNIEnv*, jobject, jstring, jstring, jstring, jlong);
typedef jstring (*GetAddressFunc)(JNIEnv*, jobject, jlong);
typedef jlong (*GetBalanceFunc)(JNIEnv*, jobject, jlong);
typedef jboolean (*StartRefreshFunc)(JNIEnv*, jobject, jlong);
typedef jboolean (*CloseWalletFunc)(JNIEnv*, jobject, jlong);

// 全局函數指針
static void* monerujo_lib = nullptr;
static CreateWalletFunc monerujo_createWallet = nullptr;
static GetAddressFunc monerujo_getAddress = nullptr;
static GetBalanceFunc monerujo_getBalance = nullptr;
static StartRefreshFunc monerujo_startRefresh = nullptr;
static CloseWalletFunc monerujo_closeWallet = nullptr;

// 載入 Monerujo 庫並獲取函數指針
static bool loadMonerujoLibrary() {
    if (monerujo_lib != nullptr) {
        return true; // 已載入
    }
    
    // 嘗試載入 libmonerujo.so
    monerujo_lib = dlopen("libmonerujo.so", RTLD_NOW);
    if (monerujo_lib == nullptr) {
        LOGE("無法載入 libmonerujo.so: %s", dlerror());
        return false;
    }
    
    // 獲取函數指針
    monerujo_createWallet = (CreateWalletFunc)dlsym(monerujo_lib, 
        "Java_com_m2049r_xmrwallet_model_WalletManager_recoveryWalletJ");
    monerujo_getAddress = (GetAddressFunc)dlsym(monerujo_lib,
        "Java_com_m2049r_xmrwallet_model_Wallet_getAddressJ");
    monerujo_getBalance = (GetBalanceFunc)dlsym(monerujo_lib,
        "Java_com_m2049r_xmrwallet_model_Wallet_getBalance");
    monerujo_startRefresh = (StartRefreshFunc)dlsym(monerujo_lib,
        "Java_com_m2049r_xmrwallet_model_Wallet_startRefresh");
    monerujo_closeWallet = (CloseWalletFunc)dlsym(monerujo_lib,
        "Java_com_m2049r_xmrwallet_model_Wallet_close");
    
    if (monerujo_createWallet == nullptr) {
        LOGE("無法找到 Monerujo 函數");
        return false;
    }
    
    LOGI("成功載入 Monerujo 函數");
    return true;
}

// ============================================
// Monero-Java 兼容 JNI 方法實作
// ============================================

extern "C" {

/**
 * 初始化 Monero 環境
 */
JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MoneroJavaCompatibilityBridge_initializeMonero(
    JNIEnv *env, jclass clazz, jstring dataDir, jboolean isTestnet) {
    
    if (!loadMonerujoLibrary()) {
        return JNI_FALSE;
    }
    
    // 調用 Monerujo 的初始化方法
    // 注意：實際的初始化可能需要不同的函數
    LOGI("初始化 Monero 環境");
    return JNI_TRUE;
}

/**
 * 創建錢包（monero-java 兼容方法）
 * 模擬 monero.wallet.MoneroWalletFull.createWalletJni
 */
JNIEXPORT jlong JNICALL
Java_monero_wallet_MoneroWalletFull_createWalletJni(
    JNIEnv *env, jclass clazz, jstring config) {
    
    LOGI("createWalletJni 被調用");
    
    if (!loadMonerujoLibrary()) {
        LOGE("Monerujo 庫未載入");
        return 0;
    }
    
    // 解析 JSON 配置
    const char* configStr = env->GetStringUTFChars(config, nullptr);
    LOGI("配置: %s", configStr);
    
    // TODO: 解析 JSON 獲取參數
    // 這裡簡化處理，實際需要 JSON 解析庫
    
    // 調用 Monerujo 的創建錢包方法
    if (monerujo_createWallet != nullptr) {
        // 創建假的 jobject（實際需要創建正確的 Java 對象）
        jobject walletManager = nullptr; // 需要創建 WalletManager 實例
        jstring path = env->NewStringUTF("");
        jstring password = env->NewStringUTF("");
        jstring mnemonic = env->NewStringUTF(""); // 從 config 中提取
        jlong restoreHeight = 0;
        
        // jlong handle = monerujo_createWallet(env, walletManager, path, password, mnemonic, restoreHeight);
        
        env->DeleteLocalRef(path);
        env->DeleteLocalRef(password);
        env->DeleteLocalRef(mnemonic);
        
        // return handle;
    }
    
    env->ReleaseStringUTFChars(config, configStr);
    return 0; // 暫時返回 0
}

/**
 * 獲取主地址
 */
JNIEXPORT jstring JNICALL
Java_monero_wallet_MoneroWalletFull_getPrimaryAddressJni(
    JNIEnv *env, jclass clazz, jlong handle) {
    
    if (monerujo_getAddress != nullptr && handle != 0) {
        jobject wallet = nullptr; // 需要從 handle 恢復 wallet 對象
        // return monerujo_getAddress(env, wallet, handle);
    }
    
    return env->NewStringUTF("unknown_address");
}

/**
 * 獲取餘額
 */
JNIEXPORT jlong JNICALL
Java_monero_wallet_MoneroWalletFull_getBalanceJni(
    JNIEnv *env, jclass clazz, jlong handle) {
    
    if (monerujo_getBalance != nullptr && handle != 0) {
        jobject wallet = nullptr; // 需要從 handle 恢復 wallet 對象
        // return monerujo_getBalance(env, wallet, handle);
    }
    
    return 0;
}

/**
 * 同步錢包
 */
JNIEXPORT jboolean JNICALL
Java_monero_wallet_MoneroWalletFull_syncJni(
    JNIEnv *env, jclass clazz, jlong handle) {
    
    if (monerujo_startRefresh != nullptr && handle != 0) {
        jobject wallet = nullptr; // 需要從 handle 恢復 wallet 對象
        // return monerujo_startRefresh(env, wallet, handle);
    }
    
    return JNI_FALSE;
}

/**
 * 關閉錢包
 */
JNIEXPORT jboolean JNICALL
Java_monero_wallet_MoneroWalletFull_closeWalletJni(
    JNIEnv *env, jclass clazz, jlong handle) {
    
    if (monerujo_closeWallet != nullptr && handle != 0) {
        jobject wallet = nullptr; // 需要從 handle 恢復 wallet 對象
        // return monerujo_closeWallet(env, wallet, handle);
    }
    
    return JNI_TRUE;
}

/**
 * 註冊 JNI 方法映射
 */
JNIEXPORT void JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MoneroJavaCompatibilityBridge_registerJNIMethods(
    JNIEnv *env, jclass clazz) {
    
    LOGI("註冊 JNI 方法映射");
    
    // 動態註冊方法
    JNINativeMethod methods[] = {
        {"createWalletJni", "(Ljava/lang/String;)J", 
            (void*)Java_monero_wallet_MoneroWalletFull_createWalletJni},
        {"getPrimaryAddressJni", "(J)Ljava/lang/String;", 
            (void*)Java_monero_wallet_MoneroWalletFull_getPrimaryAddressJni},
        {"getBalanceJni", "(J)J", 
            (void*)Java_monero_wallet_MoneroWalletFull_getBalanceJni},
        {"syncJni", "(J)Z", 
            (void*)Java_monero_wallet_MoneroWalletFull_syncJni},
        {"closeWalletJni", "(J)Z", 
            (void*)Java_monero_wallet_MoneroWalletFull_closeWalletJni}
    };
    
    // 註冊到 MoneroWalletFull 類
    jclass walletClass = env->FindClass("monero/wallet/MoneroWalletFull");
    if (walletClass != nullptr) {
        env->RegisterNatives(walletClass, methods, sizeof(methods) / sizeof(methods[0]));
        LOGI("成功註冊 %lu 個方法", sizeof(methods) / sizeof(methods[0]));
    } else {
        LOGE("無法找到 MoneroWalletFull 類");
    }
}

/**
 * JNI 載入時自動調用
 */
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    LOGI("JNI_OnLoad: monero_java_bridge");
    
    // 自動註冊方法
    jclass bridgeClass = env->FindClass(
        "com/cbstudio/wearwallet/core/multichain/monero/MoneroJavaCompatibilityBridge");
    if (bridgeClass != nullptr) {
        Java_com_cbstudio_wearwallet_core_multichain_monero_MoneroJavaCompatibilityBridge_registerJNIMethods(
            env, bridgeClass);
    }
    
    return JNI_VERSION_1_6;
}

} // extern "C"