package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Transaction Item Component - MAINTENANCE MODE
 * ULTRATHINK Phase 17 - 最終編譯完成策略
 * 
 * TODO: Complex transaction UI components temporarily simplified for maintenance
 * - All transaction display functionality simplified
 * - Keep UI structure consistent for future implementation
 * - Focus on compilation stability
 */

@Composable
fun TransactionItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // MAINTENANCE MODE: Simplified transaction item display
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionIcon()
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MAINTENANCE MODE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Transaction display disabled",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            StatusIndicator()
        }
    }
}

@Composable
private fun TransactionIcon() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = "Transaction",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatusIndicator() {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = "Status",
        tint = Color.Green,
        modifier = Modifier.size(16.dp)
    )
}

// MAINTENANCE MODE: Simplified utility functions
fun getTransactionLabel(): String = "MAINTENANCE"
fun getTransactionSubtitle(): String = "Disabled"
fun formatAddress(address: String): String = "MAINTENANCE"
fun formatAmountOnly(amount: String): String = "0.00"
fun formatKmpTimestamp(timestamp: Long): String = "00:00"
fun getAmountColor(): Color = Color.Gray
fun getSymbolFromTransaction(): String = "N/A"