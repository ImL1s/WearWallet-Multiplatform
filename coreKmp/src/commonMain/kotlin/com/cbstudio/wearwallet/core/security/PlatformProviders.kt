package com.cbstudio.wearwallet.core.security

interface PlatformProvider {
    val currentPlatform: Platform
}

interface BuildTypeProvider {
    val currentBuildType: BuildType
}

class TestPlatformProvider(
    override val currentPlatform: Platform = Platform.WEAR_OS
) : PlatformProvider

class TestBuildTypeProvider(
    override val currentBuildType: BuildType = BuildType.RELEASE
) : BuildTypeProvider
