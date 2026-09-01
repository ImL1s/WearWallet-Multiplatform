package com.cbstudio.mobile.ui.addressbook

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.mobile.R
import com.cbstudio.wearwallet.core.domain.usecase.contact.AddContactUseCase
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.usecase.contact.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AddContactUiState {
    object Idle : AddContactUiState()
    object Loading : AddContactUiState()
    object Success : AddContactUiState()
    data class Error(val message: String) : AddContactUiState()
}

class AddContactViewModel(
    private val context: Context,
    private val addContactUseCase: AddContactUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<AddContactUiState>(AddContactUiState.Idle)
    val uiState: StateFlow<AddContactUiState> = _uiState.asStateFlow()
    
    var name by mutableStateOf("")
        private set
    
    var address by mutableStateOf("")
        private set
    
    var chainType by mutableStateOf(ChainType.ETHEREUM)
        private set
    
    var note by mutableStateOf("")
        private set
    
    fun updateName(value: String) {
        name = value
    }
    
    fun updateAddress(value: String) {
        address = value.trim()
    }
    
    fun updateChainType(value: ChainType) {
        chainType = value
    }
    
    fun updateNote(value: String) {
        note = value
    }
    
    fun isAddressValid(): Boolean {
        // 簡單的以太坊地址驗證
        return address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
    }
    
    fun isFormValid(): Boolean {
        return name.length >= 2 && isAddressValid()
    }
    
    suspend fun saveContact() {
        if (!isFormValid()) return
        
        _uiState.value = AddContactUiState.Loading
        
        viewModelScope.launch {
            try {
                val contact = Contact(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    address = address.lowercase(),
                    chainType = chainType,
                    note = note.trim().takeIf { it.isNotBlank() }
                )
                
                addContactUseCase(contact)
                _uiState.value = AddContactUiState.Success
            } catch (e: Exception) {
                _uiState.value = AddContactUiState.Error(
                    context.getString(R.string.error_save_contact, e.message ?: "")
                )
            }
        }
    }
}