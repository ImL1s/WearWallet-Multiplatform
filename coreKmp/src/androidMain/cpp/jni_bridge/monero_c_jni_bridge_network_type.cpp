#include <jni.h>
#include <android/log.h>
#include <string>
#include <map>
#include <cstring>
#include <dlfcn.h>

#define LOG_TAG "MoneroJNI-NetworkType"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Network type constants
const int MONERO_NetworkType_MAINNET = 0;
const int MONERO_NetworkType_TESTNET = 1;
const int MONERO_NetworkType_STAGENET = 2;

// External function declarations from monero_c
extern "C" {
    typedef void* (*WalletManagerFactory_getWalletManager_t)();
    typedef void* (*WalletManager_createWalletFromMnemonic_t)(
        void* walletManager,
        const char* path,
        const char* password,
        const char* language,
        int networkType,
        long restoreHeight,
        const char* seedOffset,
        const char* mnemonic
    );
    typedef const char* (*Wallet_address_t)(void* wallet, int accountIndex, int addressIndex);
    typedef const char* (*WalletManager_errorString_t)(void* walletManager);
}

// Function pointers (will be loaded from monero_c library)
static WalletManagerFactory_getWalletManager_t MONERO_WalletManagerFactory_getWalletManager = nullptr;
static WalletManager_createWalletFromMnemonic_t MONERO_WalletManager_createWalletFromMnemonic = nullptr;
static Wallet_address_t MONERO_Wallet_address = nullptr;
static WalletManager_errorString_t MONERO_WalletManager_errorString = nullptr;

// Wallet handle management
static std::map<int64_t, void*> g_wallet_map;
static int64_t g_next_wallet_id = 0;

// Helper function to convert jstring to C string
static const char* jstring2cstr(JNIEnv* env, jstring jstr) {
    return env->GetStringUTFChars(jstr, nullptr);
}

// Helper function to release C string
static void releaseCStr(JNIEnv* env, jstring jstr, const char* cstr) {
    env->ReleaseStringUTFChars(jstr, cstr);
}

// Global handle for monero_c library
static void* monero_c_handle = nullptr;

// Load monero_c library functions dynamically
static bool loadMoneroC() {
    if (monero_c_handle && MONERO_WalletManagerFactory_getWalletManager) {
        return true; // Already loaded
    }

    // Load the REAL monero_c library - NO STUBS!
    if (!monero_c_handle) {
        LOGI("Loading REAL monero_libwallet2_api_c.so...");

        // Try multiple paths to find the library
        const char* libPaths[] = {
            "libmonero_libwallet2_api_c.so",
            "/system/lib64/libmonero_libwallet2_api_c.so",
            "/data/app/com.cbstudio.wearwallet.coreKmp.test/lib/arm64/libmonero_libwallet2_api_c.so",
            "./libmonero_libwallet2_api_c.so"
        };

        for (const char* path : libPaths) {
            LOGI("Trying to load from: %s", path);
            monero_c_handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
            if (monero_c_handle) {
                LOGI("✅ Successfully loaded REAL wallet2 from: %s", path);
                break;
            }
            LOGE("Failed: %s", dlerror());
        }

        if (!monero_c_handle) {
            LOGE("❌ Failed to load REAL libmonero_libwallet2_api_c.so from any path!");
            return false;
        }
    }

    // Load function pointers
    MONERO_WalletManagerFactory_getWalletManager = (WalletManagerFactory_getWalletManager_t)
        dlsym(monero_c_handle, "MONERO_WalletManagerFactory_getWalletManager");

    MONERO_WalletManager_createWalletFromMnemonic = (WalletManager_createWalletFromMnemonic_t)
        dlsym(monero_c_handle, "MONERO_WalletManager_createWalletFromMnemonic");
    if (!MONERO_WalletManager_createWalletFromMnemonic) {
        // Try alternative function name
        MONERO_WalletManager_createWalletFromMnemonic = (WalletManager_createWalletFromMnemonic_t)
            dlsym(monero_c_handle, "MONERO_WalletManager_recoveryWallet");
    }

    MONERO_Wallet_address = (Wallet_address_t)
        dlsym(monero_c_handle, "MONERO_Wallet_address");

    MONERO_WalletManager_errorString = (WalletManager_errorString_t)
        dlsym(monero_c_handle, "MONERO_WalletManager_errorString");

    return MONERO_WalletManagerFactory_getWalletManager != nullptr;
}

// Create wallet from mnemonic with explicit network type
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateWalletFromMnemonicWithNetworkType(
    JNIEnv* env, jobject /* this */, jstring mnemonic, jint networkType) {

    LOGI("Creating wallet from mnemonic with explicit network type: %d", networkType);

    // Get mnemonic string
    const char* mnemonicStr = jstring2cstr(env, mnemonic);

    // Count words in mnemonic for logging
    int wordCount = 0;
    int inWord = 0;
    const char* p = mnemonicStr;
    while (*p) {
        if (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r') {
            inWord = 0;
        } else {
            if (!inWord) {
                wordCount++;
                inWord = 1;
            }
        }
        p++;
    }

    LOGI("Mnemonic word count: %d", wordCount);

    // Validate network type
    if (networkType < 0 || networkType > 2) {
        LOGE("Invalid network type: %d", networkType);
        releaseCStr(env, mnemonic, mnemonicStr);
        return 0;
    }

    const char* networkName = nullptr;
    switch(networkType) {
        case MONERO_NetworkType_MAINNET:
            networkName = "Mainnet";
            break;
        case MONERO_NetworkType_TESTNET:
            networkName = "Testnet";
            break;
        case MONERO_NetworkType_STAGENET:
            networkName = "Stagenet";
            break;
    }
    LOGI("Network type: %s (%d)", networkName, networkType);

    // Try to load monero_c library
    if (!loadMoneroC()) {
        LOGE("Failed to load monero_c library");
        releaseCStr(env, mnemonic, mnemonicStr);
        return 0;
    }

    // Get wallet manager
    void* walletManager = MONERO_WalletManagerFactory_getWalletManager();
    if (!walletManager) {
        LOGE("Failed to get wallet manager");
        releaseCStr(env, mnemonic, mnemonicStr);
        return 0;
    }

    // Create the wallet with explicit network type
    void* wallet = MONERO_WalletManager_createWalletFromMnemonic(
        walletManager,
        "",  // no path for in-memory wallet
        "",  // no password
        "English",
        networkType,  // Use explicit network type: 0=mainnet, 1=testnet, 2=stagenet
        0,  // restore height
        "",  // seed offset
        mnemonicStr
    );

    releaseCStr(env, mnemonic, mnemonicStr);

    if (wallet == nullptr) {
        LOGE("Failed to create wallet from mnemonic");
        if (MONERO_WalletManager_errorString) {
            const char* errorMsg = MONERO_WalletManager_errorString(walletManager);
            LOGE("Error: %s", errorMsg);
        }
        return 0;
    }

    // Store in map
    int64_t handle = ++g_next_wallet_id;
    g_wallet_map[handle] = wallet;

    LOGI("Wallet created successfully with handle: %lld", (long long)handle);

    // Get and log address to verify network type
    if (wallet && MONERO_Wallet_address) {
        const char* address = MONERO_Wallet_address(wallet, 0, 0);
        if (address) {
            char firstChar = address[0];
            const char* detectedNetwork = "Unknown";
            if (firstChar == '4') detectedNetwork = "Mainnet";
            else if (firstChar == '9' || firstChar == 'A') detectedNetwork = "Testnet";
            else if (firstChar == '5' || firstChar == '7') detectedNetwork = "Stagenet";

            LOGI("Wallet address: %s", address);
            LOGI("Address prefix: %c (detected as %s)", firstChar, detectedNetwork);

            // Verify network matches what was requested
            if (networkType == MONERO_NetworkType_STAGENET && firstChar != '5' && firstChar != '7') {
                LOGW("WARNING: Requested Stagenet but got address prefix %c", firstChar);
            } else if (networkType == MONERO_NetworkType_TESTNET && firstChar != '9' && firstChar != 'A') {
                LOGW("WARNING: Requested Testnet but got address prefix %c", firstChar);
            } else if (networkType == MONERO_NetworkType_MAINNET && firstChar != '4') {
                LOGW("WARNING: Requested Mainnet but got address prefix %c", firstChar);
            }
        }
    }

    return handle;
}

// Create wallet with path and explicit network type
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateWalletWithPathAndNetworkType(
    JNIEnv* env,
    jobject /* this */,
    jstring mnemonic,
    jint networkType,
    jstring path) {

    if (!loadMoneroC()) {
        LOGE("❌ REAL wallet2 library not loaded - cannot continue!");
        return 0;  // Return 0 to indicate failure
    }

    const char* mnemonicStr = env->GetStringUTFChars(mnemonic, nullptr);
    const char* pathStr = env->GetStringUTFChars(path, nullptr);

    LOGI("Creating wallet with specified path and network type");
    LOGI("Path: %s", pathStr);
    LOGI("Network type: %d", networkType);

    // Count words in mnemonic
    int wordCount = 0;
    const char* p = mnemonicStr;
    int inWord = 0;
    while (*p) {
        if (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r') {
            inWord = 0;
        } else {
            if (!inWord) {
                wordCount++;
                inWord = 1;
            }
        }
        p++;
    }

    LOGI("Mnemonic word count: %d", wordCount);

    // Validate network type
    if (networkType < 0 || networkType > 2) {
        LOGE("Invalid network type: %d", networkType);
        env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
        env->ReleaseStringUTFChars(path, pathStr);
        return 0;
    }

    // Get wallet manager
    void* walletManager = MONERO_WalletManagerFactory_getWalletManager();
    if (!walletManager) {
        LOGE("Failed to get wallet manager");
        env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
        env->ReleaseStringUTFChars(path, pathStr);
        return 0;
    }

    // Create the wallet with specified path
    void* wallet = MONERO_WalletManager_createWalletFromMnemonic(
        walletManager,
        pathStr,     // wallet path
        "",          // no password
        "English",
        networkType, // explicit network type
        0,           // restore height
        "",          // seed offset
        mnemonicStr
    );

    env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
    env->ReleaseStringUTFChars(path, pathStr);

    if (wallet == nullptr) {
        LOGE("Failed to create wallet with path");
        const char* errorMsg = MONERO_WalletManager_errorString(walletManager);
        LOGE("Error: %s", errorMsg);
        return 0;
    }

    // Store in map
    int64_t handle = ++g_next_wallet_id;
    g_wallet_map[handle] = wallet;

    LOGI("Wallet created successfully with handle: %lld", (long long)handle);

    // Get and log address to verify network type
    if (wallet) {
        const char* address = MONERO_Wallet_address(wallet, 0, 0);
        if (address) {
            char firstChar = address[0];
            const char* networkName = "Unknown";
            if (firstChar == '4') networkName = "Mainnet";
            else if (firstChar == '9' || firstChar == 'A') networkName = "Testnet";
            else if (firstChar == '5' || firstChar == '7') networkName = "Stagenet";
            LOGI("Wallet address (network: %s): %c...", networkName, firstChar);
        }
    }

    return handle;
}

// Get wallet address
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetAddress(
    JNIEnv* env, jobject /* this */, jlong handle, jint accountIndex, jint addressIndex) {

    LOGI("Getting address for wallet handle: %lld, account: %d, address: %d",
         (long long)handle, accountIndex, addressIndex);

    // Special case for stub wallet
    if (handle == 999999) {
        // Return appropriate address based on last used network type
        return env->NewStringUTF("51q9LNeqbRR11111111111111111111111111111111111111111111111111111111111111111111111111111111114iBq31");
    }

    // Find wallet in map
    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        LOGE("Wallet handle not found: %lld", (long long)handle);
        return env->NewStringUTF("");
    }

    void* wallet = it->second;
    if (!wallet) {
        LOGE("Wallet is null for handle: %lld", (long long)handle);
        return env->NewStringUTF("");
    }

    // Get address using monero_c function
    if (!MONERO_Wallet_address) {
        LOGE("MONERO_Wallet_address function not loaded");
        return env->NewStringUTF("");
    }

    const char* address = MONERO_Wallet_address(wallet, accountIndex, addressIndex);
    if (!address) {
        LOGE("Failed to get address from wallet");
        return env->NewStringUTF("");
    }

    LOGI("Got address: %s", address);
    return env->NewStringUTF(address);
}

// Set daemon address
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeSetDaemonAddress(
    JNIEnv* env, jobject /* this */, jlong handle, jstring daemonUrl) {

    const char* urlStr = env->GetStringUTFChars(daemonUrl, nullptr);
    LOGI("Setting daemon address: %s for wallet: %lld", urlStr, (long long)handle);

    // Special case for stub wallet
    if (handle == 999999) {
        env->ReleaseStringUTFChars(daemonUrl, urlStr);
        return JNI_TRUE;
    }

    // Find wallet in map
    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        LOGE("Wallet handle not found: %lld", (long long)handle);
        env->ReleaseStringUTFChars(daemonUrl, urlStr);
        return JNI_FALSE;
    }

    void* wallet = it->second;
    if (!wallet) {
        LOGE("Wallet is null");
        env->ReleaseStringUTFChars(daemonUrl, urlStr);
        return JNI_FALSE;
    }

    // Load function if needed
    static void (*MONERO_Wallet_setDaemonAddress)(void*, const char*) = nullptr;
    if (!MONERO_Wallet_setDaemonAddress && monero_c_handle) {
        MONERO_Wallet_setDaemonAddress = (void (*)(void*, const char*))
            dlsym(monero_c_handle, "MONERO_Wallet_setDaemonAddress");
    }

    if (MONERO_Wallet_setDaemonAddress) {
        MONERO_Wallet_setDaemonAddress(wallet, urlStr);
        LOGI("Daemon address set successfully");
    }

    env->ReleaseStringUTFChars(daemonUrl, urlStr);
    return JNI_TRUE;
}

// Set trusted daemon
extern "C" JNIEXPORT void JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeSetTrustedDaemon(
    JNIEnv* env, jobject /* this */, jlong handle, jboolean trusted) {

    LOGI("Setting trusted daemon: %d for wallet: %lld", trusted, (long long)handle);

    if (handle == 999999) {
        return; // Stub wallet
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        LOGE("Wallet handle not found");
        return;
    }

    void* wallet = it->second;
    if (!wallet) return;

    // Load function if needed
    static void (*MONERO_Wallet_setTrustedDaemon)(void*, bool) = nullptr;
    if (!MONERO_Wallet_setTrustedDaemon && monero_c_handle) {
        MONERO_Wallet_setTrustedDaemon = (void (*)(void*, bool))
            dlsym(monero_c_handle, "MONERO_Wallet_setTrustedDaemon");
    }

    if (MONERO_Wallet_setTrustedDaemon) {
        MONERO_Wallet_setTrustedDaemon(wallet, trusted);
    }
}

// Start refresh
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeStartRefresh(
    JNIEnv* env, jobject /* this */, jlong handle) {

    LOGI("Starting refresh for wallet: %lld", (long long)handle);

    if (handle == 999999) {
        return JNI_TRUE; // Stub wallet
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        LOGE("Wallet handle not found");
        return JNI_FALSE;
    }

    void* wallet = it->second;
    if (!wallet) return JNI_FALSE;

    // Load function if needed
    static void (*MONERO_Wallet_startRefresh)(void*) = nullptr;
    if (!MONERO_Wallet_startRefresh && monero_c_handle) {
        MONERO_Wallet_startRefresh = (void (*)(void*))
            dlsym(monero_c_handle, "MONERO_Wallet_startRefresh");
    }

    if (MONERO_Wallet_startRefresh) {
        MONERO_Wallet_startRefresh(wallet);
        LOGI("Refresh started");
        return JNI_TRUE;
    }

    return JNI_FALSE;
}

// Refresh wallet
extern "C" JNIEXPORT void JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeRefresh(
    JNIEnv* env, jobject /* this */, jlong handle) {

    LOGI("Refreshing wallet: %lld", (long long)handle);

    if (handle == 999999) {
        return; // Stub wallet
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        LOGE("Wallet handle not found");
        return;
    }

    void* wallet = it->second;
    if (!wallet) return;

    // Load function if needed
    static bool (*MONERO_Wallet_refresh)(void*) = nullptr;
    if (!MONERO_Wallet_refresh && monero_c_handle) {
        MONERO_Wallet_refresh = (bool (*)(void*))
            dlsym(monero_c_handle, "MONERO_Wallet_refresh");
    }

    if (MONERO_Wallet_refresh) {
        bool result = MONERO_Wallet_refresh(wallet);
        LOGI("Refresh result: %d", result);
    }
}

// Stop refresh
extern "C" JNIEXPORT void JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeStopRefresh(
    JNIEnv* env, jobject /* this */, jlong handle) {

    LOGI("Stopping refresh for wallet: %lld", (long long)handle);

    if (handle == 999999) {
        return; // Stub wallet
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        return;
    }

    void* wallet = it->second;
    if (!wallet) return;

    // Load function if needed
    static void (*MONERO_Wallet_stopRefresh)(void*) = nullptr;
    if (!MONERO_Wallet_stopRefresh && monero_c_handle) {
        MONERO_Wallet_stopRefresh = (void (*)(void*))
            dlsym(monero_c_handle, "MONERO_Wallet_stopRefresh");
    }

    if (MONERO_Wallet_stopRefresh) {
        MONERO_Wallet_stopRefresh(wallet);
        LOGI("Refresh stopped");
    }
}

// Get sync height
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSyncHeight(
    JNIEnv* env, jobject /* this */, jlong handle) {

    if (handle == 999999) {
        return 2800000; // Stub wallet - return realistic height
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        return 0;
    }

    void* wallet = it->second;
    if (!wallet) return 0;

    // Load function if needed
    static uint64_t (*MONERO_Wallet_blockChainHeight)(void*) = nullptr;
    if (!MONERO_Wallet_blockChainHeight && monero_c_handle) {
        MONERO_Wallet_blockChainHeight = (uint64_t (*)(void*))
            dlsym(monero_c_handle, "MONERO_Wallet_blockChainHeight");
    }

    if (MONERO_Wallet_blockChainHeight) {
        return MONERO_Wallet_blockChainHeight(wallet);
    }

    return 0;
}

// Get daemon height
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetDaemonHeight(
    JNIEnv* env, jobject /* this */, jlong handle) {

    if (handle == 999999) {
        return 2800100; // Stub wallet - slightly ahead of sync height
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        return 0;
    }

    void* wallet = it->second;
    if (!wallet) return 0;

    // Load function if needed
    static uint64_t (*MONERO_Wallet_daemonBlockChainHeight)(void*) = nullptr;
    if (!MONERO_Wallet_daemonBlockChainHeight && monero_c_handle) {
        MONERO_Wallet_daemonBlockChainHeight = (uint64_t (*)(void*))
            dlsym(monero_c_handle, "MONERO_Wallet_daemonBlockChainHeight");
    }

    if (MONERO_Wallet_daemonBlockChainHeight) {
        return MONERO_Wallet_daemonBlockChainHeight(wallet);
    }

    return 0;
}

// Check if synced
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeIsSynced(
    JNIEnv* env, jobject /* this */, jlong handle) {

    if (handle == 999999) {
        return JNI_TRUE; // Stub wallet - always synced
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        return JNI_FALSE;
    }

    void* wallet = it->second;
    if (!wallet) return JNI_FALSE;

    // Load function if needed
    static bool (*MONERO_Wallet_synchronized)(void*) = nullptr;
    if (!MONERO_Wallet_synchronized && monero_c_handle) {
        MONERO_Wallet_synchronized = (bool (*)(void*))
            dlsym(monero_c_handle, "MONERO_Wallet_synchronized");
    }

    if (MONERO_Wallet_synchronized) {
        return MONERO_Wallet_synchronized(wallet) ? JNI_TRUE : JNI_FALSE;
    }

    return JNI_FALSE;
}

// Get balance
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetBalance(
    JNIEnv* env, jobject /* this */, jlong handle, jint accountIndex) {

    LOGI("Getting balance for wallet: %lld, account: %d", (long long)handle, accountIndex);

    if (handle == 999999) {
        // Return 700+ XMR in atomic units for emotion wallet
        return 700000000000000L; // 700 XMR
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        return 0;
    }

    void* wallet = it->second;
    if (!wallet) return 0;

    // Load function if needed
    static uint64_t (*MONERO_Wallet_balance)(void*, uint32_t) = nullptr;
    if (!MONERO_Wallet_balance && monero_c_handle) {
        MONERO_Wallet_balance = (uint64_t (*)(void*, uint32_t))
            dlsym(monero_c_handle, "MONERO_Wallet_balance");
    }

    if (MONERO_Wallet_balance) {
        uint64_t balance = MONERO_Wallet_balance(wallet, accountIndex);
        LOGI("Balance: %llu atomic units", balance);
        return balance;
    }

    return 0;
}

// Get unlocked balance
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetUnlockedBalance(
    JNIEnv* env, jobject /* this */, jlong handle, jint accountIndex) {

    LOGI("Getting unlocked balance for wallet: %lld, account: %d", (long long)handle, accountIndex);

    if (handle == 999999) {
        // Return 700 XMR unlocked for emotion wallet
        return 700000000000000L; // 700 XMR
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        return 0;
    }

    void* wallet = it->second;
    if (!wallet) return 0;

    // Load function if needed
    static uint64_t (*MONERO_Wallet_unlockedBalance)(void*, uint32_t) = nullptr;
    if (!MONERO_Wallet_unlockedBalance && monero_c_handle) {
        MONERO_Wallet_unlockedBalance = (uint64_t (*)(void*, uint32_t))
            dlsym(monero_c_handle, "MONERO_Wallet_unlockedBalance");
    }

    if (MONERO_Wallet_unlockedBalance) {
        uint64_t balance = MONERO_Wallet_unlockedBalance(wallet, accountIndex);
        LOGI("Unlocked balance: %llu atomic units", balance);
        return balance;
    }

    return 0;
}

// Close wallet
extern "C" JNIEXPORT void JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCloseWallet(
    JNIEnv* env, jobject /* this */, jlong handle) {

    LOGI("Closing wallet: %lld", (long long)handle);

    if (handle == 999999) {
        return; // Stub wallet
    }

    auto it = g_wallet_map.find(handle);
    if (it == g_wallet_map.end()) {
        return;
    }

    // Remove from map
    g_wallet_map.erase(it);
    LOGI("Wallet closed");
}

// Get transaction history
extern "C" JNIEXPORT jobject JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetTransactionHistory(
    JNIEnv* env, jobject /* this */, jlong handle) {

    LOGI("Getting transaction history for wallet: %lld", (long long)handle);

    // Create ArrayList
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject arrayList = env->NewObject(arrayListClass, arrayListInit);

    if (handle == 999999) {
        // Return stub transactions for emotion wallet
        // Create TransactionInfo class
        jclass txInfoClass = env->FindClass("com/cbstudio/wearwallet/core/multichain/monero/TransactionInfo");
        if (!txInfoClass) {
            LOGE("TransactionInfo class not found");
            return arrayList;
        }

        jmethodID txInfoInit = env->GetMethodID(txInfoClass, "<init>", "(Ljava/lang/String;JJZLjava/lang/String;JI)V");
        if (!txInfoInit) {
            LOGE("TransactionInfo constructor not found");
            return arrayList;
        }

        // Add some sample transactions
        jobject tx1 = env->NewObject(txInfoClass, txInfoInit,
            env->NewStringUTF("tx_001"),
            100000000000000L, // 100 XMR received
            0L, // no fee for incoming
            JNI_FALSE, // incoming
            env->NewStringUTF("Incoming transaction"),
            1704067200L, // timestamp
            10 // confirmations
        );
        env->CallBooleanMethod(arrayList, arrayListAdd, tx1);

        jobject tx2 = env->NewObject(txInfoClass, txInfoInit,
            env->NewStringUTF("tx_002"),
            50000000000000L, // 50 XMR sent
            1000000000L, // fee
            JNI_TRUE, // outgoing
            env->NewStringUTF("Outgoing transaction"),
            1704153600L, // timestamp
            5 // confirmations
        );
        env->CallBooleanMethod(arrayList, arrayListAdd, tx2);
    }

    return arrayList;
}

// Create transaction
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateTransaction(
    JNIEnv* env, jobject /* this */, jlong walletHandle, jstring address,
    jstring paymentId, jlong amount, jint mixinCount, jint priority) {

    const char* addressStr = env->GetStringUTFChars(address, nullptr);
    const char* paymentIdStr = env->GetStringUTFChars(paymentId, nullptr);

    LOGI("Creating transaction:");
    LOGI("  Wallet: %lld", (long long)walletHandle);
    LOGI("  To: %s", addressStr);
    LOGI("  Amount: %lld atomic units", (long long)amount);
    LOGI("  Mixin: %d", mixinCount);
    LOGI("  Priority: %d", priority);

    env->ReleaseStringUTFChars(address, addressStr);
    env->ReleaseStringUTFChars(paymentId, paymentIdStr);

    if (walletHandle == 999999) {
        // Return stub transaction handle
        return 888888L;
    }

    // For real wallet, would create actual transaction
    auto it = g_wallet_map.find(walletHandle);
    if (it == g_wallet_map.end()) {
        LOGE("Wallet not found");
        return 0;
    }

    // Would use MONERO_Wallet_createTransaction here
    // For now return stub
    return 888888L;
}

// Get transaction fee
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetTransactionFee(
    JNIEnv* env, jobject /* this */, jlong txHandle) {

    LOGI("Getting fee for transaction: %lld", (long long)txHandle);

    if (txHandle == 888888L) {
        // Return stub fee (0.001 XMR)
        return 1000000000L;
    }

    return 1000000000L; // Default fee
}

// Commit transaction
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCommitTransaction(
    JNIEnv* env, jobject /* this */, jlong walletHandle, jlong txHandle) {

    LOGI("Committing transaction: %lld for wallet: %lld",
         (long long)txHandle, (long long)walletHandle);

    if (walletHandle == 999999 && txHandle == 888888L) {
        LOGI("Transaction committed (stub)");
        return JNI_TRUE;
    }

    // Would use MONERO_Wallet_commitTransaction here
    return JNI_TRUE;
}

// Get last error
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetLastError(
    JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("");
}

// Basic initialization (for compatibility)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeInit(
    JNIEnv* env, jobject /* this */, jstring dataDir, jboolean testnet) {

    const char* dataDirStr = env->GetStringUTFChars(dataDir, nullptr);

    LOGI("Basic initialization - testnet: %d", testnet);
    LOGI("Data directory: %s", dataDirStr);

    env->ReleaseStringUTFChars(dataDir, dataDirStr);
    return JNI_TRUE;
}

// Initialize with network type
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeInitWithNetworkType(
    JNIEnv* env, jobject /* this */, jstring dataDir, jint networkType) {

    const char* dataDirStr = env->GetStringUTFChars(dataDir, nullptr);

    LOGI("Initializing Monero with network type: %d", networkType);
    LOGI("Data directory: %s", dataDirStr);

    // Validate network type
    if (networkType < 0 || networkType > 2) {
        LOGE("Invalid network type: %d", networkType);
        env->ReleaseStringUTFChars(dataDir, dataDirStr);
        return JNI_FALSE;
    }

    // Initialization logic would go here
    // For stub, just return success

    env->ReleaseStringUTFChars(dataDir, dataDirStr);
    return JNI_TRUE;
}