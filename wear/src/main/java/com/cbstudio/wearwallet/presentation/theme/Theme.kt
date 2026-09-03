package com.cbstudio.wearwallet.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * WearWallet Material 3 Expressive Theme
 *
 * Features:
 * - Material 3 Expressive design system with improved performance
 * - Optimized typography for Wear OS circular screens (min 12sp)
 * - Enhanced accessibility and usability
 * - Compatible with Wear OS 6 dynamic theming when available
 */

@Composable
fun WearWalletTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = MetallicBlue,
            onPrimary = Color.White,
            primaryContainer = Purple40,
            onPrimaryContainer = Purple80,
            secondary = PurpleGrey40,
            onSecondary = Color.White,
            secondaryContainer = PurpleGrey80,
            onSecondaryContainer = PurpleGrey40,
            tertiary = Pink40,
            onTertiary = Color.White,
            tertiaryContainer = Pink80,
            onTertiaryContainer = Pink40,
            background = MetallicDark,
            onBackground = Color.White,
            surfaceContainer = PremiumGradientStart,
            onSurface = Color.White,
            onSurfaceVariant = Color.LightGray,
            error = ErrorRed,
            onError = Color.White
        ),
        typography = WearWalletTypography,
        content = content
    )
}
