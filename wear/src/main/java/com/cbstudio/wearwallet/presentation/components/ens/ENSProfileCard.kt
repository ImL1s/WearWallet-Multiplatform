package com.cbstudio.wearwallet.presentation.components.ens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
// Minimal local ENSProfile to unblock build during stubbing phase
data class ENSProfile(
    val name: String,
    val address: String,
    val avatar: String? = null,
    val description: String? = null,
    val twitter: String? = null,
    val github: String? = null,
    val discord: String? = null,
    val telegram: String? = null,
    val multiChainAddresses: Map<Int, String> = emptyMap()
)

/**
 * WearOS 優化的 ENS 個人資料卡片
 * 顯示 ENS 名稱、頭像和相關資訊
 */
@Composable
fun ENSProfileCard(
    profile: ENSProfile,
    modifier: Modifier = Modifier,
    onAddressClick: ((chainId: Int, address: String) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { /* 主卡片點擊 */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 頭像
            ENSAvatar(
                avatarUrl = profile.avatar,
                ensName = profile.name,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // ENS 名稱
            Text(
                text = profile.name,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
            
            // 主要地址
            Spacer(modifier = Modifier.height(4.dp))
            ENSAddressChip(
                address = profile.address,
                ensName = null, // 已經顯示在上方
                onClick = { onAddressClick?.invoke(1, profile.address) }
            )
            
            // 描述
            profile.description?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 社交連結
            val socialLinks = listOfNotNull(
                profile.twitter?.let { "🐦" to it },
                profile.github?.let { "🐙" to it },
                profile.discord?.let { "💬" to it },
                profile.telegram?.let { "✈️" to it }
            )
            
            if (socialLinks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    socialLinks.forEach { (icon, _) ->
                        Text(
                            text = icon,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            
            // 多鏈地址（可展開）
            if (profile.multiChainAddresses.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                MultiChainAddresses(
                    addresses = profile.multiChainAddresses,
                    onAddressClick = onAddressClick
                )
            }
        }
    }
}

/**
 * ENS 頭像組件
 */
@Composable
fun ENSAvatar(
    avatarUrl: String?,
    ensName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colors.surface),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$ensName avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // 預設頭像 - 使用 ENS 名稱的首字母
            val initial = ensName.firstOrNull()?.uppercase() ?: "?"
            Text(
                text = initial,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
        }
    }
}

/**
 * 多鏈地址顯示組件
 */
@Composable
private fun MultiChainAddresses(
    addresses: Map<Int, String>,
    onAddressClick: ((chainId: Int, address: String) -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 展開/收起按鈕
        CompactButton(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.surface
            )
        ) {
            Text(
                text = if (expanded) "收起多鏈地址 ↑" else "查看多鏈地址 ↓",
                style = MaterialTheme.typography.caption2
            )
        }
        
        // 地址列表
        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            addresses.forEach { (chainId, address) ->
                val chainName = getChainName(chainId)
                if (chainName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = chainName,
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            modifier = Modifier.weight(0.3f)
                        )
                        ENSAddressChip(
                            address = address,
                            onClick = { onAddressClick?.invoke(chainId, address) },
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 獲取鏈名稱
 */
private fun getChainName(chainId: Int): String? {
    return when (chainId) {
        0 -> "Bitcoin"
        1 -> "Ethereum"
        10 -> "Optimism"
        56 -> "BSC"
        137 -> "Polygon"
        501 -> "Solana"
        8453 -> "Base"
        42161 -> "Arbitrum"
        43114 -> "Avalanche"
        else -> null
    }
}
