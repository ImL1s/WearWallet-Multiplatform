package com.cbstudio.wearwallet.core.multichain.testnet

/**
 * Testnet 配置
 * 集中管理所有測試網絡的配置信息
 */
object TestnetConfig {

    // ========== 測試助記詞 ==========

    /**
     * 測試錢包 #1 (主錢包)
     * ⚠️ 僅用於 testnet，絕不在 mainnet 使用
     */
    const val MNEMONIC_1 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"

    /**
     * 測試錢包 #2 (接收錢包)
     * ⚠️ 僅用於 testnet，絕不在 mainnet 使用
     */
    const val MNEMONIC_2 = "iron mind drip glad load second merge rough music cloud fresh heavy"

    // ========== RPC 端點配置 ==========

    /**
     * Ethereum Sepolia Testnet
     * Faucet: https://sepoliafaucet.com/
     * Explorer: https://sepolia.etherscan.io/
     */
    object Ethereum {
        const val RPC_URL = "https://sepolia.infura.io/v3/YOUR_INFURA_KEY"
        const val RPC_URL_ALCHEMY = "https://eth-sepolia.g.alchemy.com/v2/YOUR_ALCHEMY_KEY"
        const val RPC_URL_PUBLIC = "https://rpc.sepolia.org"
        const val CHAIN_ID = 11155111L
        const val NETWORK = "sepolia"
        const val FAUCET_URL = "https://sepoliafaucet.com/"
        const val EXPLORER_URL = "https://sepolia.etherscan.io"

        // ERC20 測試代幣
        const val USDC_ADDRESS = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238" // Sepolia USDC
    }

    /**
     * Solana Devnet
     * Faucet: solana airdrop 1 <address> --url devnet
     * Explorer: https://explorer.solana.com/?cluster=devnet
     */
    object Solana {
        const val RPC_URL = "https://api.devnet.solana.com"
        const val NETWORK = "devnet"
        const val FAUCET_COMMAND = "solana airdrop 1 \$ADDRESS --url devnet"
        const val EXPLORER_URL = "https://explorer.solana.com/?cluster=devnet"

        // SPL 測試代幣
        const val USDC_MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU" // Devnet USDC
    }

    /**
     * TRON Shasta Testnet
     * Faucet: https://www.trongrid.io/shasta/#/
     * Explorer: https://shasta.tronscan.org/
     */
    object Tron {
        const val RPC_URL = "https://api.shasta.trongrid.io"
        const val NETWORK = "shasta"
        const val FAUCET_URL = "https://www.trongrid.io/shasta/#/"
        const val EXPLORER_URL = "https://shasta.tronscan.org"

        // TRC20 測試代幣
        const val USDT_ADDRESS = "TG3XXyExBkPp9nzdajDZsozEu4BkaSJozs" // Shasta USDT
    }

    /**
     * Cardano Preprod Testnet
     * Faucet: https://docs.cardano.org/cardano-testnet/tools/faucet/
     * Explorer: https://preprod.cardanoscan.io/
     */
    object Cardano {
        const val API_URL = "https://cardano-preprod.blockfrost.io/api/v0"
        const val API_KEY = "YOUR_BLOCKFROST_KEY"
        const val NETWORK = "preprod"
        const val FAUCET_URL = "https://docs.cardano.org/cardano-testnet/tools/faucet/"
        const val EXPLORER_URL = "https://preprod.cardanoscan.io"
    }

    /**
     * Polkadot Westend Testnet
     * Faucet: https://faucet.polkadot.io/
     * Explorer: https://westend.subscan.io/
     */
    object Polkadot {
        const val RPC_URL = "wss://westend-rpc.polkadot.io"
        const val HTTP_RPC_URL = "https://westend-rpc.polkadot.io"
        const val NETWORK = "westend"
        const val FAUCET_URL = "https://faucet.polkadot.io/"
        const val EXPLORER_URL = "https://westend.subscan.io"
    }

    // ========== 測試參數 ==========

    /**
     * 測試金額限制（防止意外損失）
     */
    object Limits {
        const val MAX_ETH_AMOUNT = "0.01"
        const val MAX_SOL_AMOUNT = "0.01"
        const val MAX_TRX_AMOUNT = "1.0"
        const val MAX_ADA_AMOUNT = "5.0"
        const val MAX_DOT_AMOUNT = "0.01"
    }

    /**
     * 超時設置
     */
    object Timeouts {
        const val RPC_TIMEOUT_MS = 30000L
        const val TX_CONFIRMATION_TIMEOUT_MS = 60000L
    }
}
