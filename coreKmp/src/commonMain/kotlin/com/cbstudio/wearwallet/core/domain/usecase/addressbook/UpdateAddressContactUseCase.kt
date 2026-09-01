package com.cbstudio.wearwallet.core.domain.usecase.addressbook

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.repository.AddressBookRepository

/**
 * 更新地址聯絡人 UseCase
 */
class UpdateAddressContactUseCase(
    private val addressBookRepository: AddressBookRepository
) {
    /**
     * 執行更新聯絡人
     *
     * @param contact 要更新的聯絡人物件
     */
    suspend operator fun invoke(
        contact: AddressContact
    ): Result<AddressContact> {
        return addressBookRepository.updateContact(contact)
    }
}