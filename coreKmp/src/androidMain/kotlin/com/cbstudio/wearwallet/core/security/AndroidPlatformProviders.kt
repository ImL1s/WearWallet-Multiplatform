package com.cbstudio.wearwallet.core.security

import android.content.Context
import android.content.pm.PackageManager

class AndroidPlatformProvider(
    private val context: Context? = null,
    private val explicitPlatform: Platform? = null
) : PlatformProvider {
    override val currentPlatform: Platform
        get() {
            if (explicitPlatform != null) return explicitPlatform
            val hasWatchFeature = context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_WATCH) ?: true
            return if (hasWatchFeature) Platform.WEAR_OS else Platform.ANDROID_PHONE
        }
}

class AndroidBuildTypeProvider(
    private val buildTypeString: String? = null,
    private val isDebug: Boolean = false
) : BuildTypeProvider {
    override val currentBuildType: BuildType
        get() = when {
            buildTypeString != null -> BuildType.fromString(buildTypeString)
            isDebug -> BuildType.DEBUG
            else -> BuildType.RELEASE
        }
}
