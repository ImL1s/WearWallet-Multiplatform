package com.cbstudio.wearwallet.core.data.mapper

import com.cbstudio.wearwallet.core.database.Address_book
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.domain.model.addressbook.ContactCategory

/**
 * SQLDelight 數據模型與領域模型的映射
 */
fun Address_book.toAddressContact(): AddressContact {
    return AddressContact(
        id = id.toString(),
        name = name,
        address = address,
        chainType = ChainType.valueOf(chain_type),
        chainId = chain_id.toInt(),
        category = ContactCategory.valueOf(category ?: "OTHER"),
        tags = if (tags.isNullOrEmpty()) emptyList() else tags.split(",").map { it.trim() },
        notes = notes ?: "",
        isFavorite = is_favorite != 0L,
        isVerified = is_verified != 0L,
        usageCount = usage_count.toInt(),
        createdAt = created_at,
        updatedAt = updated_at,
        lastUsedAt = last_used_at
    )
}

fun AddressContact.toAddress_book(): Address_book {
    return Address_book(
        id = id.toLongOrNull() ?: 0L,
        name = name,
        address = address,
        chain_type = chainType.name,
        chain_id = chainId.toLong(),
        category = category.name,
        tags = tags.joinToString(","),
        notes = notes,
        is_favorite = if (isFavorite) 1L else 0L,
        is_verified = if (isVerified) 1L else 0L,
        usage_count = usageCount.toLong(),
        created_at = createdAt,
        updated_at = updatedAt,
        last_used_at = lastUsedAt
    )
}