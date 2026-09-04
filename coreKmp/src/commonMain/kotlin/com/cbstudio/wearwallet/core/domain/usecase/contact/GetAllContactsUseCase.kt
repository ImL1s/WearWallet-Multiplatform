package com.cbstudio.wearwallet.core.domain.usecase.contact

import com.cbstudio.wearwallet.core.domain.model.Contact
import com.cbstudio.wearwallet.core.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow

class GetAllContactsUseCase(
    private val contactRepository: ContactRepository
) {
    operator fun invoke(): Flow<List<Contact>> {
        return contactRepository.getAllContacts()
    }
}