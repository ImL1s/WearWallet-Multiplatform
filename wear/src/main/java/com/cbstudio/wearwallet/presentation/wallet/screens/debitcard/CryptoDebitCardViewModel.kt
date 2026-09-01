package com.cbstudio.wearwallet.presentation.wallet.screens.debitcard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Crypto Debit Card ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 17 - 最終編譯完成策略
 * 
 * TODO: Complex debit card operations temporarily disabled for maintenance
 * - All debit card functionality disabled
 * - Keep ViewModel structure consistent for future implementation
 * - Focus on compilation stability
 */
class CryptoDebitCardViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All debit card services disabled
    data class DebitCardUiState(
        val isLoading: Boolean = false,
        val cards: List<String> = emptyList(),
        val errorMessage: String? = null,
        val isMaintenanceMode: Boolean = true
    )
    
    private val _uiState = MutableStateFlow(DebitCardUiState())
    val uiState: StateFlow<DebitCardUiState> = _uiState.asStateFlow()
    
    fun createCard() {
        // Disabled in maintenance mode
    }
    
    fun freezeCard(cardId: String) {
        // Disabled in maintenance mode
    }
    
    fun topUpCard(amount: String) {
        // Disabled in maintenance mode
    }
    
    fun loadCards() {
        // Disabled in maintenance mode
    }
}