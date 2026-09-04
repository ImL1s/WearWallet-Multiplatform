package com.cbstudio.wearwallet.presentation.wallet.screens.main.token

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState

/**
 * 新增自訂代幣畫面 - MAINTENANCE MODE
 * ULTRATHINK Phase 19 - 代幣管理維護模式修復
 */
@Composable
fun AddCustomTokenScreen(
    chain: String = "Ethereum",
    onTokenAdded: () -> Unit = {},
    onCancel: () -> Unit = {},
    viewModel: AddCustomTokenViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()

    LaunchedEffect(chain) {
        viewModel.updateChain(chain)
    }
    
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onTokenAdded()
            viewModel.resetState()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = "新增自訂代幣",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                OutlinedTextField(
                    value = uiState.contractAddress,
                    onValueChange = { viewModel.updateContractAddress(it) },
                    label = { Text("合約地址") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = uiState.symbol,
                    onValueChange = { viewModel.updateSymbol(it) },
                    label = { Text("代幣符號") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true
                )
            }
            
            item {
                OutlinedTextField(
                    value = uiState.decimals,
                    onValueChange = { viewModel.updateDecimals(it) },
                    label = { Text("小數位數") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            item {
                Button(
                    onClick = { viewModel.addToken() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("新增")
                    }
                }
            }
            
            item {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text("取消")
                }
            }
            
            if (uiState.errorMessage != null) {
                 item {
                     Text(
                         text = uiState.errorMessage!!,
                         color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall,
                         modifier = Modifier.padding(8.dp)
                     )
                 }
            }
        }
    }
}