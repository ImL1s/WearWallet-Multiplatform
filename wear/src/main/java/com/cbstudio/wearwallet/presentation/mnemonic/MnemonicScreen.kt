package com.cbstudio.wearwallet.presentation.mnemonic

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.presentation.common.CommonButton

@Composable
fun MnemonicScreen(
    onMnemonicSet: () -> Unit,
    onImportClick: () -> Unit,
    viewModel: MnemonicViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onMnemonicSet()
        }
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.wallet_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            CommonButton(
                text = stringResource(R.string.create_wallet),
                onClick = { viewModel.createNewWallet() }
            )
        }

        item {
            CommonButton(
                text = stringResource(R.string.import_wallet),
                onClick = { onImportClick() }
            )
        }
    }

    // 顯示錯誤對話框 - Wear OS AlertDialog 需要 visible 參數
    AlertDialog(
        visible = uiState.error != null,
        onDismissRequest = { viewModel.clearError() },
        title = { Text(stringResource(R.string.error)) },
        text = { 
            uiState.error?.let { 
                Text(it) 
            }
        }
    )

    // 顯示載入指示器
    if (uiState.isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize()
        )
    }
}

