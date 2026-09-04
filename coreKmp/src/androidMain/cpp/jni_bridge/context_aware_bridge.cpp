/**
 * Context-Aware Monero JNI Bridge
 *
 * This bridge extension adds support for Context-driven file path resolution
 * to solve Android test environment file permission issues.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "ContextAwareBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Forward declaration of the main nativeInit function from monero_c_jni_bridge.cpp
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeInit(
    JNIEnv* env, jobject thiz, jstring dataDir, jboolean testnet);

// Helper function to convert jstring to std::string
std::string jstring2string(JNIEnv* env, jstring jStr) {
    if (jStr == nullptr) return "";

    const char* chars = env->GetStringUTFChars(jStr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jStr, chars);
    return result;
}

// Helper function to get Context.getFilesDir() path
extern "C" JNIEXPORT jstring JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetContextFilesDir(
    JNIEnv* env, jobject /* this */, jobject context) {

    if (context == nullptr) {
        LOGE("Context is null");
        return nullptr;
    }

    // Get Context class and getFilesDir method
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getFilesDirMethod = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");

    if (getFilesDirMethod == nullptr) {
        LOGE("Could not find getFilesDir method");
        return nullptr;
    }

    // Call getFilesDir()
    jobject fileObj = env->CallObjectMethod(context, getFilesDirMethod);
    if (fileObj == nullptr) {
        LOGE("getFilesDir returned null");
        return nullptr;
    }

    // Get File.getAbsolutePath()
    jclass fileClass = env->GetObjectClass(fileObj);
    jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");

    if (getAbsolutePathMethod == nullptr) {
        LOGE("Could not find getAbsolutePath method");
        return nullptr;
    }

    jstring pathString = (jstring)env->CallObjectMethod(fileObj, getAbsolutePathMethod);

    // Log the path for debugging
    std::string path = jstring2string(env, pathString);
    LOGI("Context filesDir path: %s", path.c_str());

    return pathString;
}

// Helper function to create directory using Java File.mkdirs()
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateDirectory(
    JNIEnv* env, jobject /* this */, jstring directoryPath) {

    if (directoryPath == nullptr) {
        LOGE("Directory path is null");
        return JNI_FALSE;
    }

    std::string path = jstring2string(env, directoryPath);
    LOGI("Creating directory: %s", path.c_str());

    // Create File object
    jclass fileClass = env->FindClass("java/io/File");
    jmethodID fileConstructor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
    jobject fileObj = env->NewObject(fileClass, fileConstructor, directoryPath);

    // Check if directory exists
    jmethodID existsMethod = env->GetMethodID(fileClass, "exists", "()Z");
    jboolean exists = env->CallBooleanMethod(fileObj, existsMethod);

    if (exists) {
        LOGI("Directory already exists: %s", path.c_str());
        return JNI_TRUE;
    }

    // Create directories
    jmethodID mkdirsMethod = env->GetMethodID(fileClass, "mkdirs", "()Z");
    jboolean created = env->CallBooleanMethod(fileObj, mkdirsMethod);

    if (created) {
        LOGI("Directory created successfully: %s", path.c_str());
    } else {
        LOGE("Failed to create directory: %s", path.c_str());
    }

    return created;
}

// Helper function to test file write permissions
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeTestWritePermission(
    JNIEnv* env, jobject /* this */, jstring directoryPath) {

    if (directoryPath == nullptr) {
        LOGE("Directory path is null");
        return JNI_FALSE;
    }

    std::string path = jstring2string(env, directoryPath);
    LOGI("Testing write permission for: %s", path.c_str());

    // Create test file path
    std::string testFilePath = path + "/test_write_permission.tmp";
    jstring testFilePathStr = env->NewStringUTF(testFilePath.c_str());

    // Create File object for test file
    jclass fileClass = env->FindClass("java/io/File");
    jmethodID fileConstructor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
    jobject testFileObj = env->NewObject(fileClass, fileConstructor, testFilePathStr);

    try {
        // Try to create the file
        jmethodID createNewFileMethod = env->GetMethodID(fileClass, "createNewFile", "()Z");
        jboolean created = env->CallBooleanMethod(testFileObj, createNewFileMethod);

        if (!created) {
            LOGE("Failed to create test file: %s", testFilePath.c_str());
            return JNI_FALSE;
        }

        // Check if file can be written to
        jmethodID canWriteMethod = env->GetMethodID(fileClass, "canWrite", "()Z");
        jboolean canWrite = env->CallBooleanMethod(testFileObj, canWriteMethod);

        // Clean up test file
        jmethodID deleteMethod = env->GetMethodID(fileClass, "delete", "()Z");
        env->CallBooleanMethod(testFileObj, deleteMethod);

        if (canWrite) {
            LOGI("Write permission test passed for: %s", path.c_str());
        } else {
            LOGE("Write permission test failed for: %s", path.c_str());
        }

        return canWrite;

    } catch (...) {
        LOGE("Exception during write permission test for: %s", path.c_str());
        return JNI_FALSE;
    }
}

// Enhanced initialization with Context validation
extern "C" JNIEXPORT jboolean JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeInitWithContextValidation(
    JNIEnv* env, jobject thiz, jobject context, jboolean testnet) {

    LOGI("Starting Context-aware Monero initialization");

    if (context == nullptr) {
        LOGE("Context is null - cannot proceed");
        return JNI_FALSE;
    }

    // Get the files directory from Context
    jstring filesDirPath = Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetContextFilesDir(env, thiz, context);
    if (filesDirPath == nullptr) {
        LOGE("Failed to get Context files directory");
        return JNI_FALSE;
    }

    std::string filesDir = jstring2string(env, filesDirPath);

    // Create Monero subdirectory
    std::string moneroDir = filesDir + "/monero_wallets";
    jstring moneroDirStr = env->NewStringUTF(moneroDir.c_str());

    // Create the directory
    jboolean dirCreated = Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeCreateDirectory(env, thiz, moneroDirStr);
    if (!dirCreated) {
        LOGE("Failed to create Monero directory: %s", moneroDir.c_str());
        return JNI_FALSE;
    }

    // Test write permissions
    jboolean canWrite = Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeTestWritePermission(env, thiz, moneroDirStr);
    if (!canWrite) {
        LOGE("No write permission for Monero directory: %s", moneroDir.c_str());
        return JNI_FALSE;
    }

    LOGI("Context validation complete - proceeding with standard Monero init");
    LOGI("Using Monero directory: %s", moneroDir.c_str());

    // Now call the standard nativeInit with the validated path
    return Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeInit(env, thiz, moneroDirStr, testnet);
}

// Function to get all available storage paths from Context
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_cbstudio_wearwallet_core_multichain_monero_MonerujoJNIWrapper_nativeGetAllStoragePaths(
    JNIEnv* env, jobject /* this */, jobject context) {

    if (context == nullptr) {
        LOGE("Context is null");
        return nullptr;
    }

    std::vector<std::string> paths;
    jclass contextClass = env->GetObjectClass(context);

    // Get filesDir
    jmethodID getFilesDirMethod = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");
    if (getFilesDirMethod != nullptr) {
        jobject fileObj = env->CallObjectMethod(context, getFilesDirMethod);
        if (fileObj != nullptr) {
            jclass fileClass = env->GetObjectClass(fileObj);
            jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
            jstring pathString = (jstring)env->CallObjectMethod(fileObj, getAbsolutePathMethod);
            paths.push_back("filesDir:" + jstring2string(env, pathString));
        }
    }

    // Get cacheDir
    jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
    if (getCacheDirMethod != nullptr) {
        jobject cacheObj = env->CallObjectMethod(context, getCacheDirMethod);
        if (cacheObj != nullptr) {
            jclass fileClass = env->GetObjectClass(cacheObj);
            jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
            jstring pathString = (jstring)env->CallObjectMethod(cacheObj, getAbsolutePathMethod);
            paths.push_back("cacheDir:" + jstring2string(env, pathString));
        }
    }

    // Get dataDir (API 24+)
    jmethodID getDataDirMethod = env->GetMethodID(contextClass, "getDataDir", "()Ljava/io/File;");
    if (getDataDirMethod != nullptr) {
        jobject dataObj = env->CallObjectMethod(context, getDataDirMethod);
        if (dataObj != nullptr) {
            jclass fileClass = env->GetObjectClass(dataObj);
            jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
            jstring pathString = (jstring)env->CallObjectMethod(dataObj, getAbsolutePathMethod);
            paths.push_back("dataDir:" + jstring2string(env, pathString));
        }
    }

    // Convert to Java String array
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(paths.size(), stringClass, nullptr);

    for (size_t i = 0; i < paths.size(); i++) {
        jstring jstr = env->NewStringUTF(paths[i].c_str());
        env->SetObjectArrayElement(result, i, jstr);
    }

    LOGI("Found %zu storage paths", paths.size());
    return result;
}