package com.cbstudio.wearwallet.presentation.wearfi

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WearFi Challenges Screen - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 最終衝刺編譯完成策略
 * 
 * TODO: Complex WearFi challenge functionality temporarily disabled for maintenance
 * - All WearFi features disabled
 * - Keep screen structure consistent for future implementation
 * - Focus on compilation stability
 */

@Composable
fun WearFiChallengesScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    // MAINTENANCE MODE: Simplified WearFi challenges display
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsRun,
            contentDescription = "WearFi Challenges",
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
            text = "WearFi challenges temporarily disabled",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }
    }
}