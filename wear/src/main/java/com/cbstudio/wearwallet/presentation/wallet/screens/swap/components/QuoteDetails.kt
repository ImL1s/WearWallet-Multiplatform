package com.cbstudio.wearwallet.presentation.wallet.screens.swap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.cbstudio.wearwallet.core.rango.model.RangoQuoteResponse
import androidx.compose.ui.platform.testTag

/**
 * Quote Details Component
 * 
 * Displays swap quote information in a compact format for Wear OS
 */
@Composable
fun QuoteDetails(
    quote: RangoQuoteResponse,
    fromSymbol: String,
    toSymbol: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Output Amount (Highlights)
        quote.route?.let { route ->
            val outputAmount = formatAmount(route.outputAmount, 6)
            
            Text(
                text = "Receive",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )

            Text(
                text = "$outputAmount $toSymbol",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.secondary, // Use secondary for better visibility
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Details Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Fee
                val feeValue = route.feeUsd?.let { "$${String.format("%.2f", it)}" } ?: "-"
                QuoteDetailItem(
                    label = "Est. Fee",
                    value = feeValue
                )
                
                // Time
                val timeValue = route.estimatedTimeInSeconds?.let { "~${it}s" } ?: "-"
                QuoteDetailItem(
                    label = "Est. Time",
                    value = timeValue
                )
            }
        }
    }
}

@Composable
private fun QuoteDetailItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface
        )
    }
}

/**
 * Loading Quote Placeholder
 */
@Composable
fun QuoteLoading(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Getting quote...",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * Quote Error Display
 */
@Composable
fun QuoteError(
    error: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colors.error.copy(alpha = 0.2f))
            .padding(12.dp)
            .testTag(com.cbstudio.wearwallet.presentation.TestTags.ERROR_MESSAGE),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.error,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Format amount for display - removes trailing zeros
 */
private fun formatAmount(amount: String?, maxDecimals: Int = 6): String {
    if (amount.isNullOrEmpty()) return "0"
    
    return try {
        val value = amount.toBigDecimal()
        val formatted = value.setScale(maxDecimals, java.math.RoundingMode.DOWN)
        formatted.stripTrailingZeros().toPlainString()
    } catch (e: Exception) {
        amount
    }
}
