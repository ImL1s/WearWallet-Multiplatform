package com.cbstudio.wearwallet.utils

import android.util.Log
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import timber.log.Timber

/**
 * Timber tree for production logging with Firebase Crashlytics
 */
class CrashReportingTree : Timber.Tree() {
    private val crashlytics = Firebase.crashlytics
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only log WARN, ERROR and WTF to Crashlytics
        if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
            return
        }
        
        // Log the message to Crashlytics
        crashlytics.log("$tag: $message")
        
        // If there's an exception, record it
        if (t != null) {
            crashlytics.recordException(t)
        } else if (priority == Log.ERROR || priority == Log.ASSERT) {
            // For ERROR or WTF logs without exceptions, create a synthetic exception
            // to get a stack trace in Crashlytics
            crashlytics.recordException(Exception(message))
        }
    }
}
