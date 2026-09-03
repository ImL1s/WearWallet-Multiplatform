package com.cbstudio.wearwallet.domain.model

/**
 * Short Text Complication 顯示類型
 * 
 * 定義短文本 Complication 的不同顯示模式
 */
enum class ShortTextDisplayType {
    /**
     * 顯示價格
     */
    PRICE,
    
    /**
     * 顯示變化率
     */
    CHANGE_PERCENTAGE,
    
    /**
     * 顯示餘額
     */
    BALANCE,
    
    /**
     * 顯示自動切換 (價格/變化率)
     */
    AUTO_SWITCH
}