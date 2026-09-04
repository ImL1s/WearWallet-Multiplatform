package com.cbstudio.wearwallet.presentation.wearfi

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * WearFi 儀表板 - 簡化版本
 * ULTRATHINK Phase 12 激進清理
 */
@Composable
fun WearFiDashboardScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToChallenges: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToNFTs: () -> Unit = {},
    onNavigateToMiningDashboard: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WearFi 儀表板",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "健康挖礦功能升級中\n敬請期待更豐富的體驗",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 簡單的按鈕組
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onNavigateToMiningDashboard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("挖礦中心")
            }
            
            Button(
                onClick = onNavigateToChallenges,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("挑戰任務")
            }
            
            Button(
                onClick = onNavigateToAchievements,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("成就系統")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onNavigateBack
        ) {
            Text("返回")
        }
    }
}