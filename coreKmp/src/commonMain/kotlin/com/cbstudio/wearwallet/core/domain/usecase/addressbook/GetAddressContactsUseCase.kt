package com.cbstudio.wearwallet.core.domain.usecase.addressbook

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressBookFilter
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import com.cbstudio.wearwallet.core.domain.repository.AddressBookRepository
import kotlinx.coroutines.flow.Flow

/**
 * 獲取地址聯絡人業務邏輯
 */
class GetAddressContactsUseCase(
    private val addressBookRepository: AddressBookRepository
) {
    /**
     * 獲取所有聯絡人
     */
    suspend fun getAllContacts(): Result<List<AddressContact>> {
        return addressBookRepository.getAllContacts()
    }
    
    /**
     * 觀察所有聯絡人變化
     */
    fun observeAllContacts(): Flow<List<AddressContact>> {
        return addressBookRepository.observeAllContacts()
    }
    
    /**
     * 根據分類獲取聯絡人
     */
    suspend fun getContactsByCategory(category: ContactCategory): Result<List<AddressContact>> {
        return addressBookRepository.getContactsByCategory(category)
    }
    
    /**
     * 根據區塊鏈類型獲取聯絡人
     */
    suspend fun getContactsByChainType(chainType: ChainType): Result<List<AddressContact>> {
        return addressBookRepository.getContactsByChain(chainType)
    }
    
    /**
     * 獲取收藏的聯絡人
     */
    suspend fun getFavoriteContacts(): Result<List<AddressContact>> {
        return addressBookRepository.getFavoriteContacts()
    }
    
    /**
     * 獲取最近使用的聯絡人
     */
    suspend fun getRecentContacts(limit: Int = 10): Result<List<AddressContact>> {
        return addressBookRepository.getRecentContacts(limit)
    }
    
    /**
     * 搜索聯絡人
     */
    suspend fun searchContacts(query: String): Result<List<AddressContact>> {
        if (query.isBlank()) {
            return getAllContacts()
        }
        return addressBookRepository.searchContacts(query)
    }
    
    /**
     * 根據過濾條件獲取聯絡人
     */
    suspend fun getContactsWithFilter(filter: AddressBookFilter): Result<List<AddressContact>> {
        return addressBookRepository.getContactsWithFilter(filter)
    }
    
    /**
     * 根據ID獲取特定聯絡人
     */
    suspend fun getContactById(id: String): Result<AddressContact?> {
        return addressBookRepository.getContact(id)
    }
    
    /**
     * 根據地址獲取聯絡人
     */
    suspend fun getContactByAddress(address: String, chainType: ChainType): Result<AddressContact?> {
        return addressBookRepository.getContactByAddress(address)
    }
    
    /**
     * 獲取常用聯絡人（根據使用次數排序）
     */
    suspend fun getFrequentContacts(limit: Int = 5): Result<List<AddressContact>> {
        return addressBookRepository.getFrequentContacts(limit)
    }
}