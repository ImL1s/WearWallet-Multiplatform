package com.cbstudio.wearwallet.core.domain.repository

import com.cbstudio.wearwallet.core.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getAllContacts(): Flow<List<Contact>>
    fun getContactById(contactId: String): Flow<Contact?>
    suspend fun getContactsByAddress(address: String): List<Contact>
    suspend fun insertContact(contact: Contact)
    suspend fun updateContact(contact: Contact)
    suspend fun deleteContact(contact: Contact)
    suspend fun deleteContact(contactId: String)
    suspend fun deleteAllContacts()
}