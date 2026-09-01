package com.cbstudio.wearwallet.presentation.wallet.screens.addressbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 編輯聯絡人 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 19 - 通訊錄管理維護模式修復
 */
class EditContactViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All contact editing operations disabled
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    
    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address.asStateFlow()
    
    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    fun loadContact(contactId: String) {
        // MAINTENANCE MODE: Contact loading disabled
        viewModelScope.launch {
            _isLoading.value = true
            _name.value = "維護模式"
            _address.value = "聯絡人編輯功能暫時停用"
            _note.value = "維護模式"
            _isLoading.value = false
        }
    }
    
    fun updateName(newName: String) {
        _name.value = newName
    }
    
    fun updateAddress(newAddress: String) {
        _address.value = newAddress
    }
    
    fun updateNote(newNote: String) {
        _note.value = newNote
    }
    
    fun saveContact() {
        // MAINTENANCE MODE: Contact saving disabled
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = "維護模式：聯絡人更新功能暫時停用"
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}