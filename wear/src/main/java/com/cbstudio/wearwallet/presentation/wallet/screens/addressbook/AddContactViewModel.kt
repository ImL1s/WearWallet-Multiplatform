package com.cbstudio.wearwallet.presentation.wallet.screens.addressbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.AddAddressContactUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 新增聯絡人 ViewModel - 完整實現
 * 連接到 coreKmp 的 AddAddressContactUseCase
 */
class AddContactViewModel : ViewModel(), KoinComponent {
    
    private val addAddressContactUseCase: AddAddressContactUseCase by inject()
    
    data class AddContactUiState(
        val name: String = "",
        val address: String = "",
        val chainType: ChainType = ChainType.ETHEREUM,
        val category: ContactCategory = ContactCategory.OTHER,
        val notes: String = "",
        val tags: List<String> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isAddressValid: Boolean = false,
        val contactSaved: Boolean = false
    )
    
    private val _uiState = MutableStateFlow(AddContactUiState())
    val uiState: StateFlow<AddContactUiState> = _uiState.asStateFlow()
    
    fun updateName(newName: String) {
        _uiState.update { it.copy(name = newName) }
    }
    
    fun updateAddress(newAddress: String) {
        _uiState.update { 
            it.copy(
                address = newAddress,
                isAddressValid = validateAddress(newAddress, it.chainType)
            )
        }
    }
    
    fun updateChainType(chainType: ChainType) {
        _uiState.update { 
            it.copy(
                chainType = chainType,
                isAddressValid = validateAddress(it.address, chainType)
            )
        }
    }
    
    fun updateCategory(category: ContactCategory) {
        _uiState.update { it.copy(category = category) }
    }
    
    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }
    
    fun addTag(tag: String) {
        if (tag.isNotBlank()) {
            _uiState.update { 
                it.copy(tags = it.tags + tag.trim())
            }
        }
    }
    
    fun removeTag(tag: String) {
        _uiState.update { 
            it.copy(tags = it.tags.filter { t -> t != tag })
        }
    }
    
    fun saveContact() {
        viewModelScope.launch {
            val state = _uiState.value
            
            // 驗證必填欄位
            if (state.name.isBlank()) {
                _uiState.update { it.copy(errorMessage = "請輸入聯絡人名稱") }
                return@launch
            }
            
            if (state.address.isBlank()) {
                _uiState.update { it.copy(errorMessage = "請輸入錢包地址") }
                return@launch
            }
            
            if (!state.isAddressValid) {
                _uiState.update { it.copy(errorMessage = "錢包地址格式不正確") }
                return@launch
            }
            
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                val result = addAddressContactUseCase(
                    name = state.name.trim(),
                    address = state.address.trim(),
                    chainType = state.chainType,
                    category = state.category,
                    tags = state.tags
                )
                
                when (result) {
                    is Result.Success -> {
                        Timber.d("聯絡人新增成功：${result.data.name}")
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                contactSaved = true
                            )
                        }
                    }
                    is Result.Failure -> {
                        Timber.e(result.exception, "新增聯絡人失敗")
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = result.exception.message ?: "新增失敗"
                            )
                        }
                    }
                    is Result.Loading -> {
                        // 不應該發生
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "新增聯絡人時發生錯誤")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "未知錯誤"
                    )
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun reset() {
        _uiState.value = AddContactUiState()
    }
    
    private fun validateAddress(address: String, chainType: ChainType): Boolean {
        return when (chainType) {
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON, 
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.AVALANCHE,
            ChainType.FANTOM, ChainType.CRONOS, ChainType.CRONOSZVM, ChainType.BASE,
            ChainType.ZKSYNC, ChainType.MOONBEAM, ChainType.GNOSIS, ChainType.CELO,
            ChainType.LINEA, ChainType.SEPOLIA, ChainType.GOERLI, ChainType.MUMBAI -> {
                // ETH 地址格式：0x + 40 個十六進制字符
                address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
            }
            ChainType.BITCOIN -> {
                // BTC 地址格式簡化驗證
                address.length in 26..35 && address.matches(Regex("^[13][a-km-zA-HJ-NP-Z1-9]*$|^bc1[a-z0-9]{39,59}$"))
            }
            ChainType.LITECOIN -> {
                // LTC 地址格式：L/M 開頭的 Legacy 或 ltc1 開頭的 SegWit
                address.matches(Regex("^[LM][a-km-zA-HJ-NP-Z1-9]*$|^ltc1[a-z0-9]{39,59}$"))
            }
            ChainType.DOGECOIN -> {
                // DOGE 地址格式：D 開頭的 Base58 編碼
                address.matches(Regex("^D[5-9A-HJ-NP-U][a-km-zA-HJ-NP-Z1-9]*$"))
            }
            ChainType.BITCOIN_CASH -> {
                // BCH 地址格式：bitcoincash: 前綴的 CashAddr 格式或 1/3 開頭的 Legacy
                address.matches(Regex("^bitcoincash:[a-z0-9]{42,}$|^[13][a-km-zA-HJ-NP-Z1-9]*$"))
            }
            ChainType.SOLANA -> {
                // Solana 地址格式：Base58 編碼，32-44 字符
                address.length in 32..44 && address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]+$"))
            }
            ChainType.APTOS -> {
                // Aptos 地址格式：0x + 64 個十六進制字符
                address.startsWith("0x") && address.length == 66
            }
            ChainType.SUI -> {
                // Sui 地址格式：0x + 64 個十六進制字符
                address.startsWith("0x") && address.length == 66
            }
            ChainType.COSMOS -> {
                // Cosmos 地址格式：cosmos 開頭 + bech32 編碼
                address.matches(Regex("^cosmos[a-z0-9]{39}$"))
            }
            ChainType.POLKADOT -> {
                // Polkadot 地址格式：1 開頭 + base58 編碼
                address.matches(Regex("^1[1-9A-HJ-NP-Za-km-z]{47}$"))
            }
            ChainType.CARDANO -> {
                // Cardano 地址格式：addr1 開頭 + bech32 編碼
                address.matches(Regex("^addr1[a-z0-9]{98,}$"))
            }
            ChainType.NEAR -> {
                // NEAR 地址格式：account.near 或 64 個十六進制字符
                address.matches(Regex("^([a-z0-9_-]+\\.)+near$|^[a-f0-9]{64}$"))
            }
            ChainType.TRON -> {
                // TRON 地址格式：T 開頭 + Base58 編碼，34 字符
                address.length == 34 && address.startsWith("T") &&
                address.matches(Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$"))
            }
            ChainType.TEZOS -> {
                // Tezos 地址格式：tz1/tz2/tz3/KT1 開頭 + Base58 編碼
                address.matches(Regex("^(tz1|tz2|tz3|KT1)[1-9A-HJ-NP-Za-km-z]{33}$"))
            }
            ChainType.MONERO -> {
                // Monero 地址格式：4 或 8 開頭 + Base58 編碼，95 字符
                address.length == 95 && address.matches(Regex("^[48][1-9A-HJ-NP-Za-km-z]{94}$"))
            }
        }
    }
}