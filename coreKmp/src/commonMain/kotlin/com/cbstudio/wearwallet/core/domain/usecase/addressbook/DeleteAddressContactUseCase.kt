package com.cbstudio.wearwallet.core.domain.usecase.addressbook

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.repository.AddressBookRepository

/**
 * 刪除地址聯絡人 UseCase
 */
class DeleteAddressContactUseCase(
    private val addressBookRepository: AddressBookRepository
) {
    /**
     * 執行刪除聯絡人
     *
     * @param contactId 聯絡人 ID
     */
    suspend operator fun invoke(
        contactId: String
    ): Result<Unit> {
        return addressBookRepository.deleteContact(contactId)
    }
}
