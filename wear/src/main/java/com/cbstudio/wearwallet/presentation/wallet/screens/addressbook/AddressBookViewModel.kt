package com.cbstudio.wearwallet.presentation.wallet.screens.addressbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.*
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 通訊錄 ViewModel
 * 使用 coreKmp 的 AddressContact 和相關 UseCase
 */
class AddressBookViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val getAddressContactsUseCase: GetAddressContactsUseCase by inject()
    private val searchAddressBookUseCase: SearchAddressBookUseCase by inject()
    private val addAddressContactUseCase: AddAddressContactUseCase by inject()
    private val updateAddressContactUseCase: UpdateAddressContactUseCase by inject()
    private val deleteAddressContactUseCase: DeleteAddressContactUseCase by inject()
    
    data class AddressBookUiState(
        val contacts: List<AddressContact> = emptyList(),
        val filteredContacts: List<AddressContact> = emptyList(),
        val searchQuery: String = "",
        val currentChain: ChainType = ChainType.ETHEREUM,
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    private val _uiState = MutableStateFlow(AddressBookUiState())
    val uiState: StateFlow<AddressBookUiState> = _uiState.asStateFlow()
    
    init {
        // 從全局狀態管理器獲取當前鏈
        val initialChain = ChainStateManager.getCurrentChain()
        _uiState.update { it.copy(currentChain = initialChain) }
        
        loadContacts()
        
        // 監聽鏈狀態變化
        viewModelScope.launch {
            ChainStateManager.currentChain.collect { chainType ->
                if (chainType != _uiState.value.currentChain) {
                    _uiState.update { it.copy(currentChain = chainType) }
                    loadContactsForChain(chainType)
                }
            }
        }
    }
    
    /**
     * 載入聯絡人列表
     */
    private fun loadContacts() {
        val currentChain = _uiState.value.currentChain
        loadContactsForChain(currentChain)
    }
    
    /**
     * 載入特定鏈的聯絡人
     */
    private fun loadContactsForChain(chain: ChainType) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // 載入該鏈的聯絡人
                val result = getAddressContactsUseCase.getContactsByChainType(chain)
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                contacts = result.data,
                                filteredContacts = result.data,
                                currentChain = chain,
                                isLoading = false,
                                error = null
                            )
                        }
                        Timber.d("載入聯絡人成功: ${result.data.size} 個")
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "載入聯絡人失敗"
                            )
                        }
                        Timber.e(result.exception, "載入聯絡人失敗")
                    }
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "載入聯絡人時發生錯誤"
                    )
                }
                Timber.e(e, "載入聯絡人異常")
            }
        }
    }
    
    /**
     * 搜尋查詢變更
     */
    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        if (query.isEmpty()) {
            _uiState.update { 
                it.copy(filteredContacts = it.contacts)
            }
        } else {
            viewModelScope.launch {
                val result = searchAddressBookUseCase.searchContacts(query)
                when (result) {
                    is Result.Success -> {
                        // 過濾當前鏈的聯絡人
                        val filteredContacts = result.data.filter { 
                            it.chainType == _uiState.value.currentChain 
                        }
                        _uiState.update { 
                            it.copy(filteredContacts = filteredContacts)
                        }
                    }
                    is Result.Failure -> {
                        Timber.e(result.exception, "搜尋聯絡人失敗")
                    }
                    is Result.Loading -> {
                        // 保持載入狀態
                    }
                }
            }
        }
    }
    
    /**
     * 切換收藏狀態
     */
    fun toggleFavorite(contact: AddressContact) {
        viewModelScope.launch {
            try {
                val updatedContact = contact.toggleFavorite()
                val result = updateAddressContactUseCase(updatedContact)
                when (result) {
                    is Result.Success -> {
                        // 更新本地列表
                        val updatedContacts = _uiState.value.contacts.map { 
                            if (it.id == contact.id) updatedContact else it
                        }
                        val updatedFiltered = _uiState.value.filteredContacts.map { 
                            if (it.id == contact.id) updatedContact else it
                        }
                        _uiState.update { 
                            it.copy(
                                contacts = updatedContacts,
                                filteredContacts = updatedFiltered
                            )
                        }
                        Timber.d("切換收藏狀態成功: ${contact.name}")
                    }
                    is Result.Failure -> {
                        Timber.e(result.exception, "切換收藏狀態失敗")
                    }
                    is Result.Loading -> {
                        // 保持狀態
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "切換收藏狀態異常")
            }
        }
    }
    
    /**
     * 刪除聯絡人
     */
    /**
     * 刪除聯絡人
     */
    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            try {
                when (val result = deleteAddressContactUseCase(contactId)) {
                    is Result.Success -> {
                        val updatedContacts = _uiState.value.contacts.filter { it.id != contactId }
                        val updatedFiltered = _uiState.value.filteredContacts.filter { it.id != contactId }
                        _uiState.update { 
                            it.copy(
                                contacts = updatedContacts,
                                filteredContacts = updatedFiltered
                            )
                        }
                        Timber.d("刪除聯絡人成功")
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "刪除聯絡人失敗")
                        }
                        Timber.e(result.exception, "刪除聯絡人失敗")
                    }
                    is Result.Loading -> {
                        // Ignore
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "刪除聯絡人異常")
            }
        }
    }
    
    /**
     * 新增聯絡人
     */
    fun addContact(name: String, address: String, notes: String = "") {
        viewModelScope.launch {
            try {
                val result = addAddressContactUseCase.invoke(
                    name = name,
                    address = address,
                    chainType = _uiState.value.currentChain,
                    tags = if (notes.isNotEmpty()) listOf(notes) else emptyList()
                )
                when (result) {
                    is Result.Success -> {
                        // 重新載入列表
                        loadContacts()
                        Timber.d("新增聯絡人成功: $name")
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "新增聯絡人失敗")
                        }
                        Timber.e(result.exception, "新增聯絡人失敗")
                    }
                    is Result.Loading -> {
                        // 保持狀態
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "新增聯絡人時發生錯誤")
                }
                Timber.e(e, "新增聯絡人異常")
            }
        }
    }
    
    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}