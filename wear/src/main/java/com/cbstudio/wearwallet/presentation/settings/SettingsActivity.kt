package com.cbstudio.wearwallet.presentation.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.cbstudio.wearwallet.R
import androidx.wear.compose.material.MaterialTheme
import com.cbstudio.wearwallet.data.wear.WearNftDataManager
import org.koin.android.ext.android.inject

/**
 * NFT 設定活動
 * 
 * 提供 NFT 複雜功能的基本設定選項
 */
// @AndroidEntryPoint  // Removed Hilt
class SettingsActivity : ComponentActivity() {
    
    private val wearNftDataManager: WearNftDataManager by inject<WearNftDataManager>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val action = intent.getStringExtra("action") ?: ""
        
        setContent {
            MaterialTheme {
                SettingsScreen(
                    wearNftDataManager = wearNftDataManager,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    wearNftDataManager: WearNftDataManager,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val nftSettings by wearNftDataManager.nftSettings.collectAsState()
    val connectionStatus by wearNftDataManager.connectionStatus.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 標題
        Text(
            text = stringResource(R.string.nft_settings_title),
            color = MaterialTheme.colors.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 連接狀態
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = (if (connectionStatus) Color(0xFF4CAF50) else Color(0xFFFF5722)).copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.connection_status),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = if (connectionStatus) stringResource(R.string.connected) else stringResource(R.string.not_connected),
                    color = if (connectionStatus) Color(0xFF4CAF50) else Color(0xFFFF5722),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // NFT 功能狀態
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.05f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.nft_complication),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = if (nftSettings.isEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                    color = if (nftSettings.isEnabled) Color(0xFF4CAF50) else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (nftSettings.selectedNftContract.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.current_nft),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${nftSettings.selectedNftContract.take(6)}...${nftSettings.selectedNftContract.takeLast(4)}:${nftSettings.selectedNftTokenId}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 自動輪換設定
        if (nftSettings.autoRotateEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2196F3).copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.auto_rotate),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = stringResource(R.string.enabled),
                        color = Color(0xFF2196F3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.rotate_interval_hours, nftSettings.rotateIntervalSeconds / 3600),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // 提示信息
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFF9800).copy(alpha = 0.2f)
            )
        ) {
            Text(
                text = stringResource(R.string.nft_config_phone_hint),
                color = Color(0xFFFF9800),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 操作按鈕
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 手動切換 NFT
            Button(
                onClick = {
                    wearNftDataManager.switchToNextNft()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                ),
                enabled = nftSettings.isEnabled && wearNftDataManager.getNftCollectionSize() > 1
            ) {
                Text(
                    text = stringResource(R.string.switch_nft),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
            
            // 關閉按鈕
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = stringResource(R.string.close),
                    color = MaterialTheme.colors.onSurface,
                    fontSize = 10.sp
                )
            }
        }
        
        // 底部間距
        Spacer(modifier = Modifier.height(20.dp))
    }
}
