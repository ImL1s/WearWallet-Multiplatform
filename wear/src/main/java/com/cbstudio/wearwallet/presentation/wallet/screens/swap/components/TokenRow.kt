package com.cbstudio.wearwallet.presentation.wallet.screens.swap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.cbstudio.wearwallet.core.rango.model.RangoTokenMeta

/**
 * Token Row Component for Wear OS
 * 
 * Displays a token with icon, symbol, chain, and price
 * Optimized for round watch displays
 */
@Composable
fun TokenRow(
    token: RangoTokenMeta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPrice: Boolean = true,
    showChain: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.15f)) // Darker/More subtle bg
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Token Icon
        TokenIcon(
            symbol = token.symbol,
            imageUrl = token.image,
            size = 36.dp
        )
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Token Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = token.symbol,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (showChain) {
                Text(
                    text = token.blockchain,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // Price
        if (showPrice && token.usdPrice != null) {
            Text(
                text = formatPrice(token.usdPrice),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary
            )
        }
    }
}

/**
 * Compact Token Row for smaller spaces
 */
@Composable
fun TokenRowCompact(
    symbol: String,
    chain: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TokenIcon(
            symbol = symbol,
            imageUrl = imageUrl,
            size = 24.dp
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        Column {
            Text(
                text = symbol,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurface
            )
            Text(
                text = chain,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Format USD price for display
 */
private fun formatPrice(price: Double?): String {
    if (price == null) return ""
    return when {
        price >= 1000 -> "$${String.format("%.0f", price)}"
        price >= 1 -> "$${String.format("%.2f", price)}"
        price >= 0.01 -> "$${String.format("%.4f", price)}"
        else -> "$${String.format("%.6f", price)}"
    }
}
