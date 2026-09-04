package com.cbstudio.mobile.ui.addressbook

// Chain import removed - using ChainType instead
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.cbstudio.mobile.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactScreen(
    contactId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditContactViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(contactId) {
        viewModel.loadContact(contactId)
    }
    
    // 處理更新結果
    LaunchedEffect(uiState) {
        when (uiState) {
            is EditContactUiState.Success -> {
                onNavigateBack()
            }
            is EditContactUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = (uiState as EditContactUiState.Error).message,
                    duration = SnackbarDuration.Short
                )
            }
            else -> {}
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(text = stringResource(R.string.edit_contact))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState is EditContactUiState.Editing) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.saveContact()
                                }
                            },
                            enabled = (uiState as EditContactUiState.Editing).name.length >= 2
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.save_changes)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            is EditContactUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is EditContactUiState.Editing -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 名稱輸入
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text(stringResource(R.string.contact_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.name.isNotBlank() && state.name.length < 2
                    )
                    
                    // 地址顯示（不可編輯）
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.wallet_address)) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false,
                        singleLine = true
                    )
                    
                    // 鏈顯示（不可編輯）
                    OutlinedTextField(
                        value = getChainDisplayName(state.chainType),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.blockchain)) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false,
                        singleLine = true
                    )
                    
                    // 備註輸入
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = { viewModel.updateNote(it) },
                        label = { Text(stringResource(R.string.note_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    
                    // 資訊提示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "提示",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "• 地址和區塊鏈無法更改\n• 如需變更地址或鏈，請刪除後重新新增聯絡人",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // 儲存按鈕
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.saveContact()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.name.length >= 2
                    ) {
                        Text(text = stringResource(R.string.save_changes))
                    }
                }
            }
            
            is EditContactUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = onNavigateBack) {
                            Text(stringResource(R.string.go_back))
                        }
                    }
                }
            }
            
            else -> {}
        }
    }
}

@Composable
private fun getChainDisplayName(chainType: com.cbstudio.wearwallet.core.domain.model.ChainType): String {
    return when (chainType) {
        com.cbstudio.wearwallet.core.domain.model.ChainType.ETHEREUM -> "Ethereum"
        com.cbstudio.wearwallet.core.domain.model.ChainType.BSC -> "Binance Smart Chain"
        com.cbstudio.wearwallet.core.domain.model.ChainType.POLYGON -> "Polygon"
        com.cbstudio.wearwallet.core.domain.model.ChainType.CRONOS -> "Cronos"
        else -> chainType.name
    }
}