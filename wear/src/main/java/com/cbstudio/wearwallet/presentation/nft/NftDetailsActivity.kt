package com.cbstudio.wearwallet.presentation.nft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.presentation.complication.NftComplicationDataProvider
import com.cbstudio.wearwallet.data.wear.WearNftDataManager
import org.koin.android.ext.android.inject

/**
 * NFT 詳情活動
 * 
 * 顯示 NFT 的詳細信息，針對 Wear OS 小屏幕優化
 */
// @AndroidEntryPoint  // Removed Hilt
class NftDetailsActivity : ComponentActivity() {
    
    private val wearNftDataManager: WearNftDataManager by inject<WearNftDataManager>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val contractAddress = intent.getStringExtra("nft_contract") ?: ""
        val tokenId = intent.getStringExtra("nft_token_id") ?: ""
        val name = intent.getStringExtra("nft_name") ?: "Unknown NFT"
        val imageUrl = intent.getStringExtra("nft_image_url") ?: ""
        val collectionName = intent.getStringExtra("nft_collection") ?: ""
        val action = intent.getStringExtra("action") ?: ""
        
        setContent {
            MaterialTheme {
                NftDetailsScreen(
                    contractAddress = contractAddress,
                    tokenId = tokenId,
                    name = name,
                    imageUrl = imageUrl,
                    collectionName = collectionName,
                    action = action,
                    onNextNft = {
                        if (wearNftDataManager.switchToNextNft()) {
                            // 更新複雜功能
                            NftComplicationDataProvider.requestUpdateAll(this@NftDetailsActivity)
                            finish()
                        }
                    },
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun NftDetailsScreen(
    contractAddress: String,
    tokenId: String,
    name: String,
    imageUrl: String,
    collectionName: String,
    action: String,
    onNextNft: () -> Unit,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // NFT 圖片
        // Placeholder for NFT image - in production this would use AsyncImage
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = name,
                modifier = Modifier.size(48.dp),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // NFT 名稱
        Text(
            text = name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 集合名稱
        if (collectionName.isNotBlank()) {
            Text(
                text = collectionName,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 合約地址（簡化顯示）
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
                    text = "合約地址",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                Text(
                    text = "${contractAddress.take(6)}...${contractAddress.takeLast(4)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Token ID",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                Text(
                    text = tokenId,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 操作按鈕
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 下一個 NFT 按鈕
            Button(
                onClick = onNextNft,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Text(
                    text = "下一個",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
            
            // 關閉按鈕
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = "關閉",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
        
        // 底部間距
        Spacer(modifier = Modifier.height(20.dp))
    }
}
