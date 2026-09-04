package com.cbstudio.wearwallet.feature

/**
 * Product-capability maturity. Wear release navigation only registers
 * PRODUCTION / BETA / EXPERIMENTAL routes. MAINTENANCE, DEMO, and
 * UNSUPPORTED stay out of the release graph (debug may still install them).
 *
 * Human-readable copy: `docs/FEATURE_STATUS.md`. Keep that file and this
 * enum in lockstep — [ReleaseFeatureGateTest] compares them.
 */
enum class FeatureMaturity {
    PRODUCTION,
    BETA,
    EXPERIMENTAL,
    MAINTENANCE,
    DEMO,
    UNSUPPORTED,
}
