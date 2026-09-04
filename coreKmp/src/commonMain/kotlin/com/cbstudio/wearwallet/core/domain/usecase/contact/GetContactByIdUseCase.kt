package com.cbstudio.wearwallet.core.domain.usecase.contact

import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow

class GetContactByIdUseCase(
    private val contactRepository: ContactRepository
) {
    operator fun invoke(contactId: String): Flow<Contact?> {
        return contactRepository.getContactById(contactId)
    }
}