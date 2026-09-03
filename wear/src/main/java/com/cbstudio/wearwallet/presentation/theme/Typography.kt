package com.cbstudio.wearwallet.presentation.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Typography

/**
 * WearWallet Typography optimized for Wear OS Material 3 Expressive
 *
 * - Optimized font sizes for circular watch screens
 * - Enhanced readability in dark environment
 * - Minimum 12sp everywhere for glanceable legibility
 * - Battery-efficient rendering
 */

val WearWalletDisplayLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = 0.sp
)

val WearWalletDisplayMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = 0.sp
)

val WearWalletDisplaySmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = 0.sp
)

val WearWalletTitleLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.sp
)

val WearWalletTitleMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.1.sp
)

val WearWalletTitleSmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.1.sp
)

val WearWalletBodyLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.25.sp
)

val WearWalletBodyMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp
)

val WearWalletBodySmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)

// Floor at 12sp: the Wear M3 default bodyExtraSmall is below the legibility minimum.
val WearWalletBodyExtraSmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)

val WearWalletLabelLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.3.sp
)

val WearWalletLabelMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

// Floor at 12sp: the Wear M3 default labelSmall is below the legibility minimum.
val WearWalletLabelSmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

/**
 * Full Wear M3 Typography wired into [WearWalletTheme] via MaterialTheme.
 * Arc and numeral slots keep library defaults (all above 12sp).
 */
val WearWalletTypography = Typography(
    displayLarge = WearWalletDisplayLarge,
    displayMedium = WearWalletDisplayMedium,
    displaySmall = WearWalletDisplaySmall,
    titleLarge = WearWalletTitleLarge,
    titleMedium = WearWalletTitleMedium,
    titleSmall = WearWalletTitleSmall,
    bodyLarge = WearWalletBodyLarge,
    bodyMedium = WearWalletBodyMedium,
    bodySmall = WearWalletBodySmall,
    bodyExtraSmall = WearWalletBodyExtraSmall,
    labelLarge = WearWalletLabelLarge,
    labelMedium = WearWalletLabelMedium,
    labelSmall = WearWalletLabelSmall
)
