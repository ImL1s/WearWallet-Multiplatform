package com.cbstudio.wearwallet.presentation.wearfi

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * WearFi 成就展示畫面 - 簡化版本
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */
@Composable
fun WearFiAchievementsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChallenges: () -> Unit = {},
    onNavigateToNFTGallery: () -> Unit = {},
    viewModel: WearFiAchievementsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadAchievements()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 32.dp,
                start = 8.dp,
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 標題
            item {
                Text(
                    text = "WearFi 成就",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 維護信息圖標
            item {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "WearFi 成就",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .fillMaxWidth()
                )
            }
            
            // 維護信息
            item {
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // 描述
            item {
                Text(
                    text = "WearFi 成就系統正遷移到 KMP 架構\n即將提供更豐富的健康數據挖礦獎勵",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 返回按鈕
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("返回")
                }
            }
        }
        
        // 載入指示器
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
