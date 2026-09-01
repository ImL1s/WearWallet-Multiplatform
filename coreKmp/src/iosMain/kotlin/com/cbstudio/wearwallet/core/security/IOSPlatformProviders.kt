package com.cbstudio.wearwallet.core.security

class IOSPlatformProvider : PlatformProvider {
    override val currentPlatform: Platform = Platform.IOS
}

class IOSBuildTypeProvider(
    private val explicitBuildType: BuildType = BuildType.RELEASE
) : BuildTypeProvider {
    override val currentBuildType: BuildType = explicitBuildType
}
