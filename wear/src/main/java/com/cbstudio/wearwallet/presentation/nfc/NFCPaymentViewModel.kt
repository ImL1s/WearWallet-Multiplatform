package com.cbstudio.wearwallet.presentation.nfc

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NFC 支付 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 最終語法修復
 */
class NFCPaymentViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All NFC payment services disabled
    
    private val _isNfcEnabled = MutableStateFlow(false)
    val isNfcEnabled: StateFlow<Boolean> = _isNfcEnabled.asStateFlow()
    
    private val _paymentStatus = MutableStateFlow("維護模式")
    val paymentStatus: StateFlow<String> = _paymentStatus.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun enableNfc() {
        // MAINTENANCE MODE: No actual NFC enabling
    }
    
    fun processPayment(amount: Double) {
        // MAINTENANCE MODE: No actual payment processing
    }
    
    fun clearError() {
        _error.value = null
    }
}