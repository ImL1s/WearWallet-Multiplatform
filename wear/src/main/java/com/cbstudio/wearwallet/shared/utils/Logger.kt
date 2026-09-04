package com.cbstudio.wearwallet.shared.utils

import timber.log.Timber

/**
 * Lightweight logger shim for Wear module.
 * Routes logs to Timber while matching simple Logger API used in codebase.
 */
object Logger {
    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    fun w(tag: String, message: String) {
        Timber.tag(tag).w(message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).w(throwable, message)
    }

    fun e(tag: String, message: String) {
        Timber.tag(tag).e(message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).e(throwable, message)
    }
}
