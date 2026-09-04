package com.cbstudio.wearwallet.core.domain.usecase.addressbook

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory
import com.cbstudio.wearwallet.core.domain.repository.AddressBookRepository

/**
 * 添加地址聯絡人業務邏輯
 */
class AddAddressContactUseCase(
    private val addressBookRepository: AddressBookRepository
) {
    /**
     * 添加新的地址聯絡人
     * @param name 聯絡人名稱
     * @param address 錢包地址
     * @param chainType 區塊鏈類型
     * @param category 聯絡人分類
     * @param tags 標籤列表
     * @return 創建的聯絡人
     */
    suspend operator fun invoke(
        name: String,
        address: String,
        chainType: ChainType,
        category: ContactCategory = ContactCategory.OTHER,
        tags: List<String> = emptyList()
    ): Result<AddressContact> {
        return try {
            // 驗證輸入
            if (name.isBlank()) {
                return Result.Failure(Exception("聯絡人名稱不能為空"))
            }
            
            if (address.isBlank()) {
                return Result.Failure(Exception("錢包地址不能為空"))
            }
            
            // 驗證地址格式
            if (!isValidAddress(address, chainType)) {
                return Result.Failure(Exception("無效的錢包地址格式"))
            }
            
            // 檢查地址是否已存在
            val existsResult = addressBookRepository.isAddressExists(address, chainType)
            when (existsResult) {
                is Result.Success -> {
                    if (existsResult.data) {
                        return Result.Failure(Exception("該地址已存在於地址簿中"))
                    }
                }
                is Result.Failure -> {
                    // 查詢失敗，繼續創建
                }
                is Result.Loading -> {
                    return Result.Failure(Exception("查詢狀態異常"))
                }
            }
            
            // 創建聯絡人
            val contact = AddressContact.create(
                name = name.trim(),
                address = address.trim(),
                chainType = chainType,
                category = category,
                tags = tags.filter { it.isNotBlank() }
            )
            
            addressBookRepository.createContact(contact)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    private fun isValidAddress(address: String, chainType: ChainType): Boolean {
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
                // LTC 地址格式：L/M 開頭或 ltc1 bech32
                address.matches(Regex("^[LM][a-km-zA-HJ-NP-Z1-9]{33}$|^ltc1[a-z0-9]{39,59}$"))
            }
            ChainType.DOGECOIN -> {
                // DOGE 地址格式：D 開頭
                address.matches(Regex("^D[a-km-zA-HJ-NP-Z1-9]{33}$"))
            }
            ChainType.BITCOIN_CASH -> {
                // BCH 地址格式：bitcoincash: 開頭或 legacy 格式
                address.matches(Regex("^(bitcoincash:)?[qp][a-z0-9]{41}$|^[13][a-km-zA-HJ-NP-Z1-9]{33}$"))
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
                address.matches(Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$"))
            }
            ChainType.TEZOS -> {
                // Tezos 地址格式：tz1/tz2/tz3 開頭 + Base58 編碼
                address.matches(Regex("^(tz1|tz2|tz3)[1-9A-HJ-NP-Za-km-z]{33}$"))
            }
            ChainType.MONERO -> {
                // Monero 地址格式：4 開頭（主網）或 5 開頭（測試網），95 字符
                address.matches(Regex("^[45][1-9A-HJ-NP-Za-km-z]{94}$"))
            }
        }
    }
    
    private fun generateContactId(): String {
        return "contact_${Clock.System.now().toEpochMilliseconds()}_${(1000..9999).random()}"
    }
}