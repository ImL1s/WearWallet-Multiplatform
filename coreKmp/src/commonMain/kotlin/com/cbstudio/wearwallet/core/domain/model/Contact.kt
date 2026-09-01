package com.cbstudio.wearwallet.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val chainType: ChainType = ChainType.ETHEREUM,
    val note: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)