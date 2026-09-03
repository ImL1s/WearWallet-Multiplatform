package com.cbstudio.wearwallet.core.security

class WatchOSPlatformProvider : PlatformProvider {
    override val currentPlatform: Platform = Platform.WATCH_OS
}

class WatchOSBuildTypeProvider(
    private val explicitBuildType: BuildType = BuildType.RELEASE
) : BuildTypeProvider {
    override val currentBuildType: BuildType = explicitBuildType
}
