package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import androidx.wear.compose.material.CircularProgressIndicator as WearProgressIndicator

/**
 * KMP 整合用的 WearOS UI 組件 - 簡化版本
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        WearProgressIndicator()
    }
}

@Composable  
fun BalanceCardKmp(
    totalBalance: String = "升級中",
    activeWallet: String? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onNavigateToReceive: () -> Unit = {},
    onNavigateToSend: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    MaintenanceCard(
        title = "餘額升級中",
        message = "KMP 整合進行中"
    )
}

@Composable
fun WalletInfoKmp(
    walletName: String = "升級中",
    walletAddress: String = "KMP 整合中",
    modifier: Modifier = Modifier
) {
    MaintenanceCard(
        title = "錢包信息升級中", 
        message = "KMP 整合進行中"
    )
}

@Composable
fun TokenListKmp(
    tokens: List<String> = emptyList(),
    onTokenClick: (String) -> Unit = {},
    modifier: Modifier = Modifier  
) {
    MaintenanceCard(
        title = "代幣列表升級中",
        message = "KMP 整合進行中"
    )
}

@Composable
fun TransactionHistoryKmp(
    transactions: List<String> = emptyList(),
    onTransactionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    MaintenanceCard(
        title = "交易記錄升級中",
        message = "KMP 整合進行中"
    )
}

@Composable
private fun MaintenanceCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}