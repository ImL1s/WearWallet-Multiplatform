package com.cbstudio.wearwallet.presentation.wallet.screens.addressbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 聯絡人詳情 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 19 - 通訊錄管理維護模式修復
 */
class ContactDetailViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All contact detail operations disabled
    private val _contact = MutableStateFlow<String?>(null)
    val contact: StateFlow<String?> = _contact.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    fun loadContact(contactId: String) {
        // MAINTENANCE MODE: Contact loading disabled
        viewModelScope.launch {
            _isLoading.value = true
            _contact.value = "維護模式：聯絡人詳情功能暫時停用"
            _isLoading.value = false
        }
    }
    
    fun deleteContact(contactId: String) {
        // MAINTENANCE MODE: Contact deletion disabled
        viewModelScope.launch {
            _errorMessage.value = "維護模式：聯絡人刪除功能暫時停用"
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}