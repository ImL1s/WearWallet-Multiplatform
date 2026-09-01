package com.cbstudio.wearwallet.presentation.wallet.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.compose.ui.platform.testTag
import com.cbstudio.wearwallet.presentation.TestTags

/**
 * 錢包新手引導畫面
 * 讓用戶選擇創建新錢包或導入現有錢包
 */
@Composable
fun WalletOnboardingScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScalingLazyListState()
    
    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTags.ONBOARDING_SCREEN),
        state = scrollState,
        anchorType = ScalingLazyListAnchorType.ItemStart,
        autoCentering = null,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 標題
        item {
            Text(
                text = "歡迎使用",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item {
            Text(
                text = "WearWallet",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 創建新錢包選項
        item {
            Card(
                onClick = onNavigateToCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.CREATE_WALLET_BUTTON),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "創建新錢包",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Text(
                        text = "創建新錢包",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "生成全新的錢包",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // 導入錢包選項
        item {
            Card(
                onClick = onNavigateToImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.IMPORT_WALLET_BUTTON),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "導入錢包",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Text(
                        text = "導入錢包",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "使用助記詞或私鑰",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 安全提示
        item {
            Card(
                onClick = { },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🔒 安全提示",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
                )
                
                Text(
                    text = "您的私鑰將安全地儲存在設備上，請妥善保管助記詞",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(bottom = 12.dp, start = 16.dp, end = 16.dp, top = 4.dp)
                )
            }
        }
    }
}