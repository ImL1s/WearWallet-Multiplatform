package com.cbstudio.wearwallet.domain.model

/**
 * Complication 識別符號
 * 
 * 定義所有支援的 Watch Face Complication 類型
 */
enum class ComplicationId(val value: String) {
    /**
     * 代幣價格 Complication
     */
    TOKEN_PRICE("token_price"),
    
    /**
     * 錢包餘額 Complication
     */
    WALLET_BALANCE("wallet_balance"),
    
    /**
     * Gas 費用 Complication
     */
    GAS_FEE("gas_fee"),
    
    /**
     * 投資組合總價值 Complication
     */
    PORTFOLIO("portfolio"),
    
    /**
     * QR 碼接收地址 Complication
     */
    QR_RECEIVE("qr_receive"),
    
    /**
     * NFT 收藏品顯示 Complication
     */
    NFT_DISPLAY("nft_display");
    
    companion object {
        /**
         * 根據字串值查找 ComplicationId
         * @param value 字串值
         * @return 對應的 ComplicationId，如果找不到則返回 null
         */
        fun fromString(value: String): ComplicationId? {
            return entries.find { it.value == value }
        }
        
        /**
         * 獲取所有可用的 Complication ID
         */
        fun getAllIds(): List<String> {
            return entries.map { it.value }
        }
        
        /**
         * 檢查是否為有效的 Complication ID
         */
        fun isValid(value: String): Boolean {
            return entries.any { it.value == value }
        }
    }
}