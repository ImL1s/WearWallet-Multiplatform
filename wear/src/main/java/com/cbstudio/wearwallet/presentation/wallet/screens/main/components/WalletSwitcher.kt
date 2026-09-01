package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.WalletType

@Composable
fun WalletSwitcher(
    currentWallet: WalletAccount?,
    walletCount: Int,
    onManageWallets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onManageWallets,
        modifier = modifier
            .fillMaxWidth()
            .testTag("wallet_switcher"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        if (currentWallet != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // 錢包類型圖示
                    Icon(
                        imageVector = when (currentWallet.walletType) {
                            WalletType.HOT_WALLET -> Icons.Outlined.LocalFireDepartment
                            WalletType.KEYSTONE_COLD -> Icons.Outlined.AcUnit
                            else -> Icons.Outlined.AccountBalance
                        },
                        contentDescription = null,
                        tint = when (currentWallet.walletType) {
                            WalletType.HOT_WALLET -> Color(0xFFFF6B35)
                            WalletType.KEYSTONE_COLD -> Color(0xFF4A90E2)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = currentWallet.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = "${currentWallet.address.take(6)}...${currentWallet.address.takeLast(4)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (walletCount > 1) {
                        Text(
                            text = "$walletCount",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.wallet_management),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            // 沒有錢包的狀態
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_wallet),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
} 
