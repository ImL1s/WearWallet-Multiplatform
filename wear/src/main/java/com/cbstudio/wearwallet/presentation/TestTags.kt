package com.cbstudio.wearwallet.presentation

/**
 * 統一的 UI 測試標籤常量
 * 用於 E2E 測試中的元素定位
 */
object TestTags {
    // === 主畫面 ===
    const val MAIN_SCREEN = "main_screen"
    const val BALANCE_CARD = "balance_card"
    const val BALANCE_AMOUNT = "balance_amount"
    const val CHAIN_SYMBOL = "chain_symbol"
    const val SEND_BUTTON = "send_button"
    const val RECEIVE_BUTTON = "receive_button"
    const val SWAP_BUTTON = "swap_button"

    // === 發送畫面 ===
    const val SEND_SCREEN_TITLE = "send_screen_title"
    const val SEND_ADDRESS_INPUT = "send_address_input"
    const val SEND_ADDRESS_NEXT_BUTTON = "send_address_next_button"
    const val SEND_AMOUNT_INPUT = "send_amount_input"
    const val SEND_AMOUNT_NEXT_BUTTON = "send_amount_next_button"
    const val SEND_CONFIRM_BUTTON = "send_confirm_button"
    const val SEND_SUCCESS_SCREEN = "send_success_screen"
    const val SEND_SUCCESS_DONE_BUTTON = "send_success_done_button"

    
    // === 歡迎/引導畫面 ===
    const val ONBOARDING_SCREEN = "onboarding_screen"
    const val CREATE_WALLET_BUTTON = "create_wallet_button"
    const val IMPORT_WALLET_BUTTON = "import_wallet_button"
    
    // === 創建錢包畫面 ===
    const val CREATE_WALLET_SCREEN = "create_wallet_screen"
    const val START_CREATION_BUTTON = "start_creation_button"
    const val SAFETY_WARNING_SCREEN = "safety_warning_screen"
    const val ACKNOWLEDGE_WARNING_BUTTON = "acknowledge_warning_button"
    const val MNEMONIC_DISPLAY_SCREEN = "mnemonic_display_screen"
    const val MNEMONIC_WORD_TEXT = "mnemonic_word_text"
    const val CONFIRM_BACKUP_BUTTON = "confirm_backup_button"
    const val COMPLETED_SCREEN = "completed_screen"
    
    // === 助記詞導入畫面 ===
    const val IMPORT_TYPE_MNEMONIC = "import_type_mnemonic"
    const val MNEMONIC_INPUT_SCREEN = "mnemonic_input_screen"
    const val MNEMONIC_TEXT_FIELD = "mnemonic_text_field" // Single text field for all words
    const val MNEMONIC_WORD_INPUT_PREFIX = "mnemonic_word_input_" // + index (1-12)
    const val PASTE_MNEMONIC_BUTTON = "paste_mnemonic_button"
    const val NEXT_BUTTON = "next_button"
    const val BACK_BUTTON = "back_button"
    
    // === 密碼設置畫面 ===
    const val PASSWORD_SCREEN = "password_screen"
    const val WALLET_NAME_INPUT = "wallet_name_input"
    const val PASSWORD_INPUT = "password_input"
    const val CONFIRM_PASSWORD_INPUT = "confirm_password_input"
    const val CONFIRM_BUTTON = "confirm_button"
    
    // === 導入結果畫面 ===
    const val IMPORTING_SCREEN = "importing_screen"
    const val IMPORT_SUCCESS_SCREEN = "import_success_screen"
    const val SUCCESS_MESSAGE = "success_message"
    
    // === 通用 ===
    const val LOADING_INDICATOR = "loading_indicator"
    const val ERROR_MESSAGE = "error_message"
    
    // === Swap Screen ===
    const val SWAP_TOKEN_LIST = "swap_token_list"
    const val SWAP_TOKEN_ROW = "swap_token_row"
    const val SWAP_AMOUNT_DISPLAY = "swap_amount_display"
    const val SWAP_AMOUNT_KEYPAD = "swap_amount_keypad"
    const val SWAP_AMOUNT_CONFIRM_BUTTON = "swap_amount_confirm_button"
    const val SWAP_QUOTE_CONFIRM_BUTTON = "swap_quote_confirm_button"
    const val SWAP_SUCCESS_SCREEN = "swap_success_screen"
}
