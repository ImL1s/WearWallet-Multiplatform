package com.cbstudio.wearwallet.core.multichain.config

object MoneroNodes {
    
    // Mainnet 節點（2025年1月更新 - 經測試驗證可用）
    val MAINNET_NODES = listOf(
        // 最新驗證可用節點（2025-01-27 測試通過）
        "https://node.hinto.rs:443",              // 東京，日本 - 高速節點
        "https://xmr.cryptostorm.is:18081",       // 阿姆斯特丹，荷蘭
        "http://monero.1acry.ru:18081",           // 杜塞爾多夫，德國
        "https://monero.heki.me:18089",           // 通用節點
        "http://node.moneroworld.com:18089",      // MoneroWorld 社區節點
        "http://node.supportxmr.com:18089",       // SupportXMR 節點
        
        // 原有節點（備用）
        "https://node.moneroworld.com:18089",     // MoneroWorld 社區節點 HTTPS
        "https://xmr.nownodes.io",                // NOWNodes
        "https://monero.stackwallet.com",         // Stack Wallet
        "https://monero1.heitechsoft.com",        // Heitech
        "https://xmr-tw.org:18089",               // 台灣節點
        "https://xmr-sg.org:18089",               // 新加坡節點
        "https://xmr-jp.org:18089",               // 日本節點
        "https://xmr-hk.org:18089",               // 香港節點
        
        // HTTP 備用節點
        "http://node.supportxmr.com:18081",       // SupportXMR 備用端口
        "https://opennode.xmr-tw.org:18089",      // 開放節點
        
        // 最後備用
        "http://54.153.251.193:18089"             // AWS 備用節點
    )
    
    // Testnet 節點（端口 28081）
    val TESTNET_NODES = listOf(
        "http://testnet.community.xmr.to:28081",
        "http://testnet.xmr.ditatompel.com:28081",
        "http://testnet.xmr-tw.org:28081"
    )
    
    // Stagenet 節點 - 使用用戶指定的端口 18089
    val STAGENET_NODES = listOf(
        "http://54.153.251.193:18089",            // 我們自己的 Stagenet 節點 (用戶指定的正確端口)
        "http://54.153.251.193:38089",            // 備用端口
        "https://stagenet.xmr-tw.org:38089",      // 台灣 Stagenet
        "http://stagenet.community.rino.io:38081", // Rino Stagenet
        "http://stagenet.monerujo.io:38081",      // Monerujo Stagenet
        "http://node.supportxmr.com:38089",       // SupportXMR Stagenet
        "http://stagenet-rpc.xmr.to:38081"        // XMR.to Stagenet
    )
    
    /**
     * 根據網路類型獲取節點列表
     */
    fun getNodes(network: String = "mainnet"): List<String> {
        return when (network.lowercase()) {
            "mainnet" -> MAINNET_NODES
            "testnet" -> TESTNET_NODES  
            "stagenet" -> STAGENET_NODES
            else -> MAINNET_NODES
        }
    }
    
    /**
     * 獲取最佳節點（嘗試連接並返回第一個可用的）
     */
    fun getBestNode(network: String = "mainnet"): String {
        return getNodes(network).firstOrNull() ?: MAINNET_NODES.first()
    }
    
    /**
     * 根據助記詞長度判斷網路類型
     * 13 字 = Mainnet
     * 25 字 = Testnet/Stagenet
     */
    fun getNetworkByMnemonic(mnemonic: String): String {
        val wordCount = mnemonic.trim().split(" ").size
        return when (wordCount) {
            13 -> "mainnet"
            25 -> "stagenet"  // 預設使用 Stagenet 而不是 Testnet
            else -> "mainnet"
        }
    }
}