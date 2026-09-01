package com.cbstudio.wearwallet.presentation.wallet.screens.swap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Token Icon Component for Wear OS
 * 
 * Displays token logo from URL with fallback to first letter
 */
@Composable
fun TokenIcon(
    symbol: String,
    imageUrl: String?,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(getTokenColor(symbol)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = symbol,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            // Fallback to first letter
            Text(
                text = symbol.take(1).uppercase(),
                style = if (size >= 32.dp) {
                    MaterialTheme.typography.title3
                } else {
                    MaterialTheme.typography.caption1
                },
                color = Color.White
            )
        }
    }
}

/**
 * Get a consistent color for each token based on symbol
 */
private fun getTokenColor(symbol: String): Color {
    return when (symbol.uppercase()) {
        "BNB" -> Color(0xFFF0B90B)  // Binance Yellow
        "ETH" -> Color(0xFF627EEA)  // Ethereum Blue
        "BTC" -> Color(0xFFF7931A)  // Bitcoin Orange
        "USDT" -> Color(0xFF26A17B) // Tether Green
        "USDC" -> Color(0xFF2775CA) // USDC Blue
        "MATIC", "POL" -> Color(0xFF8247E5) // Polygon Purple
        "SOL" -> Color(0xFF00FFA3)  // Solana Green
        "AVAX" -> Color(0xFFE84142) // Avalanche Red
        "DAI" -> Color(0xFFF5AC37)  // DAI Gold
        "WETH" -> Color(0xFFEC1C79) // WETH Pink
        else -> Color(0xFF6B7280)   // Default Gray
    }
}

/**
 * Chain Icon Component
 */
@Composable
fun ChainIcon(
    chainName: String,
    logoUrl: String?,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(getChainColor(chainName)),
        contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = chainName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = chainName.take(1).uppercase(),
                style = MaterialTheme.typography.caption2,
                color = Color.White
            )
        }
    }
}

private fun getChainColor(chain: String): Color {
    return when (chain.uppercase()) {
        "BSC" -> Color(0xFFF0B90B)
        "ETH", "ETHEREUM" -> Color(0xFF627EEA)
        "POLYGON" -> Color(0xFF8247E5)
        "ARBITRUM" -> Color(0xFF28A0F0)
        "OPTIMISM" -> Color(0xFFFF0420)
        "BASE" -> Color(0xFF0052FF)
        "AVAX_CCHAIN", "AVALANCHE" -> Color(0xFFE84142)
        else -> Color(0xFF6B7280)
    }
}
