package com.cbstudio.wearwallet.presentation.wallet.screens.debitcard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Crypto Debit Card Screen - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 最終衝刺編譯完成策略
 * 
 * TODO: Complex crypto debit card functionality temporarily disabled for maintenance
 * - All debit card features disabled
 * - Keep screen structure consistent for future implementation
 * - Focus on compilation stability
 */

@Composable
fun CryptoDebitCardScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {}
) {
    // MAINTENANCE MODE: Simplified crypto debit card display
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Payment,
            contentDescription = "Crypto Debit Card",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "MAINTENANCE MODE",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Crypto debit card temporarily disabled",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("返回")
            }
            
            Button(
                onClick = onNavigateToTransactions,
                modifier = Modifier.weight(1f)
            ) {
                Text("交易記錄")
            }
        }
    }
}