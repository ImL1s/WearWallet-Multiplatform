package com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.material.*
import com.cbstudio.wearwallet.R

@Composable
fun AddWalletScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateHotWallet: () -> Unit,
    onNavigateToImportHotWallet: () -> Unit,
    onNavigateToConnectKeystone: () -> Unit,
    viewModel: AddWalletViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 標題
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.add_wallet),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            }

            // 說明文字
            item {
                Text(
                    text = stringResource(R.string.select_wallet_type),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 創建熱錢包選項
            item {
                WalletTypeCard(
                    title = "🔥 ${stringResource(R.string.create_hot_wallet)}",
                    description = stringResource(R.string.create_hot_wallet_desc),
                    icon = Icons.Outlined.LocalFireDepartment,
                    iconColor = Color(0xFFFF6B35),
                    onClick = onNavigateToCreateHotWallet,
                    enabled = !isLoading
                )
            }

            // 導入熱錢包選項
            item {
                WalletTypeCard(
                    title = "📥 ${stringResource(R.string.import_hot_wallet)}",
                    description = stringResource(R.string.import_hot_wallet_desc),
                    icon = Icons.Outlined.Download,
                    iconColor = Color(0xFF4CAF50),
                    onClick = onNavigateToImportHotWallet,
                    enabled = !isLoading
                )
            }

            // 冷錢包選項
            item {
                WalletTypeCard(
                    title = "🧊 ${stringResource(R.string.wallet_type_cold)}",
                    description = stringResource(R.string.keystone_wallet_desc),
                    icon = Icons.Outlined.AcUnit,
                    iconColor = Color(0xFF4A90E2),
                    onClick = onNavigateToConnectKeystone,
                    enabled = !isLoading
                )
            }

            // 安全提示
            item {
                SecurityTip()
            }
        }

        // 載入指示器
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // 錯誤消息
        errorMessage?.let { message ->
            LaunchedEffect(message) {
                // 可以顯示 Toast 或其他錯誤提示
                // 這裡簡單地清除錯誤
                viewModel.clearError()
            }
        }
    }
}

@Composable
private fun WalletTypeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SecurityTip() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "🔒 ${stringResource(R.string.security_tip)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.wallet_security_tips),
                fontSize = 9.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                lineHeight = 11.sp
            )
        }
    }
} 
