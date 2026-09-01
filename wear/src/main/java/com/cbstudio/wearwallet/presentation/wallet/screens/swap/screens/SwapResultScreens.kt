package com.cbstudio.wearwallet.presentation.wallet.screens.swap.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*

/**
 * Swap Success Screen for Wear OS
 */
@Composable
fun SwapSuccessScreen(
    outputAmount: String = "",
    outputSymbol: String = "",
    txHash: String? = null,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // Success icon
            Text(
                text = "✅",
                style = MaterialTheme.typography.display1
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Swap Complete!",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )
            
            if (outputAmount.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Received",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                
                Text(
                    text = "$outputAmount $outputSymbol",
                    style = MaterialTheme.typography.title3,
                    color = Color(0xFF4CAF50)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Done")
            }
        }
    }
}

/**
 * Swap Progress Screen for Wear OS
 */
@Composable
fun SwapProgressScreen(
    status: String = "Processing...",
    fromChain: String = "",
    toChain: String = "",
    progress: Float = 0f
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                indicatorColor = MaterialTheme.colors.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = status,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center
            )
            
            if (fromChain.isNotEmpty() && toChain.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "$fromChain → $toChain",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
            
            if (progress > 0f) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
}

/**
 * Swap Failed Screen for Wear OS
 */
@Composable
fun SwapFailedScreen(
    error: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // Error icon
            Text(
                text = "❌",
                style = MaterialTheme.typography.display1
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Swap Failed",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = error,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = onRetry
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
