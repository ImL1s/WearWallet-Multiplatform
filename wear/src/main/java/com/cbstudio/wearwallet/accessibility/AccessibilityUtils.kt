package com.cbstudio.wearwallet.accessibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.state.ToggleableState

/**
 * 無障礙功能工具類
 * 提供 TalkBack 和其他輔助功能支援
 */
object AccessibilityUtils {
    
    /**
     * 為按鈕添加無障礙支援
     */
    fun Modifier.accessibleButton(
        label: String,
        hint: String? = null,
        role: Role = Role.Button
    ): Modifier = this
        .semantics {
            contentDescription = label
            hint?.let { stateDescription = it }
            this.role = role
        }
    
    /**
     * 為圖標添加無障礙描述
     */
    fun Modifier.accessibleIcon(
        description: String,
        isDecorative: Boolean = false
    ): Modifier = if (isDecorative) {
        this.clearAndSetSemantics { 
            // 裝飾性圖標，TalkBack 會跳過
        }
    } else {
        this.semantics {
            contentDescription = description
            role = Role.Image
        }
    }
    
    /**
     * 為列表項添加無障礙支援
     */
    fun Modifier.accessibleListItem(
        label: String,
        value: String? = null,
        position: Int? = null,
        totalCount: Int? = null,
        onClick: (() -> Unit)? = null
    ): Modifier = this
        .semantics {
            contentDescription = buildString {
                append(label)
                value?.let { append(", $it") }
                if (position != null && totalCount != null) {
                    append(", 第 $position 項，共 $totalCount 項")
                }
            }
            onClick?.let {
                this.onClick(label = "點擊$label") { 
                    it()
                    true 
                }
            }
        }
    
    /**
     * 為輸入框添加無障礙支援
     */
    fun Modifier.accessibleTextField(
        label: String,
        value: String,
        hint: String? = null,
        error: String? = null,
        maxLength: Int? = null
    ): Modifier = this
        .semantics {
            contentDescription = label
            editableText = AnnotatedString(value)
            hint?.let { stateDescription = it }
            error?.let { 
                this.error(it)
            }
            maxLength?.let {
                stateDescription = "最多 $it 個字符，目前 ${value.length} 個"
            }
        }
    
    /**
     * 為進度條添加無障礙支援
     */
    fun Modifier.accessibleProgress(
        label: String,
        progress: Float,
        isIndeterminate: Boolean = false
    ): Modifier = this
        .semantics {
            contentDescription = if (isIndeterminate) {
                "$label，載入中"
            } else {
                "$label，進度 ${(progress * 100).toInt()}%"
            }
            if (!isIndeterminate) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress,
                    range = 0f..1f
                )
            }
        }
    
    /**
     * 為切換開關添加無障礙支援
     */
    fun Modifier.accessibleSwitch(
        label: String,
        isChecked: Boolean,
        onToggle: (Boolean) -> Unit
    ): Modifier = this
        .semantics {
            contentDescription = label
            stateDescription = if (isChecked) "已開啟" else "已關閉"
            role = Role.Switch
            toggleableState = ToggleableState(isChecked)
            onClick(label = "切換$label") {
                onToggle(!isChecked)
                true
            }
        }
    
    /**
     * 為標籤頁添加無障礙支援
     */
    fun Modifier.accessibleTab(
        label: String,
        isSelected: Boolean,
        position: Int,
        totalCount: Int
    ): Modifier = this
        .semantics {
            contentDescription = "$label，標籤 $position / $totalCount"
            selected = isSelected
            role = Role.Tab
        }
    
    /**
     * 為對話框添加無障礙支援
     */
    fun Modifier.accessibleDialog(
        title: String,
        dismissLabel: String = "關閉對話框"
    ): Modifier = this
        .semantics {
            contentDescription = title
            // 對話框關閉操作
            stateDescription = dismissLabel
            liveRegion = LiveRegionMode.Assertive
        }
    
    /**
     * 為狀態訊息添加無障礙支援
     */
    fun Modifier.accessibleStatusMessage(
        message: String,
        isError: Boolean = false,
        isSuccess: Boolean = false
    ): Modifier = this
        .semantics {
            contentDescription = when {
                isError -> "錯誤：$message"
                isSuccess -> "成功：$message"
                else -> message
            }
            liveRegion = if (isError) {
                LiveRegionMode.Assertive
            } else {
                LiveRegionMode.Polite
            }
        }
}

/**
 * 無障礙功能測試標籤
 */
object AccessibilityTags {
    const val WALLET_MAIN_SCREEN = "wallet_main_screen"
    const val TOKEN_LIST = "token_list"
    const val TOKEN_ITEM = "token_item"
    const val TRANSACTION_LIST = "transaction_list"
    const val TRANSACTION_ITEM = "transaction_item"
    const val SEND_BUTTON = "send_button"
    const val RECEIVE_BUTTON = "receive_button"
    const val SETTINGS_BUTTON = "settings_button"
    const val BACK_BUTTON = "back_button"
    const val REFRESH_BUTTON = "refresh_button"
    const val SEARCH_FIELD = "search_field"
    const val PASSWORD_FIELD = "password_field"
    const val MNEMONIC_DISPLAY = "mnemonic_display"
    const val CREATE_WALLET_BUTTON = "create_wallet_button"
    const val IMPORT_WALLET_BUTTON = "import_wallet_button"
    const val ERROR_MESSAGE = "error_message"
    const val SUCCESS_MESSAGE = "success_message"
    const val LOADING_INDICATOR = "loading_indicator"
    const val EMPTY_STATE = "empty_state"
}

/**
 * 無障礙功能內容描述
 */
object AccessibilityDescriptions {
    
    object Buttons {
        const val BACK = "返回上一頁"
        const val CLOSE = "關閉"
        const val REFRESH = "刷新內容"
        const val SETTINGS = "開啟設定"
        const val SEND = "發送代幣"
        const val RECEIVE = "接收代幣"
        const val CREATE_WALLET = "創建新錢包"
        const val IMPORT_WALLET = "匯入現有錢包"
        const val COPY = "複製到剪貼板"
        const val PASTE = "從剪貼板貼上"
        const val SCAN_QR = "掃描 QR Code"
        const val SHARE = "分享"
    }
    
    object Screens {
        const val WALLET_MAIN = "錢包主畫面"
        const val TRANSACTION_HISTORY = "交易歷史"
        const val TOKEN_SELECTOR = "代幣選擇器"
        const val SETTINGS = "設定畫面"
        const val CREATE_WALLET = "創建錢包畫面"
        const val IMPORT_WALLET = "匯入錢包畫面"
    }
    
    object States {
        const val LOADING = "載入中"
        const val ERROR = "發生錯誤"
        const val SUCCESS = "操作成功"
        const val EMPTY = "沒有內容"
        const val SELECTED = "已選擇"
        const val NOT_SELECTED = "未選擇"
        const val EXPANDED = "已展開"
        const val COLLAPSED = "已收起"
    }
    
    object Inputs {
        const val PASSWORD = "密碼輸入框"
        const val SEARCH = "搜尋框"
        const val AMOUNT = "金額輸入框"
        const val ADDRESS = "地址輸入框"
        const val MNEMONIC = "助記詞輸入框"
    }
}