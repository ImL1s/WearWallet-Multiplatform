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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Swap Pair Card Component
 * 
 * Shows the From -> To token pair in a compact card format
 */
@Composable
fun SwapPairCard(
    fromSymbol: String,
    fromChain: String,
    fromImage: String?,
    toSymbol: String,
    toChain: String,
    toImage: String?,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // From Token
        TokenSelector(
            symbol = fromSymbol,
            chain = fromChain,
            imageUrl = fromImage,
            label = "From",
            onClick = onFromClick,
            modifier = Modifier.weight(1f)
        )
        
        // Arrow
        Text(
            text = "→",
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // To Token
        TokenSelector(
            symbol = toSymbol,
            chain = toChain,
            imageUrl = toImage,
            label = "To",
            onClick = onToClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TokenSelector(
    symbol: String,
    chain: String,
    imageUrl: String?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        TokenIcon(
            symbol = symbol,
            imageUrl = imageUrl,
            size = 32.dp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
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

/**
 * Swap Arrow Icon - Animated swap direction indicator
 */
@Composable
fun SwapArrow(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "↔",
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.primary
        )
    }
}
