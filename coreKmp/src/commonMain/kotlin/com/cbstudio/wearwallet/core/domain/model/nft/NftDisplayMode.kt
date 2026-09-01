package com.cbstudio.wearwallet.core.domain.model.nft

import kotlinx.serialization.Serializable

/**
 * NFT 顯示模式
 */
@Serializable
enum class NftDisplayMode {
    IMAGE_ONLY,
    IMAGE_WITH_NAME,
    IMAGE_WITH_TOKEN_ID,
    IMAGE_WITH_VALUE
}