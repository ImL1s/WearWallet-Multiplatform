package com.cbstudio.mobile.ui.addressbook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.mobile.R
import com.cbstudio.wearwallet.core.domain.usecase.contact.GetContactByIdUseCase
import com.cbstudio.wearwallet.core.domain.usecase.contact.UpdateContactUseCase
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.usecase.contact.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class EditContactUiState {
    object Loading : EditContactUiState()
    data class Editing(
        val contact: Contact,
        val name: String,
        val address: String,
        val chainType: ChainType,
        val note: String
    ) : EditContactUiState()
    object Success : EditContactUiState()
    data class Error(val message: String) : EditContactUiState()
}

class EditContactViewModel(
    private val context: Context,
    private val getContactByIdUseCase: GetContactByIdUseCase,
    private val updateContactUseCase: UpdateContactUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<EditContactUiState>(EditContactUiState.Loading)
    val uiState: StateFlow<EditContactUiState> = _uiState.asStateFlow()
    
    private var originalContact: Contact? = null
    
    fun loadContact(contactId: String) {
        viewModelScope.launch {
            try {
                getContactByIdUseCase(contactId).collect { contact ->
                    if (contact != null) {
                        originalContact = contact
                        _uiState.value = EditContactUiState.Editing(
                            contact = contact,
                            name = contact.name,
                            address = contact.address,
                            chainType = contact.chainType,
                            note = contact.note ?: ""
                        )
                    } else {
                        _uiState.value = EditContactUiState.Error(context.getString(R.string.contact_not_found))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = EditContactUiState.Error(
                    context.getString(R.string.error_load_contact_failed, e.message ?: "")
                )
            }
        }
    }
    
    fun updateName(name: String) {
        val currentState = _uiState.value
        if (currentState is EditContactUiState.Editing) {
            _uiState.value = currentState.copy(name = name)
        }
    }
    
    fun updateNote(note: String) {
        val currentState = _uiState.value
        if (currentState is EditContactUiState.Editing) {
            _uiState.value = currentState.copy(note = note)
        }
    }
    
    suspend fun saveContact() {
        val currentState = _uiState.value
        if (currentState is EditContactUiState.Editing) {
            if (currentState.name.length < 2) return
            
            viewModelScope.launch {
                try {
                    val updatedContact = currentState.contact.copy(
                        name = currentState.name.trim(),
                        note = currentState.note.trim().takeIf { it.isNotBlank() },
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    updateContactUseCase(updatedContact)
                    _uiState.value = EditContactUiState.Success
                } catch (e: Exception) {
                    _uiState.value = EditContactUiState.Error(
                        context.getString(R.string.error_update_contact_failed, e.message ?: "")
                    )
                }
            }
        }
    }
}