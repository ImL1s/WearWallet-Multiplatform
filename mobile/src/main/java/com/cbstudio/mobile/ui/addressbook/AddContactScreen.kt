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
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddContactViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 處理保存結果
    LaunchedEffect(uiState) {
        when (uiState) {
            is AddContactUiState.Success -> {
                onNavigateBack()
            }
            is AddContactUiState.Error -> {
                val errorState = uiState as AddContactUiState.Error
                snackbarHostState.showSnackbar(
                    message = errorState.message,
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
                    Text(text = stringResource(R.string.add_contact))
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
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.saveContact()
                            }
                        },
                        enabled = viewModel.isFormValid()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.save_contact)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
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
                value = viewModel.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text(stringResource(R.string.contact_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = viewModel.name.isNotBlank() && viewModel.name.length < 2
            )
            
            // 地址輸入
            OutlinedTextField(
                value = viewModel.address,
                onValueChange = { viewModel.updateAddress(it) },
                label = { Text(stringResource(R.string.wallet_address)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = viewModel.address.isNotBlank() && !viewModel.isAddressValid()
            )
            
            // 鏈選擇
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = getChainDisplayName(viewModel.chainType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.blockchain)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ChainType.values().forEach { chain ->
                        DropdownMenuItem(
                            text = { Text(getChainDisplayName(chain)) },
                            onClick = {
                                viewModel.updateChainType(chain)
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            // 備註輸入
            OutlinedTextField(
                value = viewModel.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
            
            // 表單驗證提示
            if (!viewModel.isFormValid() && viewModel.name.isNotBlank() && viewModel.address.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (viewModel.name.length < 2) {
                            Text(
                                text = "聯絡人名稱至少需要 2 個字元",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        if (!viewModel.isAddressValid()) {
                            Text(
                                text = "請輸入有效的錢包地址（0x 開頭的 42 位字元）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 保存按鈕
            Button(
                onClick = {
                    coroutineScope.launch {
                        viewModel.saveContact()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.isFormValid()
            ) {
                Text(text = stringResource(R.string.save_contact))
            }
        }
        
        // 載入中的進度指示器
        if (uiState is AddContactUiState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun getChainDisplayName(chainType: ChainType): String {
    return when (chainType) {
        ChainType.ETHEREUM -> "Ethereum"
        ChainType.BSC -> "Binance Smart Chain"
        ChainType.POLYGON -> "Polygon"
        ChainType.CRONOS -> "Cronos"
        else -> chainType.name
    }
}