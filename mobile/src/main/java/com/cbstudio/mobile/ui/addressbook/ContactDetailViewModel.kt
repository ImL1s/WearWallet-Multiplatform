package com.cbstudio.mobile.ui.addressbook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.mobile.R
import com.cbstudio.wearwallet.core.domain.usecase.contact.DeleteContactUseCase
import com.cbstudio.wearwallet.core.domain.usecase.contact.GetContactByIdUseCase
import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.usecase.contact.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ContactDetailUiState {
    object Loading : ContactDetailUiState()
    data class Success(val contact: Contact) : ContactDetailUiState()
    data class Error(val message: String) : ContactDetailUiState()
    object Deleted : ContactDetailUiState()
}

class ContactDetailViewModel(
    private val context: Context,
    private val getContactByIdUseCase: GetContactByIdUseCase,
    private val deleteContactUseCase: DeleteContactUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ContactDetailUiState>(ContactDetailUiState.Loading)
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()
    
    fun loadContact(contactId: String) {
        viewModelScope.launch {
            try {
                getContactByIdUseCase(contactId).collect { contact ->
                    if (contact != null) {
                        _uiState.value = ContactDetailUiState.Success(contact)
                    } else {
                        _uiState.value = ContactDetailUiState.Error(context.getString(R.string.contact_not_found))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ContactDetailUiState.Error(
                    context.getString(R.string.error_load_contact_failed, e.message ?: "")
                )
            }
        }
    }
    
    suspend fun deleteContact(contactId: String) {
        viewModelScope.launch {
            try {
                deleteContactUseCase(contactId)
                _uiState.value = ContactDetailUiState.Deleted
            } catch (e: Exception) {
                // 如果刪除失敗，重新載入聯絡人
                loadContact(contactId)
            }
        }
    }
}