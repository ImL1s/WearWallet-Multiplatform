package com.cbstudio.wearwallet.presentation.components.ens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*

/**
 * WearOS 優化的 ENS 地址顯示組件
 * 在有限空間中優雅地顯示 ENS 名稱或縮短地址
 */
@Composable
fun ENSAddressChip(
    address: String,
    ensName: String? = null,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    var showFullAddress by remember { mutableStateOf(false) }
    
    val displayText = when {
        isLoading -> "..."
        ensName != null && !showFullAddress -> ensName
        else -> formatAddress(address)
    }
    
    val chipModifier = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colors.surface)
        .then(
            if (onClick != null) {
                Modifier.clickable { 
                    if (ensName != null) {
                        showFullAddress = !showFullAddress
                    }
                    onClick()
                }
            } else Modifier
        )
    
    Row(
        modifier = chipModifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // ENS 圖標
        if (ensName != null && !showFullAddress) {
            Text(
                text = "🌐",
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        
        // 地址或 ENS 名稱
        Text(
            text = displayText,
            style = MaterialTheme.typography.caption1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isLoading) MaterialTheme.colors.onSurface.copy(alpha = 0.6f) 
                   else MaterialTheme.colors.onSurface
        )
        
        // 驗證狀態圖標
        if (ensName != null && !isLoading && !showFullAddress) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "✓",
                fontSize = 12.sp,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

/**
 * 格式化地址為短格式
 */
private fun formatAddress(address: String): String {
    return if (address.length > 10) {
        "${address.substring(0, 6)}...${address.substring(address.length - 4)}"
    } else {
        address
    }
}

/**
 * ENS 地址列表項目
 * 用於在列表中顯示 ENS 名稱和地址
 */
@Composable
fun ENSAddressListItem(
    address: String,
    ensName: String? = null,
    label: String? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Chip(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        label = {
            Column {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
                ENSAddressChip(
                    address = address,
                    ensName = ensName,
                    isLoading = isLoading
                )
            }
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = MaterialTheme.colors.surface
        )
    )
}
