package com.cbstudio.wearwallet.presentation.wallet.screens.utxo

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
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.dialog.Alert
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cbstudio.wearwallet.core.domain.model.ChainType

/**
 * UTXO 鏈通用發送畫面
 * 支援 Bitcoin, Litecoin, Dogecoin, Bitcoin Cash
 */
@Composable
fun UTXOSendScreen(
    chainType: ChainType,
    onNavigateBack: () -> Unit,
    onTransactionSent: (String) -> Unit,
    viewModel: UTXOSendViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    
    // 初始化鏈類型
    LaunchedEffect(chainType) {
        viewModel.initializeChain(chainType)
    }
    
    // 獲取鏈的顏色和符號
    val chainColor = when (chainType) {
        ChainType.BITCOIN -> Color(0xFFF7931A)
        ChainType.LITECOIN -> Color(0xFF345D9D)
        ChainType.DOGECOIN -> Color(0xFFC3A634)
        ChainType.BITCOIN_CASH -> Color(0xFF0AC18E)
        else -> Color.Gray
    }
    
    val chainSymbol = when (chainType) {
        ChainType.BITCOIN -> "BTC"
        ChainType.LITECOIN -> "LTC"
        ChainType.DOGECOIN -> "DOGE"
        ChainType.BITCOIN_CASH -> "BCH"
        else -> ""
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        chainColor,
                        chainColor.copy(alpha = 0.7f)
                    )
                )
            )
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 標題
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "發送 $chainSymbol",
                        style = MaterialTheme.typography.title2,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // 餘額顯示
                    if (uiState.balance > 0) {
                        Text(
                            text = "餘額: ${viewModel.formatBalance()} $chainSymbol",
                            style = MaterialTheme.typography.caption1,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // 接收地址輸入
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    onClick = { viewModel.showAddressInput() },
                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                        startBackgroundColor = Color.White.copy(alpha = 0.2f),
                        endBackgroundColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "接收地址",
                            style = MaterialTheme.typography.caption2,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (uiState.recipientAddress.isEmpty()) 
                                "點擊輸入" 
                            else 
                                "${uiState.recipientAddress.take(6)}...${uiState.recipientAddress.takeLast(4)}",
                            style = MaterialTheme.typography.body2,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // 金額輸入
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    onClick = { viewModel.showAmountInput() },
                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                        startBackgroundColor = Color.White.copy(alpha = 0.2f),
                        endBackgroundColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "金額",
                            style = MaterialTheme.typography.caption2,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (uiState.amount.isEmpty()) 
                                "點擊輸入" 
                            else 
                                "${uiState.amount} $chainSymbol",
                            style = MaterialTheme.typography.body2,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // 手續費顯示
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    onClick = { viewModel.showFeeOptions() },
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
                            text = "手續費",
                            style = MaterialTheme.typography.caption2,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${uiState.estimatedFee} sat/vB",
                            style = MaterialTheme.typography.body2,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = uiState.selectedFeeLevel.displayName,
                            style = MaterialTheme.typography.caption3,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            
            // 發送按鈕
            item {
                Button(
                    onClick = { viewModel.confirmTransaction() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    enabled = uiState.isValid && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.White
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            indicatorColor = chainColor
                        )
                    } else {
                        Text(
                            text = "確認發送",
                            color = chainColor,
                            fontWeight = FontWeight.Bold
                        )
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
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
    
    // 地址輸入對話框
    if (uiState.showAddressInput) {
        AddressInputDialog(
            currentAddress = uiState.recipientAddress,
            onAddressChange = viewModel::updateRecipientAddress,
            onDismiss = { viewModel.hideAddressInput() }
        )
    }
    
    // 金額輸入對話框
    if (uiState.showAmountInput) {
        AmountInputDialog(
            currentAmount = uiState.amount,
            chainSymbol = chainSymbol,
            maxAmount = viewModel.formatBalance(),
            onAmountChange = viewModel::updateAmount,
            onDismiss = { viewModel.hideAmountInput() }
        )
    }
    
    // 手續費選擇對話框
    if (uiState.showFeeOptions) {
        FeeOptionsDialog(
            currentLevel = uiState.selectedFeeLevel,
            onFeeSelected = viewModel::selectFeeLevel,
            onDismiss = { viewModel.hideFeeOptions() }
        )
    }
    
    // 確認對話框
    if (uiState.showConfirmation) {
        TransactionConfirmationDialog(
            recipientAddress = uiState.recipientAddress,
            amount = uiState.amount,
            chainSymbol = chainSymbol,
            estimatedFee = uiState.estimatedFee,
            onConfirm = { password ->
                viewModel.sendTransaction(password)
            },
            onDismiss = { viewModel.hideConfirmation() }
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
        LaunchedEffect(txHash) {
            onTransactionSent(txHash)
            viewModel.clearTransactionHash()
        }
    }
}

/**
 * 地址輸入對話框
 */
/**
 * 地址輸入對話框
 */
@Composable
private fun AddressInputDialog(
    currentAddress: String,
    onAddressChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = { Text("接收地址") },
            contentPadding = PaddingValues(16.dp)
        ) {
            val sampleAddresses = listOf(
                "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", 
                "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"
            )
            
            items(sampleAddresses) { address ->
                Chip(
                    label = { 
                        Text(
                            text = "${address.take(6)}...${address.takeLast(6)}",
                            style = MaterialTheme.typography.body2
                        ) 
                    },
                    secondaryLabel = {
                        Text(
                            text = "範例地址",
                            style = MaterialTheme.typography.caption3
                        )
                    },
                    onClick = { 
                        onAddressChange(address)
                        onDismiss()
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    label = { Text("取消") },
                    onClick = onDismiss,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

/**
 * 金額輸入對話框
 */
@Composable
private fun AmountInputDialog(
    currentAmount: String,
    chainSymbol: String,
    maxAmount: String,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = { Text("輸入金額") },
            contentPadding = PaddingValues(16.dp)
        ) {
            val amounts = listOf("0.0001", "0.001", "0.01", "0.1", "0.5")
            
            items(amounts) { amount ->
                Chip(
                    label = { Text("$amount $chainSymbol") },
                    onClick = { 
                        onAmountChange(amount)
                        onDismiss()
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    label = { Text("最大金額 ($maxAmount)") },
                    onClick = {
                        // 去除單位和符號，只保留數字
                        val maxVal = maxAmount.split(" ")[0]
                        onAmountChange(maxVal)
                        onDismiss()
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            item {
                Chip(
                    label = { Text("取消") },
                    onClick = onDismiss,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

/**
 * 手續費選擇對話框
 */
@Composable
private fun FeeOptionsDialog(
    currentLevel: UTXOSendViewModel.FeeLevel,
    onFeeSelected: (UTXOSendViewModel.FeeLevel) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = { Text("選擇手續費") },
            message = {
                Column {
                    UTXOSendViewModel.FeeLevel.values().forEach { level ->
                        Chip(
                            label = {
                                Column {
                                    Text(level.displayName)
                                    Text(
                                        text = level.description,
                                        style = MaterialTheme.typography.caption3,
                                        color = MaterialTheme.colors.secondary
                                    )
                                }
                            },
                            onClick = { 
                                onFeeSelected(level)
                                onDismiss()
                            },
                            colors = if (level == currentLevel) {
                                ChipDefaults.primaryChipColors()
                            } else {
                                ChipDefaults.secondaryChipColors()
                            }
                        )
                    }
                }
            },
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Chip(
                    label = { Text("取消") },
                    onClick = onDismiss,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

/**
 * 交易確認對話框
 */
/**
 * 交易確認對話框
 */
@Composable
private fun TransactionConfirmationDialog(
    recipientAddress: String,
    amount: String,
    chainSymbol: String,
    estimatedFee: Long,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        Alert(
            title = { Text("確認交易") },
            message = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("接收地址:")
                    Text(
                        text = "${recipientAddress.take(8)}...${recipientAddress.takeLast(8)}",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.secondary
                    )
                    
                    Text("金額:")
                    Text(
                        text = "$amount $chainSymbol",
                        style = MaterialTheme.typography.body1,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text("預估手續費:")
                    Text(
                        text = "$estimatedFee sat/vB",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 密碼輸入
                    CompactChip(
                        label = { 
                            Text(
                                text = if (password.isEmpty()) "輸入交易密碼" else "••••••",
                                style = MaterialTheme.typography.caption2
                            )
                        },
                        onClick = { 
                            // 這裡應該顯示密碼輸入對話框，為了簡化，我們先用模擬輸入
                            // 實際應用中應該使用另一個 TextInputDialog
                            password = "password" // 模擬輸入
                        },
                    )
                }
            },
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip(
                        label = { Text("取消") },
                        onClick = onDismiss,
                        colors = ChipDefaults.secondaryChipColors()
                    )
                    Chip(
                        label = { Text("發送") },
                        onClick = { onConfirm(password) },
                        colors = ChipDefaults.primaryChipColors(),
                        enabled = password.isNotEmpty()
                    )
                }
            }
        }
    }
}