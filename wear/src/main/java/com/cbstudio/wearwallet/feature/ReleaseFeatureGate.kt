package com.cbstudio.wearwallet.feature

import com.cbstudio.wearwallet.BuildConfig

/**
 * Compile-time-ish release navigation gate. A remote flag may further
 * disable a capability, but must not enable MAINTENANCE / DEMO /
 * UNSUPPORTED routes in a release binary.
 */
object ReleaseFeatureGate {

    fun isReleaseBuild(debug: Boolean = BuildConfig.DEBUG): Boolean = !debug

    fun isReachableInRelease(capability: WearCapability): Boolean =
        when (capability.maturity) {
            FeatureMaturity.PRODUCTION,
            FeatureMaturity.BETA,
            FeatureMaturity.EXPERIMENTAL -> true
            FeatureMaturity.MAINTENANCE,
            FeatureMaturity.DEMO,
            FeatureMaturity.UNSUPPORTED -> false
        }

    fun allowsRoute(routePattern: String, isRelease: Boolean): Boolean {
        if (!isRelease) return true
        val base = routeBase(routePattern)
        val owners = WearCapability.entries.filter { capability ->
            capability.routes.any { routeBase(it) == base }
        }
        if (owners.isEmpty()) return true
        return owners.all(::isReachableInRelease)
    }

    fun registeredRoutes(isRelease: Boolean): Set<String> =
        WearCapability.entries
            .filter { !isRelease || isReachableInRelease(it) }
            .flatMap { it.routes }
            .toSet()

    private fun routeBase(routePattern: String): String =
        routePattern.substringBefore('?').substringBefore("/{")
}
