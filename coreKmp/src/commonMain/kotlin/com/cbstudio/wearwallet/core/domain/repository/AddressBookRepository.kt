package com.cbstudio.wearwallet.core.domain.repository

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressBookFilter
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressBookStatistics
import kotlinx.coroutines.flow.Flow

/**
 * 地址簿儲存庫介面
 * 
 * 定義地址簿 CRUD 操作的標準介面
 * 各平台需要實作此介面以提供具體的資料持久化方案
 */
interface AddressBookRepository {
    
    // CRUD 操作
    /**
     * 創建新聯絡人
     */
    suspend fun createContact(contact: AddressContact): Result<AddressContact>
    
    /**
     * 獲取單個聯絡人
     */
    suspend fun getContact(id: String): Result<AddressContact?>
    
    /**
     * 獲取所有聯絡人
     */
    suspend fun getAllContacts(): Result<List<AddressContact>>
    
    /**
     * 觀察所有聯絡人（即時更新）
     */
    fun observeAllContacts(): Flow<List<AddressContact>>
    
    /**
     * 更新聯絡人
     */
    suspend fun updateContact(contact: AddressContact): Result<AddressContact>
    
    /**
     * 刪除聯絡人
     */
    suspend fun deleteContact(id: String): Result<Unit>
    
    /**
     * 批量刪除聯絡人
     */
    suspend fun deleteContacts(ids: List<String>): Result<Unit>
    
    // 搜索和篩選
    /**
     * 搜索聯絡人
     */
    suspend fun searchContacts(query: String): Result<List<AddressContact>>
    
    /**
     * 根據篩選條件獲取聯絡人
     */
    suspend fun getContactsWithFilter(filter: AddressBookFilter): Result<List<AddressContact>>
    
    /**
     * 獲取收藏聯絡人
     */
    suspend fun getFavoriteContacts(): Result<List<AddressContact>>
    
    /**
     * 獲取最近使用的聯絡人
     */
    suspend fun getRecentContacts(limit: Int = 10): Result<List<AddressContact>>
    
    /**
     * 獲取常用聯絡人（根據使用次數）
     */
    suspend fun getFrequentContacts(limit: Int = 10): Result<List<AddressContact>>
    
    /**
     * 根據區塊鏈類型獲取聯絡人
     */
    suspend fun getContactsByChain(chainType: ChainType): Result<List<AddressContact>>
    
    /**
     * 根據分類獲取聯絡人
     */
    suspend fun getContactsByCategory(category: com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory): Result<List<AddressContact>>
    
    /**
     * 根據區塊鏈類型獲取聯絡人
     */
    suspend fun getContactsByChainType(chainType: ChainType): Result<List<AddressContact>>
    
    /**
     * 根據地址獲取聯絡人
     */
    suspend fun getContactByAddress(address: String): Result<AddressContact?>
    
    /**
     * 根據標籤獲取聯絡人
     */
    suspend fun getContactsByTag(tag: String): Result<List<AddressContact>>
    
    /**
     * 記錄聯絡人使用統計
     */
    suspend fun recordUsage(contactId: String): Result<Unit>
    
    /**
     * 檢查地址是否已存在
     */
    suspend fun isAddressExists(address: String, chainType: ChainType): Result<Boolean>
    
    // 驗證
    /**
     * 驗證地址格式
     */
    suspend fun validateAddress(address: String, chainType: ChainType): Result<Boolean>
    
    // 導入/導出
    /**
     * 導入聯絡人
     */
    suspend fun importContacts(contacts: List<AddressContact>): Result<Int>
    
    /**
     * 導出聯絡人為 JSON
     */
    suspend fun exportContactsAsJson(): Result<String>
    
    /**
     * 清除所有聯絡人
     */
    suspend fun clearAllContacts(): Result<Unit>
    
    // 統計
    /**
     * 獲取聯絡人統計資訊
     */
    suspend fun getStatistics(): Result<AddressBookStatistics>
    
    // 使用統計
    /**
     * 更新聯絡人使用統計
     */
    suspend fun updateUsageStats(id: String): Result<Unit>
}