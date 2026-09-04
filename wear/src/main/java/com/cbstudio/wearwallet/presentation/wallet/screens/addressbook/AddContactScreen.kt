package com.cbstudio.wearwallet.presentation.wallet.screens.addressbook

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 新增聯絡人畫面 - 完整實現
 * 連接到 coreKmp 的 AddAddressContactUseCase
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AddContactScreen(
    onBackClick: () -> Unit = {},
    onContactSaved: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AddContactViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScalingLazyListState()
    val clipboardManager = LocalClipboardManager.current
    
    // 當聯絡人保存成功時導航回去
    LaunchedEffect(uiState.contactSaved) {
        if (uiState.contactSaved) {
            delay(500) // 短暫延遲以顯示成功訊息
            onContactSaved()
        }
    }
    
    // 動畫狀態
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1117),
                        Color(0xFF161B22)
                    )
                )
            )
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically()
        ) {
            ScalingLazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                anchorType = ScalingLazyListAnchorType.ItemStart,
                contentPadding = PaddingValues(
                    top = 20.dp,
                    bottom = 32.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 標題列
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // 返回按鈕
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // 標題
                        Text(
                            text = "新增聯絡人",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                
                // 聯絡人名稱輸入
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "聯絡人名稱",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            BasicTextField(
                                value = uiState.name,
                                onValueChange = viewModel::updateName,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                decorationBox = { innerTextField ->
                                    if (uiState.name.isEmpty()) {
                                        Text(
                                            text = "輸入名稱...",
                                            style = TextStyle(
                                                color = Color.White.copy(alpha = 0.3f),
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }
                
                // 區塊鏈選擇
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "區塊鏈",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // 主要區塊鏈選擇
                            val commonChains = listOf(
                                ChainType.ETHEREUM to "Ethereum",
                                ChainType.BSC to "BSC",
                                ChainType.POLYGON to "Polygon"
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                commonChains.forEach { (chain, name) ->
                                    FilterChip(
                                        selected = uiState.chainType == chain,
                                        onClick = { viewModel.updateChainType(chain) },
                                        label = { 
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF58A6FF).copy(alpha = 0.3f),
                                            containerColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 錢包地址輸入
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "錢包地址",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                if (uiState.address.isNotEmpty()) {
                                    Icon(
                                        imageVector = if (uiState.isAddressValid) 
                                            Icons.Default.CheckCircle 
                                        else 
                                            Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (uiState.isAddressValid) 
                                            Color(0xFF4ADE80) 
                                        else 
                                            Color(0xFFFF6B6B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            BasicTextField(
                                value = uiState.address,
                                onValueChange = viewModel::updateAddress,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp
                                ),
                                singleLine = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                decorationBox = { innerTextField ->
                                    if (uiState.address.isEmpty()) {
                                        Text(
                                            text = "貼上或輸入地址...",
                                            style = TextStyle(
                                                color = Color.White.copy(alpha = 0.3f),
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    val pasted = clipboardManager.getText()?.text
                                    viewModel.pasteAddress(pasted)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("貼上地址")
                            }
                        }
                    }
                }
                
                // 分類選擇
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "分類",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            val categories = listOf(
                                ContactCategory.FRIEND to "朋友",
                                ContactCategory.EXCHANGE to "交易所",
                                ContactCategory.OTHER to "其他"
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEach { (category, name) ->
                                    FilterChip(
                                        selected = uiState.category == category,
                                        onClick = { viewModel.updateCategory(category) },
                                        label = { 
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF58A6FF).copy(alpha = 0.3f),
                                            containerColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 備註輸入（可選）
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "備註（可選）",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            BasicTextField(
                                value = uiState.notes,
                                onValueChange = viewModel::updateNotes,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp
                                ),
                                singleLine = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                decorationBox = { innerTextField ->
                                    if (uiState.notes.isEmpty()) {
                                        Text(
                                            text = "添加備註...",
                                            style = TextStyle(
                                                color = Color.White.copy(alpha = 0.3f),
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }
                
                // 錯誤訊息
                uiState.errorMessage?.let { error ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                
                // 成功訊息
                if (uiState.contactSaved) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF4ADE80).copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "聯絡人已新增",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4ADE80)
                                )
                            }
                        }
                    }
                }
                
                // 操作按鈕
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Text(
                                text = "取消",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        
                        Button(
                            onClick = { viewModel.saveContact() },
                            enabled = uiState.name.isNotBlank() && 
                                     uiState.address.isNotBlank() && 
                                     uiState.isAddressValid &&
                                     !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF58A6FF),
                                disabledContainerColor = Color(0xFF58A6FF).copy(alpha = 0.3f)
                            )
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "保存",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}