package com.cbstudio.wearwallet.presentation.wallet.screens.swap.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.cbstudio.wearwallet.core.rango.model.RangoTokenMeta
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.components.TokenIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Amount Input Screen for Wear OS
 * 
 * Allows users to input swap amount with quick percentage buttons
 */
@Composable
fun AmountInputScreen(
    fromToken: RangoTokenMeta,
    balance: Double = 0.0,
    onAmountConfirmed: (String) -> Unit,
    onBackClick: () -> Unit = {}
) {
    var amount by remember { mutableStateOf("") }
    var displayAmount by remember { mutableStateOf("0") }
    
    
    // Calculate USD value
    val usdValue = remember(amount, fromToken.usdPrice) {
        if (amount.isNotEmpty() && fromToken.usdPrice != null) {
            try {
                amount.toDouble() * fromToken.usdPrice!!
            } catch (e: Exception) { null }
        } else null
    }
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 16.dp,
            start = 8.dp,
            end = 8.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with token info
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                TokenIcon(
                    symbol = fromToken.symbol,
                    imageUrl = fromToken.image,
                    size = 24.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send ${fromToken.symbol}",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary
                )
            }
        }
        
        // Amount display
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (amount.isEmpty()) "0" else amount,
                        style = MaterialTheme.typography.display2,
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier.testTag(com.cbstudio.wearwallet.presentation.TestTags.SWAP_AMOUNT_DISPLAY)
                    )
                    
                    usdValue?.let {
                        Text(
                            text = "~$${String.format("%.2f", it)}",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
        
        // Quick percentage buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PercentageButton("25%") { 
                    if (balance > 0) {
                        amount = String.format("%.6f", balance * 0.25).trimEnd('0').trimEnd('.')
                    }
                }
                PercentageButton("50%") { 
                    if (balance > 0) {
                        amount = String.format("%.6f", balance * 0.5).trimEnd('0').trimEnd('.')
                    }
                }
                PercentageButton("MAX") { 
                    if (balance > 0) {
                        amount = String.format("%.6f", balance * 0.95).trimEnd('0').trimEnd('.') // 95% for gas
                    }
                }
            }
        }
        
        // Number pad (simplified for watch)
        item {
            NumberPad(
                onNumberClick = { num ->
                    if (amount == "0") {
                        amount = num
                    } else {
                        amount += num
                    }
                },
                onDotClick = {
                    if (!amount.contains(".")) {
                        amount = if (amount.isEmpty()) "0." else "$amount."
                    }
                },
                onDeleteClick = {
                    if (amount.isNotEmpty()) {
                        amount = amount.dropLast(1)
                    }
                },
                modifier = Modifier.testTag(com.cbstudio.wearwallet.presentation.TestTags.SWAP_AMOUNT_KEYPAD)
            )
        }
        
        // Confirm button
        item {
            Button(
                onClick = { 
                    if (amount.isNotEmpty() && amount != "0") {
                        onAmountConfirmed(amount)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag(com.cbstudio.wearwallet.presentation.TestTags.SWAP_AMOUNT_CONFIRM_BUTTON),
                enabled = amount.isNotEmpty() && amount != "0" && amount != "."
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun PercentageButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.primary.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.primary
        )
    }
}

@Composable
private fun NumberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.onSurface
        )
    }
}

@Composable
fun NumberPad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onDotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier.padding(bottom = 6.dp), // Increased spacing
                horizontalArrangement = Arrangement.spacedBy(12.dp) // Increased spacing
            ) {
                row.forEach { number ->
                    NumberButton(
                        text = number,
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNumberClick(number) 
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("key_$number")
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberButton(
                text = ".",
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDotClick() 
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("key_dot")
            )
            NumberButton(
                text = "0",
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNumberClick("0") 
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("key_0")
            )
            // Delete Button (Custom implementation to avoid Material/Wear conflicts)
            Box(
                modifier = Modifier
                    .size(48.dp) // P1: Consistent size
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.surface)
                    .testTag("key_del")
                    .clickable(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDeleteClick()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colors.onSurface
                )
            }
        }
    }
}
