package com.cbstudio.mobile.ui.addressbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.domain.usecase.contact.GetAllContactsUseCase
import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.usecase.contact.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddressBookViewModel(
    private val getAllContactsUseCase: GetAllContactsUseCase
) : ViewModel() {
    
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    
    init {
        loadContacts()
    }
    
    private fun loadContacts() {
        viewModelScope.launch {
            getAllContactsUseCase().collect { contactList ->
                _contacts.value = contactList
            }
        }
    }
}