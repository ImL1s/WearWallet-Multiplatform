package com.cbstudio.wearwallet.presentation.wallet.screens.swap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cbstudio.wearwallet.presentation.wallet.screens.swap.screens.*
import org.koin.androidx.compose.koinViewModel

/**
 * Swap Screen - Single screen with internal navigation
 * 
 * Uses internal state-based navigation instead of NavHost
 * to avoid conflicts with Wear OS navigation.
 */
@Composable
fun SwapScreen(
    onNavigateBack: () -> Unit = {}
) {
    val viewModel: SwapViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val availableTokens by viewModel.availableTokens.collectAsState()
    
    // Internal navigation state
    var currentScreen by remember { mutableStateOf(SwapScreenState.SELECT_FROM) }
    
    when (currentScreen) {
        SwapScreenState.SELECT_FROM -> {
            SelectTokenScreen(
                title = "From",
                tokens = availableTokens,
                onTokenSelected = { token ->
                    viewModel.setFromToken(token)
                    currentScreen = SwapScreenState.SELECT_TO
                }
            )
        }
        
        SwapScreenState.SELECT_TO -> {
            SelectTokenScreen(
                title = "To",
                tokens = availableTokens,
                onTokenSelected = { token ->
                    viewModel.setToToken(token)
                    currentScreen = SwapScreenState.AMOUNT_INPUT
                }
            )
        }
        
        SwapScreenState.AMOUNT_INPUT -> {
            uiState.fromToken?.let { fromToken ->
                AmountInputScreen(
                    fromToken = fromToken,
                    balance = uiState.fromTokenBalance,
                    onAmountConfirmed = { amount ->
                        viewModel.setAmount(amount)
                        viewModel.getQuote()
                        currentScreen = SwapScreenState.QUOTE_CONFIRM
                    }
                )
            }
        }
        
        SwapScreenState.QUOTE_CONFIRM -> {
            when (uiState.status) {
                SwapStatus.SUCCESS -> {
                    currentScreen = SwapScreenState.SUCCESS
                }
                SwapStatus.FAILED -> {
                    currentScreen = SwapScreenState.FAILED
                }
                SwapStatus.EXECUTING, SwapStatus.WAITING_CONFIRMATION -> {
                    currentScreen = SwapScreenState.PROGRESS
                }
                else -> {
                    QuoteConfirmScreen(
                        uiState = uiState,
                        onConfirm = {
                            currentScreen = SwapScreenState.UNLOCK
                        },
                        onCancel = { 
                            currentScreen = SwapScreenState.AMOUNT_INPUT 
                        }
                    )
                }
            }
        }

        SwapScreenState.UNLOCK -> {
            when (uiState.status) {
                SwapStatus.EXECUTING, SwapStatus.WAITING_CONFIRMATION, SwapStatus.SUCCESS -> {
                    currentScreen = SwapScreenState.PROGRESS
                }
                else -> {
                    SwapUnlockScreen(
                        onSuccess = { /* handled by SwapViewModel state change */ },
                        onCancel = { 
                            // Returning to QUOTE_CONFIRM
                            currentScreen = SwapScreenState.QUOTE_CONFIRM 
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
        
        SwapScreenState.PROGRESS -> {
            when (uiState.status) {
                SwapStatus.SUCCESS -> {
                    currentScreen = SwapScreenState.SUCCESS
                }
                SwapStatus.FAILED -> {
                    currentScreen = SwapScreenState.FAILED
                }
                else -> {
                        SwapProgressScreen(
                        status = when (uiState.status) {
                            SwapStatus.UNLOCKING -> "Unlocking..."
                            SwapStatus.EXECUTING -> "Submitting..."
                            SwapStatus.WAITING_CONFIRMATION -> "Confirming..."
                            SwapStatus.GETTING_QUOTE -> "Getting quote..."
                            else -> "Processing..."
                        },
                        fromChain = uiState.fromToken?.blockchain ?: "",
                        toChain = uiState.toToken?.blockchain ?: ""
                    )
                }
            }
        }
        
        SwapScreenState.SUCCESS -> {
            SwapSuccessScreen(
                outputAmount = uiState.quote?.route?.outputAmount ?: "",
                outputSymbol = uiState.toToken?.symbol ?: "",
                txHash = uiState.txHash,
                onDone = {
                    viewModel.reset()
                    onNavigateBack()
                }
            )
        }
        
        SwapScreenState.FAILED -> {
            SwapFailedScreen(
                error = uiState.error ?: "Unknown error",
                onRetry = {
                    viewModel.reset()
                    currentScreen = SwapScreenState.AMOUNT_INPUT
                },
                onCancel = {
                    viewModel.reset()
                    onNavigateBack()
                }
            )
        }
    }
}

/**
 * Internal screen states for swap flow
 */
enum class SwapScreenState {
    SELECT_FROM,
    SELECT_TO,
    AMOUNT_INPUT,
    QUOTE_CONFIRM,
    UNLOCK,
    PROGRESS,
    SUCCESS,
    FAILED
}
