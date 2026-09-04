package com.cbstudio.wearwallet.presentation.wallet.screens.bitcoin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.dialog.Alert
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigInteger

/**
 * Bitcoin 錢包主畫面
 * 顯示 Bitcoin 餘額、UTXO 和交易功能
 */
@Composable
fun BitcoinWalletScreen(
    walletId: String,
    onNavigateBack: () -> Unit,
    onNavigateToSend: () -> Unit,
    onNavigateToReceive: () -> Unit,
    viewModel: BitcoinWalletViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    
    // 初始化錢包
    LaunchedEffect(walletId) {
        viewModel.initializeWallet(walletId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF7931A), // Bitcoin 橙色
                        Color(0xFFE68A00)
                    )
                )
            )
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 32.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bitcoin Logo 和標題
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "₿",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Bitcoin",
                        style = MaterialTheme.typography.title1,
                        color = Color.White
                    )
                    
                    // 網路切換
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Chip(
                            label = { Text("主網") },
                            onClick = { viewModel.switchNetwork(false) },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (uiState.selectedNetwork == com.cbstudio.wearwallet.core.domain.model.Network.BITCOIN_MAINNET) 
                                    Color.White.copy(alpha = 0.3f) 
                                else 
                                    Color.Transparent
                            )
                        )
                        Chip(
                            label = { Text("測試網") },
                            onClick = { viewModel.switchNetwork(true) },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (uiState.selectedNetwork == com.cbstudio.wearwallet.core.domain.model.Network.BITCOIN_TESTNET) 
                                    Color.White.copy(alpha = 0.3f) 
                                else 
                                    Color.Transparent
                            )
                        )
                    }
                }
            }
            
            // 餘額顯示
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    onClick = {}, // Card 不可點擊
                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                        startBackgroundColor = Color.White.copy(alpha = 0.2f),
                        endBackgroundColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "餘額",
                            style = MaterialTheme.typography.caption1,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = viewModel.formatBalanceAsBTC(),
                            style = MaterialTheme.typography.title2.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        
                        // UTXO 數量
                        Text(
                            text = "${uiState.utxos.size} UTXOs",
                            style = MaterialTheme.typography.caption2,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // 地址顯示（縮短版）
            item {
                if (uiState.address.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        onClick = onNavigateToReceive,
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = Color.White.copy(alpha = 0.1f),
                            endBackgroundColor = Color.Transparent
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "地址",
                                style = MaterialTheme.typography.caption2,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "${uiState.address.take(6)}...${uiState.address.takeLast(4)}",
                                style = MaterialTheme.typography.body2,
                                color = Color.White,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // 操作按鈕
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Button(
                        onClick = { viewModel.showSendDialog() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.White
                        )
                    ) {
                        Text(
                            text = "發送",
                            color = Color(0xFFF7931A)
                        )
                    }
                    Button(
                        onClick = onNavigateToReceive,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = "接收",
                            color = Color.White
                        )
                    }
                }
            }
            
            // 刷新按鈕
            item {
                Chip(
                    label = { Text("刷新餘額") },
                    onClick = { viewModel.refreshBalance() },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color.White.copy(alpha = 0.1f)
                    ),
                    enabled = !uiState.isLoading
                )
            }
            
            // UTXO 列表標題
            if (uiState.utxos.isNotEmpty()) {
                item {
                    Text(
                        text = "UTXOs",
                        style = MaterialTheme.typography.title3,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                
                // UTXO 列表
                items(uiState.utxos.take(5)) { utxo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        onClick = {}, // UTXO 卡片不可點擊
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = Color.White.copy(alpha = 0.05f),
                            endBackgroundColor = Color.Transparent
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "${utxo.value} sats",
                                style = MaterialTheme.typography.body1,
                                color = Color.White
                            )
                            Text(
                                text = "${utxo.txid.take(8)}...${utxo.txid.takeLast(4)}",
                                style = MaterialTheme.typography.caption2,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = if (utxo.confirmed) "已確認" else "未確認",
                                style = MaterialTheme.typography.caption3,
                                color = if (utxo.confirmed) Color.Green else Color.Yellow,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // 返回按鈕
            item {
                Chip(
                    label = { Text("返回") },
                    onClick = onNavigateBack,
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        
        // 載入指示器
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    indicatorColor = Color.White
                )
            }
        }
    }
    
    // 發送對話框
    if (uiState.showSendDialog) {
        BitcoinSendDialog(
            recipientAddress = uiState.recipientAddress,
            sendAmount = uiState.sendAmount,
            onAddressChange = viewModel::updateRecipientAddress,
            onAmountChange = viewModel::updateSendAmount,
            onConfirm = { password ->
                viewModel.sendTransaction(password)
            },
            onDismiss = { viewModel.hideSendDialog() }
        )
    }
    
    // 錯誤對話框
    uiState.error?.let { error ->
        Dialog(
            showDialog = true,
            onDismissRequest = { viewModel.clearError() }
        ) {
            Alert(
                title = { Text("錯誤") },
                message = { Text(error) },
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Chip(
                        label = { Text("確定") },
                        onClick = { viewModel.clearError() },
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
            }
        }
    }
    
    // 成功對話框
    uiState.transactionHash?.let { txHash ->
        Dialog(
            showDialog = true,
            onDismissRequest = { viewModel.clearTransactionHash() }
        ) {
            Alert(
                title = { Text("交易成功") },
                message = { 
                    Column {
                        Text("交易已廣播")
                        Text(
                            text = "${txHash.take(8)}...${txHash.takeLast(8)}",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                },
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Chip(
                        label = { Text("確定") },
                        onClick = { viewModel.clearTransactionHash() },
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
            }
        }
    }
}

/**
 * Bitcoin 發送對話框
 */
@Composable
private fun BitcoinSendDialog(
    recipientAddress: String,
    sendAmount: String,
    onAddressChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showAddressInput by remember { mutableStateOf(false) }
    var showAmountInput by remember { mutableStateOf(false) }
    var showPasswordInput by remember { mutableStateOf(false) }

    // 主對話框
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = { Text("發送 Bitcoin", textAlign = TextAlign.Center) },
            message = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 接收地址輸入
                    CompactChip(
                        label = {
                            Text(
                                text = if (recipientAddress.isEmpty()) "點擊輸入地址" else "${recipientAddress.take(6)}...${recipientAddress.takeLast(6)}",
                                style = MaterialTheme.typography.caption2
                            )
                        },
                        onClick = { showAddressInput = true },
                        colors = ChipDefaults.secondaryChipColors()
                    )

                    // 金額輸入
                    CompactChip(
                        label = {
                            Text(
                                text = if (sendAmount.isEmpty()) "點擊輸入金額" else "$sendAmount BTC",
                                style = MaterialTheme.typography.caption2
                            )
                        },
                        onClick = { showAmountInput = true },
                        colors = ChipDefaults.secondaryChipColors()
                    )

                    // 密碼輸入
                    CompactChip(
                        label = {
                            Text(
                                text = if (password.isEmpty()) "點擊輸入密碼" else "••••••",
                                style = MaterialTheme.typography.caption2
                            )
                        },
                        onClick = { showPasswordInput = true },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            },
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactChip(
                        label = { Text("取消") },
                        onClick = onDismiss,
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                    CompactChip(
                        label = { Text("發送") },
                        onClick = { onConfirm(password) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = Color(0xFFF7931A)
                        ),
                        modifier = Modifier.weight(1f),
                        enabled = recipientAddress.isNotEmpty() && sendAmount.isNotEmpty() && password.isNotEmpty()
                    )
                }
            }
        }
    }

    // 地址輸入
    if (showAddressInput) {
        TextInputDialog(
            title = "接收地址",
            initialValue = recipientAddress,
            options = listOf("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"),
            onConfirm = {
                onAddressChange(it)
                showAddressInput = false
            },
            onDismiss = { showAddressInput = false }
        )
    }

    // 金額輸入
    if (showAmountInput) {
        TextInputDialog(
            title = "金額 (BTC)",
            initialValue = sendAmount,
            options = listOf("0.0001", "0.001", "0.01", "0.1"),
            onConfirm = {
                onAmountChange(it)
                showAmountInput = false
            },
            onDismiss = { showAmountInput = false }
        )
    }

    // 密碼輸入
    if (showPasswordInput) {
        TextInputDialog(
            title = "錢包密碼",
            initialValue = password,
            options = listOf("123456", "password"),
            onConfirm = {
                password = it
                showPasswordInput = false
            },
            onDismiss = { showPasswordInput = false }
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    options: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(showDialog = true, onDismissRequest = onDismiss) {
        Alert(
            title = { Text(title) },
            contentPadding = PaddingValues(16.dp)
        ) {
            items(options) { value ->
                Chip(
                    label = { 
                        Text(
                            text = if (value.length > 15) "${value.take(6)}...${value.takeLast(6)}" else value,
                            style = MaterialTheme.typography.body2
                        ) 
                    },
                    onClick = { onConfirm(value) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("關閉") },
                    onClick = onDismiss,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}