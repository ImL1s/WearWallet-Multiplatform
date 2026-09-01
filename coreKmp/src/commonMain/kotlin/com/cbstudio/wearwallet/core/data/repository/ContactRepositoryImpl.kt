package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * ContactRepository 的簡化實現
 * 暫時使用記憶體儲存，後續可以改為使用 SQLDelight
 */
class ContactRepositoryImpl : ContactRepository {
    private val contacts = mutableListOf<Contact>()
    
    override fun getAllContacts(): Flow<List<Contact>> {
        return flowOf(contacts.toList())
    }
    
    override fun getContactById(contactId: String): Flow<Contact?> {
        return flowOf(contacts.find { it.id == contactId })
    }
    
    override suspend fun getContactsByAddress(address: String): List<Contact> {
        return contacts.filter { it.address.equals(address, ignoreCase = true) }
    }
    
    override suspend fun insertContact(contact: Contact) {
        contacts.removeAll { it.id == contact.id }
        contacts.add(contact)
    }
    
    override suspend fun updateContact(contact: Contact) {
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            contacts[index] = contact
        }
    }
    
    override suspend fun deleteContact(contact: Contact) {
        contacts.removeAll { it.id == contact.id }
    }
    
    override suspend fun deleteContact(contactId: String) {
        contacts.removeAll { it.id == contactId }
    }
    
    override suspend fun deleteAllContacts() {
        contacts.clear()
    }
}