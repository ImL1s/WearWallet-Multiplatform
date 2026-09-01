package com.cbstudio.wearwallet.core.security

/**
 * 標準規範認證附加數據 (Canonical Authenticated Additional Data - AAD)
 * 
 * 依據 P1-3 規範，將加密金鑰信封嚴格綁定至調用端預期的 Context (Schema, Purpose, KeyId, Chain, WalletId)。
 * 嚴禁信任信封自帶之 unauthenticated AAD。
 */
object CanonicalAad {
    const val SCHEMA_VERSION = "v1"
    const val PURPOSE_KEY_BACKUP = "key_backup"
    const val PURPOSE_WALLET_STORAGE = "wallet_storage"
    const val PURPOSE_TRANSACTION_SIGNING = "tx_signing"

    const val KEY_TYPE_PRIVATE_KEY = "private_key"
    const val KEY_TYPE_MNEMONIC = "mnemonic"

    /**
     * 為金鑰備份/匯出/匯入建立 Canonical AAD
     * 格式: schema=v1|purpose=key_backup|keyId=<keyId>
     */
    fun forKeyBackup(keyId: String): ByteArray {
        require(keyId.isNotBlank()) { "keyId cannot be blank" }
        return "schema=$SCHEMA_VERSION|purpose=$PURPOSE_KEY_BACKUP|keyId=$keyId".encodeToByteArray()
    }

    /**
     * 為錢包帳戶存儲建立 Canonical AAD
     * 格式: schema=v1|purpose=wallet_storage|walletId=<walletId>|keyType=<keyType>
     */
    fun forWalletStorage(walletId: String, keyType: String): ByteArray {
        require(walletId.isNotBlank()) { "walletId cannot be blank" }
        require(keyType.isNotBlank()) { "keyType cannot be blank" }
        return "schema=$SCHEMA_VERSION|purpose=$PURPOSE_WALLET_STORAGE|walletId=$walletId|keyType=$keyType".encodeToByteArray()
    }

    /**
     * 為特定交易簽章建立 Canonical AAD
     * 格式: schema=v1|purpose=tx_signing|keyId=<keyId>|chainId=<chainId>|intentHash=<intentHash>
     */
    fun forTransactionSigning(keyId: String, chainId: String, intentHash: String): ByteArray {
        require(keyId.isNotBlank()) { "keyId cannot be blank" }
        require(chainId.isNotBlank()) { "chainId cannot be blank" }
        require(intentHash.isNotBlank()) { "intentHash cannot be blank" }
        return "schema=$SCHEMA_VERSION|purpose=$PURPOSE_TRANSACTION_SIGNING|keyId=$keyId|chainId=$chainId|intentHash=$intentHash".encodeToByteArray()
    }
}
