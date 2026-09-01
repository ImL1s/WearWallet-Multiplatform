package com.cbstudio.wearwallet.core.domain.usecase.contact

import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository

class UpdateContactUseCase(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(contact: Contact) {
        contactRepository.updateContact(contact)
    }
}