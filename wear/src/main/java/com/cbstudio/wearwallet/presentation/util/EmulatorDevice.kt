package com.cbstudio.wearwallet.presentation.util

import android.os.Build

/** Detect AVD / emulator images (root heuristics and adb input behave differently). */
fun isEmulatorDevice(): Boolean {
    return isEmulatorSignals(
        fingerprint = Build.FINGERPRINT.orEmpty(),
        model = Build.MODEL.orEmpty(),
        manufacturer = Build.MANUFACTURER.orEmpty(),
        hardware = Build.HARDWARE.orEmpty()
    )
}

/**
 * Physical GSI images can start with `generic` without being an emulator.
 * Wear AVDs are identified by ranchu/goldfish, sdk_gwear, or explicit emulator markers.
 */
internal fun isEmulatorSignals(
    fingerprint: String,
    model: String,
    manufacturer: String,
    hardware: String
): Boolean {
    val fp = fingerprint.lowercase()
    val md = model.lowercase()
    val mf = manufacturer.lowercase()
    val hw = hardware.lowercase()
    return hw.contains("goldfish") ||
        hw.contains("ranchu") ||
        fp.contains("emulator") ||
        md.contains("emulator") ||
        md.contains("android sdk built for") ||
        mf.contains("genymotion") ||
        md.contains("sdk_gwear") ||
        fp.contains("sdk_gwear")
}
