package com.cbstudio.wearwallet.feature

import com.cbstudio.wearwallet.presentation.navigation.WalletRoute

/**
 * Wear-facing capability registry. Status values are the code source of
 * truth used by [ReleaseFeatureGate]; `docs/FEATURE_STATUS.md` is the
 * public matrix. Nothing here is PRODUCTION: send still has Task D gaps,
 * and no physical-device / mainnet / store evidence exists in this tree.
 */
enum class WearCapability(
    val id: String,
    val maturity: FeatureMaturity,
    val routes: Set<String> = emptySet(),
) {
    WEAR_SEND(
        id = "wear_send",
        maturity = FeatureMaturity.BETA,
        routes = setOf(WalletRoute.SEND, WalletRoute.UTXO_SEND),
    ),
    WEAR_RECEIVE(
        id = "wear_receive",
        maturity = FeatureMaturity.BETA,
        routes = setOf(WalletRoute.RECEIVE),
    ),
    WALLET_BACKUP_CREATE_IMPORT(
        id = "wallet_backup_create_import",
        maturity = FeatureMaturity.BETA,
        routes = setOf(
            WalletRoute.WALLET_MANAGEMENT,
            WalletRoute.MNEMONIC_DISPLAY,
            WalletRoute.IMPORT_WALLET,
            WalletRoute.IMPORT_MNEMONIC,
        ),
    ),
    KEYSTONE(
        id = "keystone",
        maturity = FeatureMaturity.EXPERIMENTAL,
        routes = setOf(WalletRoute.KEYSTONE_CONNECT, WalletRoute.KEYSTONE_SEND),
    ),
    SWAP(
        id = "swap",
        maturity = FeatureMaturity.EXPERIMENTAL,
        routes = setOf(WalletRoute.SWAP),
    ),
    WEAR_FI(
        id = "wear_fi",
        maturity = FeatureMaturity.MAINTENANCE,
        routes = setOf(WalletRoute.WEAR_FI),
    ),
    NFC(
        id = "nfc",
        maturity = FeatureMaturity.MAINTENANCE,
        routes = setOf(WalletRoute.NFC_PAYMENT, WalletRoute.WRIST_TRANSFER),
    ),
    DEBIT_CARD(
        id = "debit_card",
        maturity = FeatureMaturity.MAINTENANCE,
        routes = setOf(WalletRoute.DEBIT_CARD),
    ),
    AI_ASSISTANT(
        id = "ai_assistant",
        maturity = FeatureMaturity.MAINTENANCE,
        routes = setOf(WalletRoute.AI_ASSISTANT, WalletRoute.AI_INVESTMENT_ADVISOR),
    ),
    DEFI_ONE_CLICK(
        id = "defi_one_click",
        maturity = FeatureMaturity.MAINTENANCE,
        routes = setOf(WalletRoute.DEFI_ONE_CLICK),
    ),
    DIRECT_KMP(
        id = "direct_kmp",
        maturity = FeatureMaturity.MAINTENANCE,
    ),
    WATCHOS(
        id = "watchos",
        maturity = FeatureMaturity.EXPERIMENTAL,
    ),
    MOBILE_COMPANION(
        id = "mobile_companion",
        maturity = FeatureMaturity.EXPERIMENTAL,
    ),
    BROADCAST(
        id = "broadcast",
        maturity = FeatureMaturity.UNSUPPORTED,
    ),
    MAINNET_SOFTWARE_SIGN(
        id = "mainnet_software_sign",
        maturity = FeatureMaturity.UNSUPPORTED,
    ),
}
