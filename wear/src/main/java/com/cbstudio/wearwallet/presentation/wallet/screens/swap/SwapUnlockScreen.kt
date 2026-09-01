package com.cbstudio.wearwallet.presentation.wallet.screens.swap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cbstudio.wearwallet.presentation.wallet.components.PasswordInputDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun SwapUnlockScreen(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SwapViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // We observe the unlockState from ViewModel. 
    // Assuming SwapViewModel has properties for this. If not, I will add them in the next step.
    
    PasswordInputDialog(
        title = "授權交易", // Authorize Transaction
        message = "請輸入密碼以簽署交易", // Enter password to sign transaction
        isVisible = true,
        onPasswordSubmit = { password ->
            viewModel.unlockWallet(password)
        },
        onDismiss = onCancel,
        error = uiState.error, // Assuming error is exposed in uiState
        isLoading = uiState.status == SwapStatus.UNLOCKING // Derived from status
    )

    // Handle success navigation via side effect in Navigation graph, 
    // OR ViewModel can trigger a one-time event. 
    // For now, assume ViewModel updates state or triggers navigation callback.
}
