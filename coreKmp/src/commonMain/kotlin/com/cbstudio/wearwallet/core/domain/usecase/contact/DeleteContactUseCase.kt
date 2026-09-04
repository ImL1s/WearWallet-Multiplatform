package com.cbstudio.wearwallet.core.domain.usecase.contact

import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository

class DeleteContactUseCase(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(contact: Contact) {
        contactRepository.deleteContact(contact)
    }
    
    suspend operator fun invoke(contactId: String) {
        contactRepository.deleteContact(contactId)
    }
}