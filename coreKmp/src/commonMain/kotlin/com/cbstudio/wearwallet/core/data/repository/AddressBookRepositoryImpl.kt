package com.cbstudio.wearwallet.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.Address_book
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.SelectRecent
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressBookFilter
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressBookStatistics
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import com.cbstudio.wearwallet.core.domain.repository.AddressBookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import com.cbstudio.wearwallet.core.security.SideEffectTracker
import com.cbstudio.wearwallet.core.security.GlobalSideEffectTracker

/**
 * 使用 SQLDelight 實現的地址簿儲存庫
 * 提供完整的地址簿資料持久化功能
 */
class AddressBookRepositoryImpl(
    private val database: CoreWalletDatabase,
    private val sideEffectTracker: SideEffectTracker = GlobalSideEffectTracker.instance
) : AddressBookRepository {
    
    private val addressBookQueries = database.addressBookQueries
    
    override suspend fun createContact(contact: AddressContact): Result<AddressContact> {
        return try {
            println("🔧 RealAddressBookRepository.createContact 開始")
            println("   name: ${contact.name}")
            println("   address: ${contact.address}")
            println("   chainType: ${contact.chainType}")
            
            // 檢查地址是否已存在
            if (addressBookQueries.existsByAddressAndChain(contact.address, contact.chainType.name).executeAsOne()) {
                return Result.Failure(Exception("聯絡人地址已存在"))
            }
            
            // 插入到數據庫
            sideEffectTracker.onDbWrite()
            addressBookQueries.insert(
                name = contact.name,
                address = contact.address,
                chain_type = contact.chainType.name,
                chain_id = 1L, // 默認使用 chain_id = 1
                category = contact.category.name,
                tags = contact.tags.joinToString(","),
                notes = "", // 暫時設為空字符串
                is_favorite = 0L, // 暫時設為 false
                is_verified = 0L  // 暫時設為 false
            )
            
            // 獲取插入的聯絡人 ID
            val contactId = addressBookQueries.lastInsertRowId().executeAsOne()
            println("🔧 聯絡人 ID: $contactId")
            
            // 查詢並返回創建的聯絡人
            val createdContact = addressBookQueries.selectById(contactId).executeAsOne()
            println("🔧 聯絡人創建成功！")
            Result.Success(createdContact.let { convertToAddressContact(it) })
        } catch (e: Exception) {
            println("❌ RealAddressBookRepository.createContact 失敗: ${e.message}")
            e.printStackTrace()
            Result.Failure(e)
        }
    }
    
    override suspend fun getContact(id: String): Result<AddressContact?> {
        return try {
            val contactId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid contact ID")
            )
            val contact = addressBookQueries.selectById(contactId).executeAsOneOrNull()
            Result.Success(contact?.let { convertToAddressContact(it) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getAllContacts(): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.selectAll().executeAsList()
            Result.Success(contacts.map { contact -> convertToAddressContact(contact) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override fun observeAllContacts(): Flow<List<AddressContact>> {
        return addressBookQueries.selectAll()
            .asFlow()
            .mapToList(kotlinx.coroutines.Dispatchers.Default)
            .map { contacts ->
                contacts.map { contact -> convertToAddressContact(contact) }
            }
    }
    
    override suspend fun updateContact(contact: AddressContact): Result<AddressContact> {
        return try {
            val contactId = contact.id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid contact ID")
            )
            
            addressBookQueries.update(
                name = contact.name,
                category = contact.category.name,
                tags = contact.tags.joinToString(","),
                notes = "", // 暫時設為空字符串
                is_favorite = 0L, // 暫時設為 false
                is_verified = 0L, // 暫時設為 false
                id = contactId
            )
            
            // 查詢並返回更新的聯絡人
            val updatedContact = addressBookQueries.selectById(contactId).executeAsOne()
            Result.Success(updatedContact.let { convertToAddressContact(it) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteContact(id: String): Result<Unit> {
        return try {
            val contactId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid contact ID")
            )
            addressBookQueries.deleteById(contactId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun deleteContacts(ids: List<String>): Result<Unit> {
        return try {
            val contactIds = ids.mapNotNull { it.toLongOrNull() }
            if (contactIds.isEmpty()) {
                return Result.Success(Unit)
            }
            
            // SQLDelight 不支援 IN 查詢的參數列表，需要逐個刪除
            contactIds.forEach { id ->
                addressBookQueries.deleteById(id)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun searchContacts(query: String): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.searchContacts(query, query, query).executeAsList()
            Result.Success(contacts.map { contact -> convertToAddressContact(contact) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getContactsWithFilter(filter: AddressBookFilter): Result<List<AddressContact>> {
        return try {
            // 根據不同的篩選條件執行不同的查詢並直接轉換
            val addressContacts = when {
                filter.chainType != null -> {
                    addressBookQueries.selectByChainType(filter.chainType.name).executeAsList()
                        .map { convertToAddressContact(it) }
                }
                filter.category != null -> {
                    addressBookQueries.selectByCategory(filter.category.name).executeAsList()
                        .map { convertToAddressContact(it) }
                }
                filter.tag != null -> {
                    addressBookQueries.selectByTag(filter.tag).executeAsList()
                        .map { convertToAddressContact(it) }
                }
                filter.isFavorite == true -> {
                    addressBookQueries.selectFavorites().executeAsList()
                        .map { convertToAddressContact(it) }
                }
                filter.isRecent == true -> {
                    addressBookQueries.selectRecent(filter.limit?.toLong() ?: 10L).executeAsList()
                        .map { convertToAddressContact(it) }
                }
                filter.isFrequent == true -> {
                    addressBookQueries.selectFrequent(filter.limit?.toLong() ?: 10L).executeAsList()
                        .map { convertToAddressContact(it) }
                }
                !filter.searchQuery.isNullOrBlank() -> {
                    val query = filter.searchQuery
                    addressBookQueries.searchContacts(query, query, query).executeAsList()
                        .map { convertToAddressContact(it) }
                }
                else -> {
                    addressBookQueries.selectAll().executeAsList()
                        .map { convertToAddressContact(it) }
                }
            }
            
            Result.Success(addressContacts)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getFavoriteContacts(): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.selectFavorites().executeAsList()
            Result.Success(contacts.map { contact -> convertToAddressContact(contact) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getRecentContacts(limit: Int): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.selectRecent(limit.toLong()).executeAsList()
            Result.Success(contacts.map { contact -> convertToAddressContact(contact) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getFrequentContacts(limit: Int): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.selectFrequent(limit.toLong()).executeAsList()
            Result.Success(contacts.map { contact -> convertToAddressContact(contact) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getContactsByChain(chainType: ChainType): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.selectByChainType(chainType.name).executeAsList()
            Result.Success(contacts.map { contact -> convertToAddressContact(contact) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getContactsByTag(tag: String): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.selectByTag(tag).executeAsList()
            Result.Success(contacts.map { contact -> convertToAddressContact(contact) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun isAddressExists(address: String, chainType: ChainType): Result<Boolean> {
        return try {
            val exists = addressBookQueries.existsByAddressAndChain(address, chainType.name).executeAsOne()
            Result.Success(exists)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun validateAddress(address: String, chainType: ChainType): Result<Boolean> {
        return try {
            // 基本的地址格式驗證
            val isValid = when (chainType) {
                ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON, 
                ChainType.AVALANCHE, ChainType.ARBITRUM, ChainType.OPTIMISM -> {
                    // 以太坊格式地址：0x 開頭，42 個字符
                    address.startsWith("0x") && address.length == 42
                }
                else -> {
                    // 其他鏈類型的基本檢查
                    address.isNotBlank() && address.length > 10
                }
            }
            Result.Success(isValid)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun importContacts(contacts: List<AddressContact>): Result<Int> {
        return try {
            var importedCount = 0
            contacts.forEach { contact ->
                val result = createContact(contact)
                if (result is Result.Success) {
                    importedCount++
                }
            }
            Result.Success(importedCount)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun exportContactsAsJson(): Result<String> {
        return try {
            val contactsResult = getAllContacts()
            when (contactsResult) {
                is Result.Success -> {
                    val json = Json.encodeToString(contactsResult.data)
                    Result.Success(json)
                }
                is Result.Failure -> contactsResult
                is Result.Loading -> Result.Failure(Exception("Unexpected loading state"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun clearAllContacts(): Result<Unit> {
        return try {
            addressBookQueries.deleteAll()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getStatistics(): Result<AddressBookStatistics> {
        return try {
            val totalContacts = addressBookQueries.countAll().executeAsOne().toInt()
            val favoriteContacts = addressBookQueries.countFavorites().executeAsOne().toInt()
            
            // 目前僅實現基本統計，其他統計功能可後續擴展
            val statistics = AddressBookStatistics(
                totalContacts = totalContacts,
                favoriteContacts = favoriteContacts,
                verifiedContacts = 0, // TODO: 需要實現驗證統計
                contactsByChain = emptyMap(), // TODO: 需要實現鏈統計
                contactsByCategory = emptyMap(), // TODO: 需要實現分類統計
                totalUsageCount = 0,
                averageUsageCount = 0.0,
                mostUsedContact = null,
                recentlyAddedCount = 0, // TODO: 需要實現最近添加統計
                recentlyUsedCount = 0,  // TODO: 需要實現最近使用統計
                allTags = emptyList(),  // TODO: 需要實現標籤統計
                mostPopularTags = emptyList()
            )
            
            Result.Success(statistics)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun updateUsageStats(id: String): Result<Unit> {
        return try {
            val contactId = id.toLongOrNull() ?: return Result.Failure(
                Exception("Invalid contact ID")
            )
            addressBookQueries.updateUsageStats(contactId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getContactsByCategory(category: ContactCategory): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.selectByCategory(category.name).executeAsList()
            Result.Success(contacts.map { contact -> convertToAddressContact(contact) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getContactByAddress(address: String): Result<AddressContact?> {
        return try {
            val contacts = addressBookQueries.selectAll().executeAsList()
            val matchingContact = contacts.find { contact ->
                contact.address.equals(address, ignoreCase = true)
            }
            Result.Success(matchingContact?.let { convertToAddressContact(it) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getContactsByChainType(chainType: ChainType): Result<List<AddressContact>> {
        return try {
            val contacts = addressBookQueries.selectAll().executeAsList()
                .filter { it.chain_type == chainType.name }
            Result.Success(contacts.map { convertToAddressContact(it) })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun recordUsage(contactId: String): Result<Unit> {
        return try {
            addressBookQueries.updateUsageStats(contactId.toLong())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}

/**
 * 通用轉換函數：將任意具有相同結構的數據庫記錄轉換為領域模型 AddressContact
 */
private inline fun <T> convertToAddressContact(
    dbContact: T,
    getId: (T) -> Long,
    getName: (T) -> String,
    getAddress: (T) -> String,
    getChainType: (T) -> String,
    getChainId: (T) -> Long,
    getCategory: (T) -> String?,
    getTags: (T) -> String?,
    getNotes: (T) -> String?,
    getIsFavorite: (T) -> Long,
    getIsVerified: (T) -> Long,
    getUsageCount: (T) -> Long,
    getCreatedAt: (T) -> Long,
    getUpdatedAt: (T) -> Long,
    getLastUsedAt: (T) -> Long?
): AddressContact {
    return AddressContact(
        id = getId(dbContact).toString(),
        name = getName(dbContact),
        address = getAddress(dbContact),
        chainType = ChainType.valueOf(getChainType(dbContact)),
        chainId = getChainId(dbContact).toInt(),
        category = ContactCategory.valueOf(getCategory(dbContact) ?: "OTHER"),
        tags = if (getTags(dbContact).isNullOrEmpty()) emptyList() else getTags(dbContact)!!.split(",").map { it.trim() },
        notes = getNotes(dbContact) ?: "",
        isFavorite = getIsFavorite(dbContact) != 0L,
        isVerified = getIsVerified(dbContact) != 0L,
        usageCount = getUsageCount(dbContact).toInt(),
        createdAt = getCreatedAt(dbContact),
        updatedAt = getUpdatedAt(dbContact),
        lastUsedAt = getLastUsedAt(dbContact)
    )
}

/**
 * Address_book 轉換函數
 */
private fun convertToAddressContact(dbContact: Address_book): AddressContact {
    return convertToAddressContact(
        dbContact = dbContact,
        getId = { it.id },
        getName = { it.name },
        getAddress = { it.address },
        getChainType = { it.chain_type },
        getChainId = { it.chain_id },
        getCategory = { it.category },
        getTags = { it.tags },
        getNotes = { it.notes },
        getIsFavorite = { it.is_favorite },
        getIsVerified = { it.is_verified },
        getUsageCount = { it.usage_count },
        getCreatedAt = { it.created_at },
        getUpdatedAt = { it.updated_at },
        getLastUsedAt = { it.last_used_at }
    )
}

/**
 * SelectRecent 轉換函數
 */
private fun convertToAddressContact(dbContact: SelectRecent): AddressContact {
    return convertToAddressContact(
        dbContact = dbContact,
        getId = { it.id },
        getName = { it.name },
        getAddress = { it.address },
        getChainType = { it.chain_type },
        getChainId = { it.chain_id },
        getCategory = { it.category },
        getTags = { it.tags },
        getNotes = { it.notes },
        getIsFavorite = { it.is_favorite },
        getIsVerified = { it.is_verified },
        getUsageCount = { it.usage_count },
        getCreatedAt = { it.created_at },
        getUpdatedAt = { it.updated_at },
        getLastUsedAt = { it.last_used_at }
    )
}

/**
 * 擴展函數：將數據庫 Address_book 轉換為領域模型 AddressContact
 */
private fun Address_book.toAddressContact(): AddressContact = convertToAddressContact(this)