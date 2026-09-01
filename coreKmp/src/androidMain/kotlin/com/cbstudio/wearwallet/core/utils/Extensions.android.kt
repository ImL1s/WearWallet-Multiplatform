package com.cbstudio.wearwallet.core.utils

actual fun String.formatNative(vararg args: Any?): String {
    return java.lang.String.format(this, *args)
}