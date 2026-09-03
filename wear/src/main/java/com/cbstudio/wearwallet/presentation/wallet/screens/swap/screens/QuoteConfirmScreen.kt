package com.cbstudio.wearwallet.presentation.wallet.screens.swap.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.SwapStatus
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.SwapUiState
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.components.QuoteDetails
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.components.QuoteError
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.components.QuoteLoading
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.components.TokenIcon
import androidx.compose.ui.platform.testTag

/**
 * Quote Confirmation Screen for Wear OS
 * 
 * Displays the swap quote and allows user to confirm
 */
@Composable
fun QuoteConfirmScreen(
    uiState: SwapUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val fromToken = uiState.fromToken
    val toToken = uiState.toToken
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 24.dp, // Reduced top padding
            bottom = 8.dp,
            start = 8.dp,
            end = 8.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header (Compact)
        item {
            Text(
                text = "Confirm Swap",
                style = MaterialTheme.typography.title3, // Smaller title
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )
        }
        
        // Compact From -> To Visualization
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                // From
                TokenIcon(
                    symbol = fromToken?.symbol ?: "",
                    imageUrl = fromToken?.image,
                    size = 20.dp // Smaller icon
                )
                Text(
                    text = " ${uiState.amount} ${fromToken?.symbol}",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface
                )
                
                // Arrow
                Text(
                    text = " → ",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                
                // To
                TokenIcon(
                    symbol = toToken?.symbol ?: "",
                    imageUrl = toToken?.image,
                    size = 20.dp // Smaller icon
                )
                Text(
                    text = " ${toToken?.symbol}",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface
                )
            }
        }
        
        // Quote details
        item {
            when (uiState.status) {
                SwapStatus.GETTING_QUOTE -> {
                    QuoteLoading(modifier = Modifier.height(60.dp)) // Compact loading
                }
                SwapStatus.FAILED -> {
                    QuoteError(error = uiState.error ?: "Unknown error")
                }
                SwapStatus.QUOTE_READY -> {
                    uiState.quote?.let { quote ->
                        QuoteDetails(
                            quote = quote,
                            fromSymbol = fromToken?.symbol ?: "",
                            toSymbol = toToken?.symbol ?: "",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                else -> {
                    QuoteLoading(modifier = Modifier.height(60.dp))
                }
            }
        }
        
        // Action buttons (Prioritize visibility)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            
            if (uiState.status == SwapStatus.QUOTE_READY) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth(0.9f) // Use 90% width for easier tap
                        .height(36.dp) // Compact height
                        .testTag(com.cbstudio.wearwallet.presentation.TestTags.SWAP_QUOTE_CONFIRM_BUTTON),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
                ) {
                    Text("Confirm", style = MaterialTheme.typography.button)
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            CompactCancelButton(onCancel)
        }
    }
}

@Composable
private fun CompactCancelButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp), // Small circle button for cancel
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.surface
        )
    ) {
        Text("X", style = MaterialTheme.typography.caption2)
    }
}
