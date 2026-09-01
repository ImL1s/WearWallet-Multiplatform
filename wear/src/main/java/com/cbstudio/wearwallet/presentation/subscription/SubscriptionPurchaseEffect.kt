package com.cbstudio.wearwallet.presentation.subscription

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * 處理訂閱購買的副作用
 * 在 Composable 中使用此函數來處理需要 Activity 的購買流程
 */
@Composable
fun SubscriptionPurchaseEffect(
    viewModel: SubscriptionManagementViewModel,
    uiState: SubscriptionManagementUiState
) {
    val activity = LocalContext.current as? Activity
    val coroutineScope = rememberCoroutineScope()
    
    // 監聽待購買的產品 ID
    LaunchedEffect(uiState.pendingPurchaseProductId) {
        val productId = uiState.pendingPurchaseProductId
        if (productId != null && activity != null) {
            coroutineScope.launch {
                viewModel.executePurchase(activity, productId)
            }
        }
    }
}
