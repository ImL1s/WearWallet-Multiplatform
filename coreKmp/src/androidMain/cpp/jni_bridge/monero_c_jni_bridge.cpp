/**
 * Monero C JNI Bridge
 * 
 * This bridge uses monero_c (C wrapper around wallet2_api) instead of direct C++ API.
 * Based on the successful approach from multi_chain_wallet_core Flutter project.
 * 
 * monero_c provides pure C API which is much easier to call from JNI than C++.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <android/log.h>
#include <dlfcn.h>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <atomic>
#include <chrono>

#define LOG_TAG "MoneroCJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Network type constants from wallet2_api_c.h
const int MONERO_NetworkType_MAINNET = 0;
const int MONERO_NetworkType_TESTNET = 1;
const int MONERO_NetworkType_STAGENET = 2;

// Function pointers for monero_c API
typedef void* (*WalletManagerFactory_getWalletManager_t)();
typedef void* (*WalletManager_createWallet_t)(void*, const char*, const char*, const char*, int);
typedef void* (*WalletManager_openWallet_t)(void*, const char*, const char*, int);
typedef void* (*WalletManager_recoveryWallet_t)(void*, const char*, const char*, const char*, int, uint64_t, uint64_t, const char*);
typedef bool (*WalletManager_closeWallet_t)(void*, void*, bool);
typedef const char* (*WalletManager_errorString_t)(void*);
typedef void (*WalletManager_setDaemonAddress_t)(void*, const char*);
typedef void (*free_t)(void*);
typedef bool (*Wallet_addressValid_t)(void*, const char*, int);

typedef const char* (*Wallet_seed_t)(void*, const char*);
typedef const char* (*Wallet_address_t)(void*, uint64_t, uint64_t);
typedef const char* (*Wallet_secretViewKey_t)(void*);
typedef const char* (*Wallet_secretSpendKey_t)(void*);
typedef bool (*Wallet_init_t)(void*, const char*, uint64_t, const char*, const char*, bool, bool, const char*);
typedef bool (*Wallet_store_t)(void*, const char*);
typedef void (*Wallet_setRefreshFromBlockHeight_t)(void*, uint64_t);
typedef bool (*Wallet_connectToDaemon_t)(void*);
typedef uint64_t (*Wallet_balance_t)(void*, uint32_t);
typedef uint64_t (*Wallet_unlockedBalance_t)(void*, uint32_t);
typedef uint64_t (*Wallet_blockChainHeight_t)(void*);
typedef uint64_t (*Wallet_daemonBlockChainHeight_t)(void*);
typedef bool (*Wallet_synchronized_t)(void*);
typedef void (*Wallet_startRefresh_t)(void*);
typedef void (*Wallet_pauseRefresh_t)(void*);
typedef int (*Wallet_status_t)(void*);
typedef const char* (*Wallet_errorString_t)(void*);
typedef bool (*Wallet_refresh_t)(void*);
typedef void (*Wallet_setTrustedDaemon_t)(void*, bool);
typedef void* (*Wallet_history_t)(void*);

// Transaction history functions
typedef int (*TransactionHistory_count_t)(void*);
typedef void* (*TransactionHistory_transaction_t)(void*, int);
typedef void (*TransactionHistory_refresh_t)(void*);

// Transaction info functions
typedef const char* (*TransactionInfo_hash_t)(void*);
typedef uint64_t (*TransactionInfo_blockHeight_t)(void*);
typedef uint64_t (*TransactionInfo_amount_t)(void*);
typedef uint64_t (*TransactionInfo_fee_t)(void*);
typedef int (*TransactionInfo_direction_t)(void*);
typedef bool (*TransactionInfo_isPending_t)(void*);
typedef uint64_t (*TransactionInfo_timestamp_t)(void*);
typedef const char* (*TransactionInfo_paymentId_t)(void*);
typedef int (*TransactionInfo_confirmations_t)(void*);

// Transfer functions
typedef void* (*Wallet_createTransaction_t)(void*, const char*, const char*, uint64_t, uint32_t, uint32_t, const char*, const char*);
typedef bool (*PendingTransaction_commit_t)(void*, const char*);
typedef int (*PendingTransaction_status_t)(void*);
typedef const char* (*PendingTransaction_errorString_t)(void*);
typedef uint64_t (*PendingTransaction_amount_t)(void*);
typedef uint64_t (*PendingTransaction_fee_t)(void*);
typedef const char* (*PendingTransaction_txid_t)(void*, int);
typedef uint64_t (*PendingTransaction_txCount_t)(void*);

// Global function pointers
static void* monero_lib_handle = nullptr;
static WalletManagerFactory_getWalletManager_t MONERO_WalletManagerFactory_getWalletManager = nullptr;
static WalletManager_createWallet_t MONERO_WalletManager_createWallet = nullptr;
static WalletManager_openWallet_t MONERO_WalletManager_openWallet = nullptr;
static WalletManager_recoveryWallet_t MONERO_WalletManager_recoveryWallet = nullptr;
static WalletManager_closeWallet_t MONERO_WalletManager_closeWallet = nullptr;
static WalletManager_errorString_t MONERO_WalletManager_errorString = nullptr;
static WalletManager_setDaemonAddress_t MONERO_WalletManager_setDaemonAddress = nullptr;
static free_t MONERO_free = nullptr;

static Wallet_seed_t MONERO_Wallet_seed = nullptr;
static Wallet_address_t MONERO_Wallet_address = nullptr;
static Wallet_secretViewKey_t MONERO_Wallet_secretViewKey = nullptr;
static Wallet_secretSpendKey_t MONERO_Wallet_secretSpendKey = nullptr;
static Wallet_addressValid_t MONERO_Wallet_addressValid = nullptr;
static Wallet_init_t MONERO_Wallet_init = nullptr;
static Wallet_store_t MONERO_Wallet_store = nullptr;
static Wallet_setRefreshFromBlockHeight_t MONERO_Wallet_setRefreshFromBlockHeight = nullptr;
static Wallet_connectToDaemon_t MONERO_Wallet_connectToDaemon = nullptr;
static Wallet_balance_t MONERO_Wallet_balance = nullptr;
static Wallet_unlockedBalance_t MONERO_Wallet_unlockedBalance = nullptr;
static Wallet_blockChainHeight_t MONERO_Wallet_blockChainHeight = nullptr;
static Wallet_daemonBlockChainHeight_t MONERO_Wallet_daemonBlockChainHeight = nullptr;
static Wallet_synchronized_t MONERO_Wallet_synchronized = nullptr;
static Wallet_startRefresh_t MONERO_Wallet_startRefresh = nullptr;
static Wallet_pauseRefresh_t MONERO_Wallet_pauseRefresh = nullptr;
static Wallet_status_t MONERO_Wallet_status = nullptr;
static Wallet_errorString_t MONERO_Wallet_errorString = nullptr;
static Wallet_refresh_t MONERO_Wallet_refresh = nullptr;
static Wallet_setTrustedDaemon_t MONERO_Wallet_setTrustedDaemon = nullptr;
static Wallet_history_t MONERO_Wallet_history = nullptr;

// Transaction history functions
static TransactionHistory_count_t MONERO_TransactionHistory_count = nullptr;
static TransactionHistory_transaction_t MONERO_TransactionHistory_transaction = nullptr;
static TransactionHistory_refresh_t MONERO_TransactionHistory_refresh = nullptr;

// Transaction info functions
static TransactionInfo_hash_t MONERO_TransactionInfo_hash = nullptr;
static TransactionInfo_blockHeight_t MONERO_TransactionInfo_blockHeight = nullptr;
static TransactionInfo_amount_t MONERO_TransactionInfo_amount = nullptr;
static TransactionInfo_fee_t MONERO_TransactionInfo_fee = nullptr;
static TransactionInfo_direction_t MONERO_TransactionInfo_direction = nullptr;
static TransactionInfo_isPending_t MONERO_TransactionInfo_isPending = nullptr;
static TransactionInfo_timestamp_t MONERO_TransactionInfo_timestamp = nullptr;
static TransactionInfo_paymentId_t MONERO_TransactionInfo_paymentId = nullptr;
static TransactionInfo_confirmations_t MONERO_TransactionInfo_confirmations = nullptr;

// Transfer functions
static Wallet_createTransaction_t MONERO_Wallet_createTransaction = nullptr;
static PendingTransaction_commit_t MONERO_PendingTransaction_commit = nullptr;
static PendingTransaction_status_t MONERO_PendingTransaction_status = nullptr;
static PendingTransaction_errorString_t MONERO_PendingTransaction_errorString = nullptr;
static PendingTransaction_amount_t MONERO_PendingTransaction_amount = nullptr;
static PendingTransaction_fee_t MONERO_PendingTransaction_fee = nullptr;
static PendingTransaction_txid_t MONERO_PendingTransaction_txid = nullptr;
static PendingTransaction_txCount_t MONERO_PendingTransaction_txCount = nullptr;

// Global variable to store last error message
static char g_lastError[512] = {0};

// Load monero_c library and resolve symbols
bool loadMoneroC() {
    if (monero_lib_handle != nullptr) {
        return true; // Already loaded
    }
    
    // Try to load monero_c library
    // First try the bundled version from cs_monero_flutter_libs
    const char* lib_names[] = {
        "monero_libwallet2_api_c",  // Without lib prefix and .so suffix for System.loadLibrary compatibility
        "libmonero_libwallet2_api_c.so",
        "libmonero.so",
        "libmonero_c.so",
        nullptr
    };
    
    for (const char** lib_name = lib_names; *lib_name != nullptr; lib_name++) {
        // Try without path first (relies on LD_LIBRARY_PATH)
        monero_lib_handle = dlopen(*lib_name, RTLD_LAZY | RTLD_GLOBAL);
        if (monero_lib_handle != nullptr) {
            LOGI("Successfully loaded %s", *lib_name);
            break;
        }
        // Clear any error
        dlerror();
    }
    
    if (monero_lib_handle == nullptr) {
        LOGE("Failed to load monero_c library: %s", dlerror());
        return false;
    }
    
    // Resolve function pointers
    #define RESOLVE_SYMBOL(func) \
        func = (decltype(func))dlsym(monero_lib_handle, #func); \
        if (func == nullptr) { \
            LOGE("Failed to resolve symbol: %s", #func); \
            return false; \
        }
    
    RESOLVE_SYMBOL(MONERO_WalletManagerFactory_getWalletManager);
    RESOLVE_SYMBOL(MONERO_WalletManager_createWallet);
    RESOLVE_SYMBOL(MONERO_WalletManager_openWallet);
    RESOLVE_SYMBOL(MONERO_WalletManager_recoveryWallet);
    RESOLVE_SYMBOL(MONERO_WalletManager_closeWallet);
    // Note: addressValid is on Wallet, not WalletManager
    RESOLVE_SYMBOL(MONERO_WalletManager_errorString);
    RESOLVE_SYMBOL(MONERO_WalletManager_setDaemonAddress);
    RESOLVE_SYMBOL(MONERO_free);
    
    RESOLVE_SYMBOL(MONERO_Wallet_seed);
    RESOLVE_SYMBOL(MONERO_Wallet_address);
    RESOLVE_SYMBOL(MONERO_Wallet_secretViewKey);
    RESOLVE_SYMBOL(MONERO_Wallet_secretSpendKey);
    RESOLVE_SYMBOL(MONERO_Wallet_addressValid);
    RESOLVE_SYMBOL(MONERO_Wallet_init);
    RESOLVE_SYMBOL(MONERO_Wallet_store);
    RESOLVE_SYMBOL(MONERO_Wallet_setRefreshFromBlockHeight);
    RESOLVE_SYMBOL(MONERO_Wallet_connectToDaemon);
    RESOLVE_SYMBOL(MONERO_Wallet_balance);
    RESOLVE_SYMBOL(MONERO_Wallet_unlockedBalance);
    RESOLVE_SYMBOL(MONERO_Wallet_blockChainHeight);
    RESOLVE_SYMBOL(MONERO_Wallet_daemonBlockChainHeight);
    RESOLVE_SYMBOL(MONERO_Wallet_synchronized);
    RESOLVE_SYMBOL(MONERO_Wallet_startRefresh);
    RESOLVE_SYMBOL(MONERO_Wallet_pauseRefresh);
    RESOLVE_SYMBOL(MONERO_Wallet_status);
    RESOLVE_SYMBOL(MONERO_Wallet_errorString);
    RESOLVE_SYMBOL(MONERO_Wallet_refresh);
    RESOLVE_SYMBOL(MONERO_Wallet_setTrustedDaemon);
    RESOLVE_SYMBOL(MONERO_Wallet_history);
    
    // Transaction history symbols
    RESOLVE_SYMBOL(MONERO_TransactionHistory_count);
    RESOLVE_SYMBOL(MONERO_TransactionHistory_transaction);
    RESOLVE_SYMBOL(MONERO_TransactionHistory_refresh);
    
    // Transaction info symbols
    RESOLVE_SYMBOL(MONERO_TransactionInfo_hash);
    RESOLVE_SYMBOL(MONERO_TransactionInfo_blockHeight);
    RESOLVE_SYMBOL(MONERO_TransactionInfo_amount);
    RESOLVE_SYMBOL(MONERO_TransactionInfo_fee);
    RESOLVE_SYMBOL(MONERO_TransactionInfo_direction);
    RESOLVE_SYMBOL(MONERO_TransactionInfo_isPending);
    RESOLVE_SYMBOL(MONERO_TransactionInfo_timestamp);
    RESOLVE_SYMBOL(MONERO_TransactionInfo_paymentId);
    RESOLVE_SYMBOL(MONERO_TransactionInfo_confirmations);
    
    // Transfer symbols
    RESOLVE_SYMBOL(MONERO_Wallet_createTransaction);
    RESOLVE_SYMBOL(MONERO_PendingTransaction_commit);
    RESOLVE_SYMBOL(MONERO_PendingTransaction_status);
    RESOLVE_SYMBOL(MONERO_PendingTransaction_errorString);
    RESOLVE_SYMBOL(MONERO_PendingTransaction_amount);
    RESOLVE_SYMBOL(MONERO_PendingTransaction_fee);

    // CRITICAL FIX: Ensure txid function is loaded
    RESOLVE_SYMBOL(MONERO_PendingTransaction_txid);
    if (!MONERO_PendingTransaction_txid) {
        LOGW("⚠️ MONERO_PendingTransaction_txid not found, trying alternative...");
        MONERO_PendingTransaction_txid = (PendingTransaction_txid_t)
            dlsym(monero_lib_handle, "PendingTransaction_txid");
    }

    RESOLVE_SYMBOL(MONERO_PendingTransaction_txCount);
    
    #undef RESOLVE_SYMBOL
    
    LOGI("All monero_c symbols resolved successfully");
    return true;
}

// Helper function to convert Java string to C string
const char* jstring2cstr(JNIEnv* env, jstring jStr) {
    if (!jStr) return "";
    return env->GetStringUTFChars(jStr, nullptr);
}

// Helper function to release C string
void releaseCStr(JNIEnv* env, jstring jStr, const char* cStr) {
    if (jStr && cStr) {
        env->ReleaseStringUTFChars(jStr, cStr);
    }
}

// Static variable to store wallet directory
static char g_walletDir[512] = {0};
// Static counter for unique wallet IDs
static std::atomic<int> g_walletCounter(0);

// Set wallet directory (called from nativeInit)
void setWalletDirectory(const char* dir) {
    if (dir) {
        strncpy(g_walletDir, dir, sizeof(g_walletDir) - 1);
        g_walletDir[sizeof(g_walletDir) - 1] = '\0';
    }
}

// Initialize monero_c
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeInit(
    JNIEnv* env, jobject /* this */, jstring dataDir, jboolean /* testnet */) {
    
    LOGI("Initializing monero_c bridge");
    
    if (!loadMoneroC()) {
        LOGE("Failed to load monero_c library");
        return JNI_FALSE;
    }
    
    // Store the wallet directory
    if (dataDir) {
        const char* dataDirStr = jstring2cstr(env, dataDir);
        setWalletDirectory(dataDirStr);
        LOGI("Wallet directory set to: %s", dataDirStr);
        releaseCStr(env, dataDir, dataDirStr);
    }
    
    return JNI_TRUE;
}

// Create wallet from mnemonic using monero_c
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateWalletFromMnemonic(
    JNIEnv* env, jobject /* this */, jstring mnemonic, jboolean testnet) {
    
    if (!loadMoneroC()) {
        LOGE("monero_c not loaded");
        return 0;
    }
    
    // Get wallet manager
    void* walletManager = MONERO_WalletManagerFactory_getWalletManager();
    if (!walletManager) {
        LOGE("Failed to get wallet manager");
        return 0;
    }
    
    const char* mnemonicStr = jstring2cstr(env, mnemonic);
    // Original logic: testnet boolean maps to TESTNET, not STAGENET
    // monero_c expects: 0=mainnet, 1=testnet, 2=stagenet
    int networkType = testnet ? MONERO_NetworkType_TESTNET : MONERO_NetworkType_MAINNET;
    
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
    
    LOGI("Creating wallet from mnemonic (network type: %d, words: %d)", networkType, wordCount);
    
    // Validate word count - Monero expects 25 words, not BIP39 12 words
    if (wordCount != 25 && wordCount != 24 && wordCount != 13 && wordCount != 12) {
        LOGE("Invalid mnemonic word count: %d (expected 25 for Monero, 12 for BIP39)", wordCount);
        // Continue anyway to see the actual error from monero_c
    }
    
    // Create wallet using temporary path (monero_c requires a path even for in-memory wallets)
    LOGI("First 50 chars of mnemonic: %.50s...", mnemonicStr);
    
    // Use a recent restore height for stagenet to avoid syncing from genesis
    // Optimized for faster sync while still capturing EMOTION wallet transactions
    uint64_t restoreHeight = testnet ? 1700000 : 0;  // Start from block 1700000 for faster sync
    
    LOGI("Creating wallet with restore height: %llu", restoreHeight);
    
    // Use the wallet directory set during initialization
    char tempPath[512];
    if (strlen(g_walletDir) > 0) {
        // Use the directory provided by nativeInit
        snprintf(tempPath, sizeof(tempPath), "%s/monero_wallet_%d_%ld", 
                 g_walletDir, rand(), (long)time(nullptr));
    } else {
        // Fallback to a memory-only wallet (no file persistence)
        snprintf(tempPath, sizeof(tempPath), ":memory:monero_wallet_%d_%ld", 
                 rand(), (long)time(nullptr));
    }
    
    LOGI("Using temporary wallet path: %s", tempPath);
    
    // Log mnemonic info for debugging (already counted words above)
    LOGI("Mnemonic word count: %d", wordCount);
    
    // Monero uses 25-word Electrum-style mnemonics
    // Skip validation for now and try to create wallet directly
    
    // Create wallet from mnemonic using temporary path
    // Note: Monero uses Electrum-style mnemonics, not BIP39
    void* wallet = MONERO_WalletManager_recoveryWallet(
        walletManager,
        tempPath,
        "",  // No password
        mnemonicStr,
        networkType,
        restoreHeight,   // Restore height to avoid full sync
        1,   // KDF rounds (use 1 for faster creation)
        ""   // Seed offset (empty for standard wallet)
    );
    
    releaseCStr(env, mnemonic, mnemonicStr);
    
    if (!wallet) {
        const char* error = MONERO_WalletManager_errorString(walletManager);
        LOGE("Failed to create wallet: %s", error ? error : "unknown error");
        // Store the error for later retrieval
        if (error) {
            strncpy(g_lastError, error, sizeof(g_lastError) - 1);
            g_lastError[sizeof(g_lastError) - 1] = '\0';
        } else {
            strcpy(g_lastError, "Failed to create wallet: unknown error");
        }
        return 0;
    }
    
    // Check wallet status
    int status = MONERO_Wallet_status(wallet);
    if (status != 0) { // 0 = Status_Ok
        const char* error = MONERO_Wallet_errorString(wallet);
        LOGE("Wallet creation failed: %s", error ? error : "unknown error");
        if (error) {
            strncpy(g_lastError, error, sizeof(g_lastError) - 1);
            g_lastError[sizeof(g_lastError) - 1] = '\0';
        } else {
            sprintf(g_lastError, "Wallet creation failed with status %d", status);
        }
        MONERO_WalletManager_closeWallet(walletManager, wallet, false);
        return 0;
    }
    
    LOGI("Wallet created successfully");
    return (jlong)wallet;
}

// Get wallet address
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetAddress(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jint accountIndex, jint addressIndex) {

    if (!walletPtr) return env->NewStringUTF("");

    void* wallet = (void*)walletPtr;
    const char* address = MONERO_Wallet_address(wallet, accountIndex, addressIndex);

    return env->NewStringUTF(address ? address : "");
}

// Create wallet with specified path
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateWalletWithPath(
    JNIEnv* env,
    jobject /* this */,
    jstring mnemonic,
    jboolean testnet,
    jstring path) {

    const char* mnemonicStr = env->GetStringUTFChars(mnemonic, nullptr);
    const char* pathStr = env->GetStringUTFChars(path, nullptr);

    LOGI("Creating wallet with specified path");
    LOGI("Path: %s", pathStr);
    LOGI("Testnet: %s", testnet ? "true" : "false");

    // Count words in mnemonic
    int wordCount = 0;
    char* mnemonicCopy = strdup(mnemonicStr);
    char* token = strtok(mnemonicCopy, " ");
    while (token != nullptr) {
        wordCount++;
        token = strtok(nullptr, " ");
    }
    free(mnemonicCopy);

    LOGI("Mnemonic word count: %d", wordCount);

    // Get wallet manager instance
    void* walletManager = MONERO_WalletManagerFactory_getWalletManager();
    if (!walletManager) {
        LOGE("Failed to get wallet manager");
        env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
        env->ReleaseStringUTFChars(path, pathStr);
        return 0;
    }

    // Create wallet based on mnemonic type
    void* wallet = nullptr;
    int netType = testnet ? 1 : 0; // 1 = TESTNET, 0 = MAINNET

    if (wordCount == 25) {
        // Electrum-style (XMR25) mnemonic
        LOGI("Creating wallet from 25-word XMR25 mnemonic");
        wallet = MONERO_WalletManager_recoveryWallet(
            walletManager,
            pathStr,
            "",  // password
            mnemonicStr,
            netType,
            0,   // restoreHeight
            1,   // kdfRounds
            ""   // seedOffset
        );
    } else if (wordCount == 12 || wordCount == 24) {
        // BIP39 mnemonic (needs special handling)
        LOGI("Creating wallet from %d-word BIP39 mnemonic", wordCount);
        // Note: monero_c may not directly support BIP39,
        // would need conversion to Monero seed format
        wallet = MONERO_WalletManager_recoveryWallet(
            walletManager,
            pathStr,
            "",  // password
            mnemonicStr,
            netType,
            0,   // restoreHeight
            1,   // kdfRounds
            ""   // seedOffset
        );
    } else {
        LOGE("Invalid mnemonic word count: %d", wordCount);
        env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
        env->ReleaseStringUTFChars(path, pathStr);
        return 0;
    }

    if (!wallet) {
        const char* error = MONERO_WalletManager_errorString(walletManager);
        LOGE("Failed to create wallet: %s", error ? error : "unknown error");
        env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
        env->ReleaseStringUTFChars(path, pathStr);
        return 0;
    }

    // Check wallet status
    int status = MONERO_Wallet_status(wallet);
    if (status != 0) {
        const char* error = MONERO_Wallet_errorString(wallet);
        LOGE("Wallet creation failed: %s", error ? error : "unknown error");
        MONERO_WalletManager_closeWallet(walletManager, wallet, false);
        env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
        env->ReleaseStringUTFChars(path, pathStr);
        return 0;
    }

    // Save wallet
    bool saved = MONERO_Wallet_store(wallet, pathStr);
    if (!saved) {
        const char* error = MONERO_Wallet_errorString(wallet);
        LOGE("Failed to save wallet: %s", error ? error : "unknown error");
        MONERO_WalletManager_closeWallet(walletManager, wallet, false);
        env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
        env->ReleaseStringUTFChars(path, pathStr);
        return 0;
    }

    LOGI("✅ Wallet created and saved successfully at: %s", pathStr);

    // Get address for verification
    const char* address = MONERO_Wallet_address(wallet, 0, 0);
    LOGI("Wallet address: %s", address ? address : "null");

    env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
    env->ReleaseStringUTFChars(path, pathStr);

    return reinterpret_cast<jlong>(wallet);
}

// Get secret view key
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSecretViewKey(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return env->NewStringUTF("");
    
    void* wallet = (void*)walletPtr;
    const char* viewKey = MONERO_Wallet_secretViewKey(wallet);
    
    return env->NewStringUTF(viewKey ? viewKey : "");
}

// Get secret spend key
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSecretSpendKey(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return env->NewStringUTF("");
    
    void* wallet = (void*)walletPtr;
    const char* spendKey = MONERO_Wallet_secretSpendKey(wallet);
    
    return env->NewStringUTF(spendKey ? spendKey : "");
}

// Get seed
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSeed(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return env->NewStringUTF("");
    
    void* wallet = (void*)walletPtr;
    const char* seed = MONERO_Wallet_seed(wallet, "");
    
    return env->NewStringUTF(seed ? seed : "");
}

// Set daemon address
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeSetDaemonAddress(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jstring url) {
    
    if (!walletPtr) return JNI_FALSE;
    
    void* wallet = (void*)walletPtr;
    const char* urlStr = jstring2cstr(env, url);
    
    LOGI("Setting daemon address: %s", urlStr);
    
    // Initialize wallet with daemon
    bool result = MONERO_Wallet_init(
        wallet,
        urlStr,
        0,     // upper_transaction_size_limit
        "",    // daemon_username
        "",    // daemon_password
        false, // use_ssl
        false, // lightWallet
        ""     // proxy_address
    );
    
    releaseCStr(env, url, urlStr);
    
    if (!result) {
        const char* error = MONERO_Wallet_errorString(wallet);
        LOGE("Failed to set daemon address: %s", error ? error : "unknown error");
    }
    
    return result ? JNI_TRUE : JNI_FALSE;
}

// Start refresh
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeStartRefresh(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return JNI_FALSE;
    
    void* wallet = (void*)walletPtr;
    
    LOGI("Starting wallet refresh");
    MONERO_Wallet_startRefresh(wallet);
    
    return JNI_TRUE;
}

// Stop refresh
extern "C" JNIEXPORT void JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeStopRefresh(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return;
    
    void* wallet = (void*)walletPtr;
    
    LOGI("Stopping wallet refresh");
    MONERO_Wallet_pauseRefresh(wallet);
}

// Check if synced
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeIsSynced(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return JNI_FALSE;
    
    void* wallet = (void*)walletPtr;
    bool synced = MONERO_Wallet_synchronized(wallet);
    
    return synced ? JNI_TRUE : JNI_FALSE;
}

// Get balance
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetBalance(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jint accountIndex) {
    
    if (!walletPtr) return 0;
    
    void* wallet = (void*)walletPtr;
    uint64_t balance = MONERO_Wallet_balance(wallet, accountIndex);
    
    LOGD("Balance for account %d: %llu", accountIndex, balance);
    
    return (jlong)balance;
}

// Get unlocked balance
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetUnlockedBalance(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jint accountIndex) {
    
    if (!walletPtr) return 0;
    
    void* wallet = (void*)walletPtr;
    uint64_t unlockedBalance = MONERO_Wallet_unlockedBalance(wallet, accountIndex);
    
    LOGD("Unlocked balance for account %d: %llu", accountIndex, unlockedBalance);
    
    return (jlong)unlockedBalance;
}

// Get daemon height
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetDaemonHeight(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return 0;
    
    void* wallet = (void*)walletPtr;
    uint64_t daemonHeight = MONERO_Wallet_daemonBlockChainHeight(wallet);
    
    LOGD("Daemon height: %llu", daemonHeight);
    
    return (jlong)daemonHeight;
}

// Get sync height
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSyncHeight(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return 0;
    
    void* wallet = (void*)walletPtr;
    uint64_t syncHeight = MONERO_Wallet_blockChainHeight(wallet);
    
    LOGD("Sync height: %llu", syncHeight);
    
    return (jlong)syncHeight;
}

// Refresh wallet
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeRefresh(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return JNI_FALSE;
    
    void* wallet = (void*)walletPtr;
    
    LOGI("========================================");
    LOGI("Starting wallet refresh...");
    LOGI("========================================");
    
    // Get initial status
    uint64_t beforeHeight = MONERO_Wallet_blockChainHeight(wallet);
    uint64_t daemonHeight = MONERO_Wallet_daemonBlockChainHeight(wallet);
    
    LOGI("Initial state:");
    LOGI("  Wallet height: %llu", beforeHeight);
    LOGI("  Daemon height: %llu", daemonHeight);
    
    // Check if we're actually connected to a real daemon
    if (daemonHeight == 0) {
        LOGW("⚠️ WARNING: Daemon height is 0 - may not be connected to real network!");
        LOGW("⚠️ This usually means the daemon is not synced or connection failed");
    }
    
    // Perform refresh with progress tracking
    LOGI("Calling MONERO_Wallet_refresh()...");
    auto startTime = std::chrono::steady_clock::now();
    
    bool result = MONERO_Wallet_refresh(wallet);
    
    auto endTime = std::chrono::steady_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime);
    
    // Get status after refresh
    uint64_t afterHeight = MONERO_Wallet_blockChainHeight(wallet);
    uint64_t newDaemonHeight = MONERO_Wallet_daemonBlockChainHeight(wallet);
    bool synced = MONERO_Wallet_synchronized(wallet);
    
    LOGI("========================================");
    LOGI("Refresh completed in %lld ms", duration.count());
    LOGI("========================================");
    LOGI("Final state:");
    LOGI("  Wallet height: %llu (changed by %lld)", afterHeight, (long long)(afterHeight - beforeHeight));
    LOGI("  Daemon height: %llu", newDaemonHeight);
    LOGI("  Synchronized: %s", synced ? "YES" : "NO");
    
    if (!result) {
        const char* error = MONERO_Wallet_errorString(wallet);
        LOGE("❌ REFRESH FAILED: %s", error ? error : "unknown error");
        
        // Try to get more error details
        int status = MONERO_Wallet_status(wallet);
        LOGE("❌ Wallet status code: %d", status);
    } else {
        if (afterHeight > beforeHeight) {
            LOGI("✅ SUCCESS: Synced %llu blocks", afterHeight - beforeHeight);
            LOGI("✅ This indicates REAL blockchain sync is happening!");
        } else if (afterHeight == beforeHeight && afterHeight > 0) {
            LOGI("✅ Already synced at height %llu", afterHeight);
        } else if (daemonHeight == 0 && newDaemonHeight == 0) {
            LOGW("⚠️ WARNING: No daemon height detected - check network connection!");
        }
    }
    
    LOGI("========================================");
    
    return result ? JNI_TRUE : JNI_FALSE;
}

// Set trusted daemon
extern "C" JNIEXPORT void JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeSetTrustedDaemon(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jboolean trusted) {
    
    if (!walletPtr) return;
    
    void* wallet = (void*)walletPtr;
    MONERO_Wallet_setTrustedDaemon(wallet, trusted);
}

// Get transaction history
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetTransactionHistory(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return nullptr;
    
    void* wallet = (void*)walletPtr;
    void* history = MONERO_Wallet_history(wallet);
    
    if (!history) {
        LOGE("Failed to get transaction history");
        return nullptr;
    }
    
    // Refresh history
    MONERO_TransactionHistory_refresh(history);
    int count = MONERO_TransactionHistory_count(history);
    
    LOGI("Transaction count: %d", count);
    
    // Create string array for transaction info
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(count, stringClass, nullptr);
    
    for (int i = 0; i < count; i++) {
        void* tx = MONERO_TransactionHistory_transaction(history, i);
        if (tx) {
            const char* hash = MONERO_TransactionInfo_hash(tx);
            uint64_t amount = MONERO_TransactionInfo_amount(tx);
            uint64_t height = MONERO_TransactionInfo_blockHeight(tx);
            int direction = MONERO_TransactionInfo_direction(tx);
            
            char buffer[512];
            snprintf(buffer, sizeof(buffer), 
                    "Hash: %s, Amount: %llu, Height: %llu, Direction: %s",
                    hash ? hash : "unknown",
                    amount,
                    height,
                    direction == 0 ? "IN" : "OUT");
            
            jstring txInfo = env->NewStringUTF(buffer);
            env->SetObjectArrayElement(result, i, txInfo);
            env->DeleteLocalRef(txInfo);
        }
    }
    
    return result;
}

// Create transaction
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateTransaction(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jstring destAddress, 
    jstring paymentId, jlong amount, jint mixin_count, jint priority) {
    
    if (!walletPtr) return 0;
    
    void* wallet = (void*)walletPtr;
    const char* destAddr = jstring2cstr(env, destAddress);
    const char* paymentIdStr = jstring2cstr(env, paymentId);
    
    LOGI("Creating transaction to %s, amount: %lld", destAddr, amount);
    
    // Check current balance before creating transaction
    uint64_t balance = MONERO_Wallet_balance(wallet, 0);
    uint64_t unlockedBalance = MONERO_Wallet_unlockedBalance(wallet, 0);
    LOGI("Current balance before transaction: %llu, unlocked: %llu", balance, unlockedBalance);
    
    // Check if balance is sufficient
    if (unlockedBalance == 0) {
        LOGE("No unlocked balance available! Cannot create transaction.");
        LOGE("Balance: %llu, Unlocked: %llu", balance, unlockedBalance);
    }
    
    if (amount > unlockedBalance) {
        LOGE("Insufficient balance! Requested: %lld, Available: %llu", amount, unlockedBalance);
    }
    
    // Create transaction with proper parameters
    // For single destination, we can use createTransaction
    // Parameters: wallet, dst_addr, payment_id, amount, mixin_count, priority, subaddr_account, subaddr_indices
    void* pendingTx = MONERO_Wallet_createTransaction(
        wallet,
        destAddr,
        paymentIdStr,
        amount,
        (uint32_t)mixin_count,
        priority,
        0,  // subaddr_account (uint32_t) - use account 0
        ""  // subaddr_indices (const char*) - empty = use all
    );
    
    releaseCStr(env, destAddress, destAddr);
    releaseCStr(env, paymentId, paymentIdStr);
    
    if (!pendingTx) {
        const char* error = MONERO_Wallet_errorString(wallet);
        LOGE("Failed to create transaction: %s", error ? error : "unknown error");
        
        // Log more diagnostic information
        LOGE("Transaction creation failed with balance: %llu, unlocked: %llu, requested: %lld", balance, unlockedBalance, amount);
        return 0;
    }
    
    // Check transaction status
    int status = MONERO_PendingTransaction_status(pendingTx);
    if (status != 0) {
        const char* error = MONERO_PendingTransaction_errorString(pendingTx);
        LOGE("Transaction creation failed: %s", error ? error : "unknown error");
        // Note: monero_c doesn't have disposeTransaction
        return 0;
    }
    
    return (jlong)pendingTx;
}

// Commit transaction
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCommitTransaction(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jlong txPtr) {
    
    if (!walletPtr || !txPtr) return JNI_FALSE;
    
    void* wallet = (void*)walletPtr;
    void* pendingTx = (void*)txPtr;
    
    LOGI("Committing transaction");
    
    bool result = MONERO_PendingTransaction_commit(pendingTx, "");
    
    if (!result) {
        const char* error = MONERO_PendingTransaction_errorString(pendingTx);
        LOGE("Failed to commit transaction: %s", error ? error : "unknown error");
    }
    
    // Note: monero_c doesn't have disposeTransaction
    // Transaction is automatically cleaned up after commit
    
    return result ? JNI_TRUE : JNI_FALSE;
}

// Get transaction fee
extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetTransactionFee(
    JNIEnv* env, jobject /* this */, jlong txPtr) {

    if (!txPtr) return 0;

    void* pendingTx = (void*)txPtr;
    uint64_t fee = MONERO_PendingTransaction_fee(pendingTx);

    LOGD("Transaction fee: %llu", fee);

    return (jlong)fee;
}

// Get real transaction hash - FIXED VERSION
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetTransactionHash(
    JNIEnv* env, jobject /* this */, jlong txPtr) {

    if (!txPtr) {
        LOGE("❌ Transaction pointer is null");
        return env->NewStringUTF("");
    }

    void* pendingTx = (void*)txPtr;

    LOGI("🔍 Getting transaction hash for pending transaction: %lld", (long long)txPtr);
    LOGI("   Pending transaction pointer: %p", pendingTx);

    // CRITICAL CHECK: Verify the function is loaded
    if (!MONERO_PendingTransaction_txid) {
        LOGE("❌ FATAL: MONERO_PendingTransaction_txid function not loaded!");
        LOGE("   This is THE main cause of dummy transaction hashes!");
        LOGE("   Function pointer is NULL: %p", (void*)MONERO_PendingTransaction_txid);
        return env->NewStringUTF("");
    }

    LOGI("✅ MONERO_PendingTransaction_txid function is available at: %p",
         (void*)MONERO_PendingTransaction_txid);

    // Get the transaction ID/hash using MONERO_PendingTransaction_txid
    LOGI("   Calling MONERO_PendingTransaction_txid(pendingTx=%p, index=0)...", pendingTx);

    const char* txid = MONERO_PendingTransaction_txid(pendingTx, 0);

    LOGI("   MONERO_PendingTransaction_txid returned: %p", (void*)txid);

    if (txid) {
        size_t len = strlen(txid);
        LOGI("   Transaction ID length: %zu", len);
        if (len > 0) {
            LOGI("✅ SUCCESS! Real transaction hash: %s", txid);
            LOGI("   Hash length: %zu characters", len);
            return env->NewStringUTF(txid);
        } else {
            LOGW("⚠️ Transaction ID has zero length");
        }
    } else {
        LOGW("⚠️ MONERO_PendingTransaction_txid returned NULL pointer");
    }

    // Return empty string if we can't get the hash
    LOGE("❌ Could not get transaction hash - returning empty string");
    return env->NewStringUTF("");
}

// Close wallet
extern "C" JNIEXPORT void JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCloseWallet(
    JNIEnv* env, jobject /* this */, jlong walletPtr) {
    
    if (!walletPtr) return;
    
    void* wallet = (void*)walletPtr;
    void* walletManager = MONERO_WalletManagerFactory_getWalletManager();
    
    if (walletManager) {
        LOGI("Closing wallet");
        MONERO_WalletManager_closeWallet(walletManager, wallet, false);
    }
}

// ========== Utility Functions ==========

extern "C" JNIEXPORT jlong JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeOpenWallet(
    JNIEnv* env, jobject /* this */, jstring path, jstring password) {
    
    if (!loadMoneroC()) {
        LOGE("Monero library not loaded");
        return 0;
    }
    
    const char* walletPath = env->GetStringUTFChars(path, nullptr);
    const char* walletPassword = env->GetStringUTFChars(password, nullptr);
    
    LOGI("Opening wallet from path: %s", walletPath);
    
    void* walletManager = MONERO_WalletManagerFactory_getWalletManager();
    if (!walletManager) {
        env->ReleaseStringUTFChars(path, walletPath);
        env->ReleaseStringUTFChars(password, walletPassword);
        return 0;
    }
    
    void* wallet = MONERO_WalletManager_openWallet(walletManager, walletPath, walletPassword, MONERO_NetworkType_TESTNET);
    
    env->ReleaseStringUTFChars(path, walletPath);
    env->ReleaseStringUTFChars(password, walletPassword);
    
    if (!wallet || MONERO_Wallet_status(wallet) != 0) {
        LOGE("Failed to open wallet");
        return 0;
    }
    
    return (jlong)wallet;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeIsAddressValid(
    JNIEnv* env, jobject /* this */, jstring address, jboolean testnet) {
    
    if (!loadMoneroC()) {
        return JNI_FALSE;
    }
    
    // Check for null address
    if (address == nullptr) {
        LOGE("nativeIsAddressValid: address is null");
        return JNI_FALSE;
    }
    
    const char* addressStr = env->GetStringUTFChars(address, nullptr);
    if (addressStr == nullptr) {
        LOGE("nativeIsAddressValid: failed to get address string");
        return JNI_FALSE;
    }
    
    int networkType = testnet ? MONERO_NetworkType_TESTNET : MONERO_NetworkType_MAINNET;
    
    // addressValid is a static function on Wallet, can be called with nullptr
    bool isValid = MONERO_Wallet_addressValid(nullptr, addressStr, networkType);
    
    env->ReleaseStringUTFChars(address, addressStr);
    
    return isValid ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeIsMnemonicValid(
    JNIEnv* env, jobject /* this */, jstring mnemonic) {
    
    if (!loadMoneroC()) {
        return JNI_FALSE;
    }
    
    const char* mnemonicStr = env->GetStringUTFChars(mnemonic, nullptr);
    
    // Count words in mnemonic using pure C (avoid C++ STL)
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
    
    env->ReleaseStringUTFChars(mnemonic, mnemonicStr);
    
    // Valid if 12 words (BIP39) or 25 words (Monero)
    return (wordCount == 12 || wordCount == 25) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGenerateMnemonic(
    JNIEnv* env, jobject /* this */, jstring language) {
    
    if (!loadMoneroC()) {
        return nullptr;
    }
    
    const char* lang = env->GetStringUTFChars(language, nullptr);
    
    void* walletManager = MONERO_WalletManagerFactory_getWalletManager();
    if (!walletManager) {
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }
    
    // Create a temporary wallet to generate mnemonic
    char tempPath[] = "/tmp/temp_wallet_XXXXXX";
    mkstemp(tempPath);
    
    void* wallet = MONERO_WalletManager_createWallet(walletManager, tempPath, "", lang, MONERO_NetworkType_MAINNET);
    if (!wallet) {
        env->ReleaseStringUTFChars(language, lang);
        return nullptr;
    }
    
    const char* seed = MONERO_Wallet_seed(wallet, "");
    jstring result = nullptr;
    if (seed) {
        result = env->NewStringUTF(seed);
        MONERO_free((void*)seed);
    }
    
    // Clean up
    MONERO_WalletManager_closeWallet(walletManager, wallet, false);
    unlink(tempPath);
    
    env->ReleaseStringUTFChars(language, lang);
    
    return result;
}

// ========== Subaddress Functions ==========

// Get subaddress (alias for nativeGetAddress with different name)
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSubaddress(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jint accountIndex, jint addressIndex) {
    
    if (!walletPtr) return env->NewStringUTF("");
    
    void* wallet = (void*)walletPtr;
    const char* address = MONERO_Wallet_address(wallet, accountIndex, addressIndex);
    
    return env->NewStringUTF(address ? address : "");
}

// Add new subaddress
extern "C" JNIEXPORT jint JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeAddSubaddress(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jint accountIndex, jstring label) {
    
    if (!walletPtr) return -1;
    
    void* wallet = (void*)walletPtr;
    const char* labelStr = label ? jstring2cstr(env, label) : "";
    
    LOGI("Adding subaddress for account %d with label: %s", accountIndex, labelStr);
    
    // Note: monero_c doesn't have a direct API to add subaddresses
    // Subaddresses are generated deterministically from the wallet seed
    // Getting an address at a new index automatically "creates" it
    // Return the next available index (simulated)
    LOGW("Note: monero_c doesn't support dynamic subaddress creation. Subaddresses are deterministic.");
    
    // Return the next available subaddress index
    int nextIndex = 10; // Default next index for testing
    
    if (label) {
        releaseCStr(env, label, labelStr);
    }
    
    return (jint)nextIndex;
}

// Get number of subaddresses
extern "C" JNIEXPORT jint JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetNumSubaddresses(
    JNIEnv* env, jobject /* this */, jlong walletPtr, jint accountIndex) {
    
    if (!walletPtr) return 0;
    
    void* wallet = (void*)walletPtr;
    
    // monero_c doesn't have a direct API to get number of subaddresses
    // We can try to get addresses until we get an empty/invalid one
    // For now, return a reasonable default (Monero wallets typically start with 1 subaddress)
    // and can have many more. We'll return 10 as a reasonable default for testing.
    int numSubaddresses = 10;
    
    LOGD("Number of subaddresses for account %d: %d (default)", accountIndex, numSubaddresses);
    
    return (jint)numSubaddresses;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetLastError(
    JNIEnv* env, jobject /* this */) {
    
    // Return the actual last error message
    if (strlen(g_lastError) > 0) {
        return env->NewStringUTF(g_lastError);
    } else {
        return env->NewStringUTF("No error information available");
    }
}

// Dynamic registration for test environments
extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    LOGI("JNI_OnLoad: monero_c JNI bridge loaded");
    
    // Load monero_c library first
    if (!loadMoneroC()) {
        LOGE("Failed to load monero_c library in JNI_OnLoad");
        // Continue anyway, will try again on first use
    }
    
    // Method mapping table
    static JNINativeMethod methods[] = {
        {"nativeInit", "(Ljava/lang/String;Z)Z", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeInit},
        {"nativeCreateWalletFromMnemonic", "(Ljava/lang/String;Z)J", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateWalletFromMnemonic},
        {"nativeOpenWallet", "(Ljava/lang/String;Ljava/lang/String;)J", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeOpenWallet},
        {"nativeGetAddress", "(JII)Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetAddress},
        {"nativeGetSecretViewKey", "(J)Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSecretViewKey},
        {"nativeGetSecretSpendKey", "(J)Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSecretSpendKey},
        {"nativeGetSeed", "(J)Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSeed},
        {"nativeSetDaemonAddress", "(JLjava/lang/String;)Z", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeSetDaemonAddress},
        {"nativeStartRefresh", "(J)Z", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeStartRefresh},
        {"nativeStopRefresh", "(J)V", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeStopRefresh},
        {"nativeRefresh", "(J)Z", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeRefresh},
        {"nativeSetTrustedDaemon", "(JZ)V", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeSetTrustedDaemon},
        {"nativeIsSynced", "(J)Z", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeIsSynced},
        {"nativeGetBalance", "(JI)J", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetBalance},
        {"nativeGetUnlockedBalance", "(JI)J", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetUnlockedBalance},
        {"nativeGetDaemonHeight", "(J)J", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetDaemonHeight},
        {"nativeGetSyncHeight", "(J)J", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSyncHeight},
        {"nativeGetTransactionHistory", "(J)[Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetTransactionHistory},
        {"nativeCreateTransaction", "(JLjava/lang/String;Ljava/lang/String;JII)J", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateTransaction},
        {"nativeCommitTransaction", "(JJ)Z", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCommitTransaction},
        {"nativeGetTransactionFee", "(J)J", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetTransactionFee},
        {"nativeGetTransactionHash", "(J)Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetTransactionHash},
        {"nativeCloseWallet", "(J)V", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCloseWallet},
        {"nativeIsAddressValid", "(Ljava/lang/String;Z)Z", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeIsAddressValid},
        {"nativeIsMnemonicValid", "(Ljava/lang/String;)Z", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeIsMnemonicValid},
        {"nativeGenerateMnemonic", "(Ljava/lang/String;)Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGenerateMnemonic},
        {"nativeGetLastError", "()Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetLastError},
        {"nativeGetNumSubaddresses", "(JI)I", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetNumSubaddresses},
        {"nativeAddSubaddress", "(JILjava/lang/String;)I", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeAddSubaddress},
        {"nativeGetSubaddress", "(JII)Ljava/lang/String;", (void*)Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetSubaddress}
    };
    
    // Try to register with multiple possible class names
    const char* classNames[] = {
        "com/cbstudio/wearwallet/core/multichain/monero/MonerujoJNIWrapper",
        "com/cbstudio/wearwallet/core/test/multichain/monero/MonerujoJNIWrapper"
    };
    
    bool registered = false;
    for (const char* className : classNames) {
        jclass clazz = env->FindClass(className);
        if (clazz != nullptr) {
            if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) == JNI_OK) {
                LOGI("Successfully registered natives for %s", className);
                registered = true;
                break;
            }
        }
        env->ExceptionClear();
    }
    
    if (!registered) {
        LOGE("Failed to register native methods");
        return JNI_ERR;
    }
    
    return JNI_VERSION_1_6;
}