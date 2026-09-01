package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.domain.model.ChainType



/**
 * 主畫面餘額卡片 - 連接到 coreKmp
 */
@Composable
fun SimpleMainBalanceCard(
    currentWallet: WalletAccount?,
    chainType: ChainType,
    balance: Double,
    balanceUsd: String,
    tokenPrice: Double?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { /* TODO: 實現點擊動作 */ },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 錢包資訊行
            if (currentWallet != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 錢包類型圖標
                    Icon(
                        imageVector = when (currentWallet.walletType) {
                            WalletType.HOT_WALLET -> Icons.Outlined.LocalFireDepartment
                            WalletType.KEYSTONE_COLD -> Icons.Outlined.AcUnit
                            else -> Icons.Outlined.LocalFireDepartment
                        },
                        contentDescription = null,
                        tint = when (currentWallet.walletType) {
                            WalletType.HOT_WALLET -> Color(0xFFFF6B35)
                            WalletType.KEYSTONE_COLD -> Color(0xFF4A90E2)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // 錢包名稱或類型文字
                    Text(
                        text = when (currentWallet.walletType) {
                            WalletType.HOT_WALLET -> "維護模式錢包"
                            WalletType.KEYSTONE_COLD -> "Keystone 錢包"
                            else -> currentWallet.name
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 鏈名稱
            Text(
                text = when (chainType) {
                    ChainType.ETHEREUM -> "ETH"
                    ChainType.BSC -> "BNB"
                    ChainType.POLYGON -> "MATIC"
                    ChainType.BITCOIN -> "BTC"
                    ChainType.SOLANA -> "SOL"
                    else -> chainType.name
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 餘額
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = formatAdaptiveBalance(balance),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // USD 價值
                Text(
                    text = "≈ $balanceUsd",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 當前價格（如果有）
                tokenPrice?.let { price ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "@ $${"%.2f".format(price)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}