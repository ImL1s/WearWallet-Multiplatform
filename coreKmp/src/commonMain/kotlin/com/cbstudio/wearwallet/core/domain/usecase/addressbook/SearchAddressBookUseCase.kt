package com.cbstudio.wearwallet.core.domain.usecase.addressbook

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressBookFilter
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressBookStatistics
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.AddressBookRepository
import kotlinx.coroutines.flow.Flow

/**
 * 地址簿搜尋和查詢用例
 */
class SearchAddressBookUseCase(
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
     * 搜尋聯絡人
     */
    suspend fun searchContacts(query: String): Result<List<AddressContact>> {
        return if (query.isBlank()) {
            addressBookRepository.getAllContacts()
        } else {
            addressBookRepository.searchContacts(query)
        }
    }
    
    /**
     * 根據篩選條件獲取聯絡人
     */
    suspend fun getContactsWithFilter(filter: AddressBookFilter): Result<List<AddressContact>> {
        return addressBookRepository.getContactsWithFilter(filter)
    }
    
    /**
     * 獲取收藏聯絡人
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
     * 獲取常用聯絡人
     */
    suspend fun getFrequentContacts(limit: Int = 10): Result<List<AddressContact>> {
        return addressBookRepository.getFrequentContacts(limit)
    }
    
    /**
     * 根據區塊鏈類型獲取聯絡人
     */
    suspend fun getContactsByChain(chainType: ChainType): Result<List<AddressContact>> {
        return addressBookRepository.getContactsByChain(chainType)
    }
    
    /**
     * 根據分類獲取聯絡人
     */
    suspend fun getContactsByCategory(category: ContactCategory): Result<List<AddressContact>> {
        return addressBookRepository.getContactsByCategory(category)
    }
    
    /**
     * 根據標籤獲取聯絡人
     */
    suspend fun getContactsByTag(tag: String): Result<List<AddressContact>> {
        return addressBookRepository.getContactsByTag(tag)
    }
    
    /**
     * 根據地址獲取聯絡人
     */
    suspend fun getContactByAddress(address: String): Result<AddressContact?> {
        return addressBookRepository.getContactByAddress(address)
    }
    
    /**
     * 檢查地址是否已存在
     */
    suspend fun isAddressExists(address: String, chainType: ChainType): Result<Boolean> {
        return addressBookRepository.isAddressExists(address, chainType)
    }
    
    /**
     * 獲取地址簿統計資訊
     */
    suspend fun getStatistics(): Result<AddressBookStatistics> {
        return addressBookRepository.getStatistics()
    }
    
    /**
     * 驗證地址格式
     */
    suspend fun validateAddress(address: String, chainType: ChainType): Result<Boolean> {
        return addressBookRepository.validateAddress(address, chainType)
    }
    
    /**
     * 記錄聯絡人使用
     */
    suspend fun recordUsage(contactId: String): Result<Unit> {
        return addressBookRepository.recordUsage(contactId)
    }
}