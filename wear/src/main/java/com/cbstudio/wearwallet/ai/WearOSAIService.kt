/**
 * Wear OS 平台增強型 AI 服務實現
 * 使用 Firebase Vertex AI SDK + 本地規則引擎
 * 
 * 更新 (2025-07-28): 基於現有 Firebase Vertex AI 16.0.2
 * - 使用現有的 Firebase BOM 33.7.0 + firebase-vertexai
 * - 針對 Wear OS 優化的輕量級實現
 * - 支援離線本地規則引擎和在線 AI 服務
 * - 智能使用量管理和成本控制
 */

package com.cbstudio.wearwallet.ai

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Firebase Vertex AI Gemini 回應結構
 */
data class WearOSGeminiResponse(
    val action: String,
    val details: WearOSCommandDetails? = null,
    val response: String,
    val confidence: Double
)

data class WearOSCommandDetails(
    val toAddress: String? = null,
    val amount: String? = null,
    val token: String? = null
)

/**
 * AI 指令處理結果
 */
data class WearOSAIResult(
    val success: Boolean,
    val action: WearOSWalletAction?,
    val response: String,
    val confidence: Float,
    val source: String
)

/**
 * Wear OS 錢包操作
 * 
 * ULTRATHINK Phase 9.1 更新：
 * - 新增實時價格查詢操作
 * - 新增市場行情總覽操作
 */
sealed class WearOSWalletAction {
    object CheckBalance : WearOSWalletAction()
    data class SendTransaction(val address: String?, val amount: String?) : WearOSWalletAction()
    object ShowTransactionHistory : WearOSWalletAction()
    object ShowPortfolio : WearOSWalletAction()
    object GenerateQRCode : WearOSWalletAction()
    // ULTRATHINK Phase 9.1: 實時價格查詢
    data class CheckTokenPrice(val tokenSymbol: String) : WearOSWalletAction()
    object ShowMarketOverview : WearOSWalletAction()
    // ULTRATHINK Phase 9.2: Gas 費監控和網路狀態查詢
    object CheckGasFee : WearOSWalletAction()
    object CheckNetworkStatus : WearOSWalletAction()
    object CompareChainFees : WearOSWalletAction()
    // ULTRATHINK Phase 9.3: 智能加密新聞和市場情緒分析
    object CheckCryptoNews : WearOSWalletAction()
    data class CheckTokenNews(val tokenSymbol: String) : WearOSWalletAction()
    object CheckMarketSentiment : WearOSWalletAction()
    // ULTRATHINK Phase 9.4: 智能地址安全檢查和風險評估
    data class CheckAddressSecurity(val address: String) : WearOSWalletAction()
    object ShowSecurityTips : WearOSWalletAction()
    object CheckWalletSecurity : WearOSWalletAction()
    // ULTRATHINK Phase 9.5: 跨鏈橋查詢和手續費比較
    data class CheckCrossChainBridge(val fromChain: String?, val toChain: String) : WearOSWalletAction()
    object CompareBridgeFees : WearOSWalletAction()
    object ShowBridgeRecommendations : WearOSWalletAction()
    // ULTRATHINK Phase 10: 進階 AI 功能
    object CheckDeFiYield : WearOSWalletAction() // DeFi 收益推薦
    object CheckWhaleActivity : WearOSWalletAction() // 鯨魚動向追蹤
    object ExecuteBatchTransaction : WearOSWalletAction() // 批量交易
    object SuggestTradingStrategy : WearOSWalletAction() // 交易策略建議
    // ULTRATHINK Phase 11: 進階 Gemini AI 功能
    data class AnalyzeNFTImage(val imageBitmap: android.graphics.Bitmap?) : WearOSWalletAction() // NFT 視覺分析
    data class AuditSmartContract(val contractCode: String?, val address: String?) : WearOSWalletAction() // 智能合約審計
    object OptimizePortfolio : WearOSWalletAction() // AI 投資組合優化
    data class AuthenticateVoice(val audioData: ByteArray?) : WearOSWalletAction() // 語音生物識別
    data class VerifyTransactionVisually(val imageBitmap: android.graphics.Bitmap?) : WearOSWalletAction() // 視覺交易驗證
    
    // ULTRATHINK Phase 12: 自主 AI 代理功能
    data class StartTradingAgent(val config: String?) : WearOSWalletAction() // 啟動自主交易代理
    data class StopTradingAgent(val agentId: String?) : WearOSWalletAction() // 停止交易代理
    object CheckAgentStatus : WearOSWalletAction() // 檢查代理狀態
    object StartSecurityAgent : WearOSWalletAction() // 啟動行為生物識別安全代理
    object CheckSecurityStatus : WearOSWalletAction() // 檢查安全狀態
    data class ConfigureAgent(val agentType: String, val config: String) : WearOSWalletAction() // 配置代理
    object StartCrossChainOptimizer : WearOSWalletAction() // 啟動跨鏈優化代理
    object CheckMarketIntelligence : WearOSWalletAction() // 檢查市場情報
    object CheckRiskStatus : WearOSWalletAction() // 檢查風險狀態
    
    object Unknown : WearOSWalletAction()
}

/**
 * 使用統計
 */
data class WearOSUsageStats(
    val todayRequests: Int,
    val remainingRequests: Int,
    val monthlyUsage: Int,
    val estimatedCost: Float
)

/**
 * Wear OS 專用 AI 服務實現
 * 
 * 特色：
 * - 本地優先處理策略 (90% 使用本地規則)
 * - Firebase Vertex AI 備用 API (10% 複雜查詢)
 * - Wear OS 優化的輕量級實現
 * - 智能成本控制和使用量追蹤
 * 
 * 何時調用 Gemini API：
 * 1. 本地規則引擎信心度 < 0.7 時
 * 2. 複雜自然語言理解（如「幫我做一個完整的投資組合分析」）
 * 3. 多步驟批量操作（如「買 ETH，然後轉到 Polygon，再質押在 Aave」）
 * 4. 情緒分析和複雜推理（如「根據最近的市場表現，我應該怎麼做？」）
 * 5. 用戶明確要求使用 AI（如「問 AI」、「讓 Gemini 分析」）
 */
class WearOSAIService(private val context: Context) {
    
    private val preferences: SharedPreferences = 
        context.getSharedPreferences("wearos_ai_prefs", Context.MODE_PRIVATE)
    private val localRulesEngine = WearOSLocalRulesEngine()
    private val usageTracker = WearOSAIUsageTracker(preferences)
    
    // ULTRATHINK Phase 8.7: 語音回應系統 (TTS)
    private var textToSpeech: TextToSpeech? = null
    
    init {
        // 初始化 TTS 引擎
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.TRADITIONAL_CHINESE
                android.util.Log.d("WearOSAIService", "TTS 初始化成功，設置繁體中文語言")
            } else {
                android.util.Log.e("WearOSAIService", "TTS 初始化失敗: $status")
            }
        }
    }
    
    // Firebase Vertex AI - Gemini Pro 模型初始化
    private val vertexAI = Firebase.vertexAI
    private val model = vertexAI.generativeModel(
        modelName = "gemini-pro",
        generationConfig = generationConfig {
            temperature = 0.3f
            topK = 20
            topP = 0.8f
            maxOutputTokens = 100 // Wear OS 限制輸出長度
        }
    )
    
    // 使用 Android 內建的 JSONObject 進行解析
    
    /**
     * 處理自然語言指令 - Wear OS 優化版本
     */
    suspend fun processNaturalLanguageCommand(command: String): WearOSAIResult = withContext(Dispatchers.Default) {
        try {
            android.util.Log.d("WearOSAIService", "處理指令: '$command'")
            
            // 1. 本地規則引擎優先 (Wear OS 性能優化)
            val localResult = localRulesEngine.processCommand(command)
            android.util.Log.d("WearOSAIService", "本地結果: success=${localResult.success}, confidence=${localResult.confidence}, response=${localResult.response}")
            
            if (localResult.confidence > 0.7f) {
                // ULTRATHINK Phase 8.8: 智能語音播放 - 餘額查詢等待實際結果
                if (localResult.action != WearOSWalletAction.CheckBalance) {
                    speakResponse(localResult.response)
                }
                return@withContext localResult
            }
            
            // 2. 檢查 AI API 使用量 (Wear OS 限制較嚴格)
            if (!usageTracker.canMakeAPICall()) {
                android.util.Log.d("WearOSAIService", "API 使用量已達上限，使用降級回應")
                val fallbackResult = createWearOSFallbackResponse(command)
                speakResponse(fallbackResult.response)
                return@withContext fallbackResult
            }
            
            // 3. 使用 Firebase Vertex AI 進行深度分析
            android.util.Log.d("WearOSAIService", "使用 Firebase Vertex AI 處理")
            val apiResult = callWearOSVertexAI(command)
            usageTracker.recordAPICall()
            
            android.util.Log.d("WearOSAIService", "API 結果: success=${apiResult.success}, response=${apiResult.response}")
            // ULTRATHINK Phase 8.7: API 結果語音回應
            speakResponse(apiResult.response)
            return@withContext apiResult
            
        } catch (e: Exception) {
            android.util.Log.e("WearOSAIService", "處理指令時出現錯誤", e)
            val errorResult = WearOSAIResult(
                success = false,
                action = null,
                response = "處理指令時出現錯誤：${e.message}",
                confidence = 0.0f,
                source = "ERROR"
            )
            // ULTRATHINK Phase 8.7: 錯誤訊息語音回應
            speakResponse("處理指令時出現錯誤")
            return@withContext errorResult
        }
    }
    
    /**
     * 獲取使用統計
     */
    fun getUsageStats(): WearOSUsageStats {
        return usageTracker.getStats()
    }
    
    /**
     * ULTRATHINK Phase 8.7: 語音回應播放
     */
    fun speakResponse(text: String) {
        textToSpeech?.let { tts ->
            if (text.isNotBlank()) {
                android.util.Log.d("WearOSAIService", "🔊 播放語音回應: '$text'")
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wearos_ai_response")
            }
        } ?: run {
            android.util.Log.w("WearOSAIService", "TTS 未初始化，無法播放語音")
        }
    }
    
    /**
     * 釋放 TTS 資源
     */
    fun shutdown() {
        textToSpeech?.shutdown()
        textToSpeech = null
    }
    
    // 私有方法
    
    /**
     * 調用 Firebase Vertex AI - Wear OS 優化版本
     */
    private suspend fun callWearOSVertexAI(command: String): WearOSAIResult {
        return try {
            val prompt = buildWearOSWalletPrompt(command)
            val response = model.generateContent(prompt)
            
            val responseText = response.text ?: throw Exception("空的 AI 回應")
            return parseWearOSGeminiResponse(responseText, command)
            
        } catch (e: Exception) {
            // Firebase Vertex AI 調用失敗，降級到本地處理
            WearOSAIResult(
                success = false,
                action = localRulesEngine.parseAction(command),
                response = "AI 服務暫時不可用：${e.message}",
                confidence = 0.5f,
                source = "FALLBACK"
            )
        }
    }
    
    /**
     * 建立 Wear OS 優化的錢包 prompt
     */
    private fun buildWearOSWalletPrompt(command: String): String {
        return """
        你是 WearWallet AI 助手，具備加密貨幣專業知識。
        
        用戶指令："$command"
        
        功能範圍：
        - 基本操作：餘額查詢、交易歷史、發送交易、收款
        - 市場分析：價格查詢、新聞、情緒分析、鯨魚動向
        - DeFi 操作：收益查詢、質押建議、跨鏈橋
        - 安全檢查：地址風險評估、合約安全
        - 批量操作：多步驟交易、複合策略
        
        請分析指令並以 JSON 回應：
        {
          "action": "動作類型",
          "details": {相關參數},
          "response": "智能回應 (繁體中文，最多50字)",
          "confidence": 0.0-1.0,
          "complexity": "simple|medium|complex",
          "requiresMultiStep": true/false
        }
        
        若為複雜多步驟操作，請詳細列出步驟。
        """.trimIndent()
    }
    
    /**
     * 解析 Gemini 回應 - Wear OS 版本
     */
    private fun parseWearOSGeminiResponse(response: String, originalCommand: String): WearOSAIResult {
        return try {
            if (response.contains("{") && response.contains("}")) {
                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}") + 1
                val jsonString = response.substring(jsonStart, jsonEnd)
                
                val jsonObject = JSONObject(jsonString)
                val action = jsonObject.optString("action", "unknown")
                val responseText = jsonObject.optString("response", "無回應")
                val confidence = jsonObject.optDouble("confidence", 0.5)
                
                val walletAction = when (action.lowercase()) {
                    "check_balance" -> WearOSWalletAction.CheckBalance
                    "send_transaction" -> {
                        val details = jsonObject.optJSONObject("details")
                        val toAddress = details?.optString("toAddress")
                        val amount = details?.optString("amount")
                        if (!toAddress.isNullOrEmpty() && !amount.isNullOrEmpty()) {
                            WearOSWalletAction.SendTransaction(
                                address = toAddress,
                                amount = amount
                            )
                        } else {
                            null
                        }
                    }
                    "show_history" -> WearOSWalletAction.ShowTransactionHistory
                    "show_portfolio" -> WearOSWalletAction.ShowPortfolio
                    "generate_qr" -> WearOSWalletAction.GenerateQRCode
                    else -> WearOSWalletAction.Unknown
                }
                
                WearOSAIResult(
                    success = true,
                    action = walletAction,
                    response = responseText,
                    confidence = confidence.toFloat(),
                    source = "VERTEX_AI"
                )
            } else {
                val localAction = localRulesEngine.parseAction(originalCommand)
                WearOSAIResult(
                    success = true,
                    action = localAction,
                    response = response.take(50), // Wear OS 限制回應長度
                    confidence = 0.7f,
                    source = "VERTEX_AI"
                )
            }
        } catch (e: Exception) {
            WearOSAIResult(
                success = false,
                action = localRulesEngine.parseAction(originalCommand),
                response = "AI 回應解析失敗",
                confidence = 0.3f,
                source = "FALLBACK"
            )
        }
    }
    
    private fun createWearOSFallbackResponse(command: String): WearOSAIResult {
        return WearOSAIResult(
            success = true,
            action = localRulesEngine.parseAction(command),
            response = "今日 AI 額度已用完，使用本地處理",
            confidence = 0.6f,
            source = "FALLBACK"
        )
    }
}

/**
 * Wear OS 本地規則引擎 - 輕量級版本
 */
class WearOSLocalRulesEngine {
    
    fun processCommand(command: String): WearOSAIResult {
        val lowerCommand = command.lowercase()
        
        // 餘額查詢 - ULTRATHINK Phase 8.8: 智能回應
        if (lowerCommand.contains("餘額") || lowerCommand.contains("balance")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckBalance,
                response = "正在查詢您的錢包餘額",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // 交易歷史 - ULTRATHINK Phase 8.8: 智能回應
        if (lowerCommand.contains("歷史") || lowerCommand.contains("history")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.ShowTransactionHistory,
                response = "正在為您打開交易歷史",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // 投資組合 - ULTRATHINK Phase 8.8: 智能回應
        if (lowerCommand.contains("投資組合") || lowerCommand.contains("portfolio")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.ShowPortfolio,
                response = "正在為您打開投資組合",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // 收款碼 - ULTRATHINK Phase 8.8: 智能回應
        if (lowerCommand.contains("收款") || lowerCommand.contains("qr")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.GenerateQRCode,
                response = "正在為您生成收款二維碼",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.1: 實時價格查詢命令
        if (lowerCommand.contains("價格") || lowerCommand.contains("多少錢") || 
            lowerCommand.contains("price") || lowerCommand.contains("值多少")) {
            
            // 解析代幣名稱
            val tokenSymbol = when {
                lowerCommand.contains("以太") || lowerCommand.contains("eth") -> "ETH"
                lowerCommand.contains("比特幣") || lowerCommand.contains("bitcoin") || lowerCommand.contains("btc") -> "BTC"
                lowerCommand.contains("usdt") || lowerCommand.contains("泰達") -> "USDT"
                lowerCommand.contains("usdc") -> "USDC"
                lowerCommand.contains("bnb") || lowerCommand.contains("幣安") -> "BNB"
                lowerCommand.contains("matic") || lowerCommand.contains("polygon") -> "MATIC"
                else -> null
            }
            
            return if (tokenSymbol != null) {
                WearOSAIResult(
                    success = true,
                    action = WearOSWalletAction.CheckTokenPrice(tokenSymbol),
                    response = "正在查詢 $tokenSymbol 的實時價格",
                    confidence = 0.95f,
                    source = "LOCAL_RULES"
                )
            } else {
                WearOSAIResult(
                    success = true,
                    action = null,
                    response = "請告訴我您想查詢哪個代幣的價格，例如「以太坊價格」或「比特幣多少錢」",
                    confidence = 0.8f,
                    source = "LOCAL_RULES"
                )
            }
        }
        
        // ULTRATHINK Phase 9.1: 市場總覽查詢
        if (lowerCommand.contains("市場") || lowerCommand.contains("行情") || 
            lowerCommand.contains("market")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.ShowMarketOverview,
                response = "正在為您查詢市場行情",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.2: Gas 費查詢命令
        if (lowerCommand.contains("gas") || lowerCommand.contains("手續費") || 
            lowerCommand.contains("gas費") || lowerCommand.contains("燃料費") ||
            lowerCommand.contains("交易費") || lowerCommand.contains("fee")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckGasFee,
                response = "正在查詢當前網路的 Gas 費用",
                confidence = 0.95f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.2: 網路狀態查詢命令
        if (lowerCommand.contains("網路狀態") || lowerCommand.contains("網路擁塞") ||
            lowerCommand.contains("網路忙") || lowerCommand.contains("network") ||
            lowerCommand.contains("擁塞") || lowerCommand.contains("congestion")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckNetworkStatus,
                response = "正在檢查網路狀態",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.2: 多鏈費用比較
        if (lowerCommand.contains("比較") && (lowerCommand.contains("手續費") || 
            lowerCommand.contains("gas") || lowerCommand.contains("鏈"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CompareChainFees,
                response = "正在比較不同區塊鏈的手續費",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.3: 加密新聞查詢命令
        if (lowerCommand.contains("新聞") || lowerCommand.contains("消息") || 
            lowerCommand.contains("news") || lowerCommand.contains("最新")) {
            
            // 檢查是否指定特定代幣的新聞
            val tokenSymbol = when {
                lowerCommand.contains("以太") || lowerCommand.contains("eth") -> "ETH"
                lowerCommand.contains("比特幣") || lowerCommand.contains("bitcoin") || lowerCommand.contains("btc") -> "BTC"
                lowerCommand.contains("bnb") || lowerCommand.contains("幣安") -> "BNB"
                lowerCommand.contains("matic") || lowerCommand.contains("polygon") -> "MATIC"
                lowerCommand.contains("usdt") || lowerCommand.contains("泰達") -> "USDT"
                else -> null
            }
            
            return if (tokenSymbol != null) {
                WearOSAIResult(
                    success = true,
                    action = WearOSWalletAction.CheckTokenNews(tokenSymbol),
                    response = "正在為您查詢 $tokenSymbol 的最新新聞",
                    confidence = 0.95f,
                    source = "LOCAL_RULES"
                )
            } else {
                WearOSAIResult(
                    success = true,
                    action = WearOSWalletAction.CheckCryptoNews,
                    response = "正在為您查詢最新加密貨幣新聞",
                    confidence = 0.9f,
                    source = "LOCAL_RULES"
                )
            }
        }
        
        // ULTRATHINK Phase 9.3: 市場情緒查詢命令
        if (lowerCommand.contains("市場情緒") || lowerCommand.contains("情緒") || 
            lowerCommand.contains("sentiment") || lowerCommand.contains("看漲") ||
            lowerCommand.contains("看跌") || lowerCommand.contains("氣氛")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckMarketSentiment,
                response = "正在分析當前市場情緒",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.4: 地址安全檢查命令
        if (lowerCommand.contains("地址") && (lowerCommand.contains("安全") || 
            lowerCommand.contains("檢查") || lowerCommand.contains("風險") ||
            lowerCommand.contains("security") || lowerCommand.contains("safe"))) {
            
            // 嘗試從指令中提取地址
            val addressPattern = "0x[a-fA-F0-9]{40}".toRegex()
            val foundAddress = addressPattern.find(command)?.value
            
            return if (foundAddress != null) {
                WearOSAIResult(
                    success = true,
                    action = WearOSWalletAction.CheckAddressSecurity(foundAddress),
                    response = "正在檢查地址 ${foundAddress.take(10)}...${foundAddress.takeLast(4)} 的安全性",
                    confidence = 0.95f,
                    source = "LOCAL_RULES"
                )
            } else {
                WearOSAIResult(
                    success = true,
                    action = null,
                    response = "請提供要檢查的地址，例如「檢查地址 0x123...abc 的安全性」",
                    confidence = 0.85f,
                    source = "LOCAL_RULES"
                )
            }
        }
        
        // ULTRATHINK Phase 9.4: 安全提示和最佳實踐
        if (lowerCommand.contains("安全提示") || lowerCommand.contains("安全建議") || 
            lowerCommand.contains("security tips") || lowerCommand.contains("如何安全")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.ShowSecurityTips,
                response = "為您提供加密錢包安全提示",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.4: 錢包安全檢查
        if (lowerCommand.contains("錢包") && (lowerCommand.contains("安全") || 
            lowerCommand.contains("檢查") || lowerCommand.contains("狀態"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckWalletSecurity,
                response = "正在檢查您的錢包安全狀態",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.5: 跨鏈橋查詢命令
        if (lowerCommand.contains("跨鏈") || lowerCommand.contains("轉移到") || 
            lowerCommand.contains("橋") || lowerCommand.contains("bridge") ||
            lowerCommand.contains("如何轉到") || lowerCommand.contains("怎麼轉到")) {
            
            // 解析目標鏈
            val toChain = when {
                lowerCommand.contains("polygon") || lowerCommand.contains("matic") -> "Polygon"
                lowerCommand.contains("bsc") || lowerCommand.contains("幣安") || lowerCommand.contains("binance") -> "BSC"
                lowerCommand.contains("ethereum") || lowerCommand.contains("以太坊") || lowerCommand.contains("eth") -> "Ethereum"
                lowerCommand.contains("cronos") -> "Cronos"
                else -> null
            }
            
            // 解析來源鏈（可選）
            val fromChain = when {
                lowerCommand.contains("從 polygon") || lowerCommand.contains("from polygon") -> "Polygon"
                lowerCommand.contains("從 bsc") || lowerCommand.contains("from bsc") -> "BSC"
                lowerCommand.contains("從 ethereum") || lowerCommand.contains("from ethereum") -> "Ethereum"
                lowerCommand.contains("從 cronos") || lowerCommand.contains("from cronos") -> "Cronos"
                else -> null
            }
            
            return if (toChain != null) {
                WearOSAIResult(
                    success = true,
                    action = WearOSWalletAction.CheckCrossChainBridge(fromChain, toChain),
                    response = "正在查詢${fromChain?.let { "從 $it " } ?: ""}到 $toChain 的跨鏈橋",
                    confidence = 0.95f,
                    source = "LOCAL_RULES"
                )
            } else {
                WearOSAIResult(
                    success = true,
                    action = null,
                    response = "請告訴我您想轉移到哪個鏈，例如「如何轉移到 Polygon」或「怎麼跨鏈到 BSC」",
                    confidence = 0.85f,
                    source = "LOCAL_RULES"
                )
            }
        }
        
        // ULTRATHINK Phase 9.5: 跨鏈橋費用比較
        if ((lowerCommand.contains("橋") || lowerCommand.contains("跨鏈")) && 
            (lowerCommand.contains("費用") || lowerCommand.contains("手續費") || 
             lowerCommand.contains("比較") || lowerCommand.contains("最便宜"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CompareBridgeFees,
                response = "正在比較不同跨鏈橋的費用",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 9.5: 跨鏈橋推薦
        if ((lowerCommand.contains("推薦") || lowerCommand.contains("建議") || 
             lowerCommand.contains("最好") || lowerCommand.contains("最佳")) && 
            (lowerCommand.contains("橋") || lowerCommand.contains("跨鏈"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.ShowBridgeRecommendations,
                response = "正在為您推薦最佳跨鏈橋",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 10.1: DeFi 收益查詢命令
        if (lowerCommand.contains("收益") || lowerCommand.contains("質押") || 
            lowerCommand.contains("defi") || lowerCommand.contains("apy") ||
            lowerCommand.contains("哪裡質押") || lowerCommand.contains("最佳收益")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckDeFiYield,
                response = "正在查詢最佳 DeFi 收益機會",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 10.2: 鯨魚動向追蹤命令
        if (lowerCommand.contains("鯨魚") || lowerCommand.contains("大戶") || 
            lowerCommand.contains("whale") || lowerCommand.contains("巨鯨") ||
            lowerCommand.contains("跟單") || lowerCommand.contains("大額交易")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckWhaleActivity,
                response = "正在追蹤鯨魚動向",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 10.3: 批量交易識別（需要 Gemini API）
        if (lowerCommand.contains("然後") || lowerCommand.contains("接著") || 
            lowerCommand.contains("再") || lowerCommand.contains("最後") ||
            (lowerCommand.contains("買") && lowerCommand.contains("轉"))) {
            // 複雜批量操作，信心度降低以觸發 Gemini API
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.ExecuteBatchTransaction,
                response = "正在分析您的批量交易請求",
                confidence = 0.6f, // 故意降低信心度以觸發 Gemini
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 10.4: 交易策略建議（可能需要 Gemini）
        if (lowerCommand.contains("策略") || lowerCommand.contains("建議") || 
            lowerCommand.contains("怎麼做") || lowerCommand.contains("應該") ||
            lowerCommand.contains("分析")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.SuggestTradingStrategy,
                response = "正在為您分析最佳策略",
                confidence = 0.65f, // 中等信心度，可能觸發 Gemini
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 11.1: NFT 視覺分析命令（需要 Gemini Vision API）
        if ((lowerCommand.contains("nft") || lowerCommand.contains("藝術品") || 
            lowerCommand.contains("圖片")) && 
            (lowerCommand.contains("分析") || lowerCommand.contains("看看") || 
            lowerCommand.contains("值") || lowerCommand.contains("評估"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.AnalyzeNFTImage(null),
                response = "請提供 NFT 圖片，我會為您進行深度視覺分析",
                confidence = 0.5f, // 低信心度，需要 Gemini Vision
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 11.2: 智能合約審計命令（需要 Gemini API）
        if ((lowerCommand.contains("合約") || lowerCommand.contains("contract")) &&
            (lowerCommand.contains("審計") || lowerCommand.contains("檢查") || 
            lowerCommand.contains("安全") || lowerCommand.contains("audit"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.AuditSmartContract(null, null),
                response = "請提供智能合約地址或代碼，我會進行安全審計",
                confidence = 0.5f, // 低信心度，需要 Gemini
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 11.3: 投資組合優化命令（需要 Gemini API）
        if (lowerCommand.contains("優化") || lowerCommand.contains("調整") ||
            lowerCommand.contains("投資組合") || lowerCommand.contains("portfolio") ||
            lowerCommand.contains("配置") || lowerCommand.contains("建議投資")) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.OptimizePortfolio,
                response = "正在使用 AI 分析您的投資組合並提供優化建議",
                confidence = 0.55f, // 需要 Gemini 預測分析
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 11.4: 語音生物識別命令（需要 Gemini API）
        if ((lowerCommand.contains("語音") || lowerCommand.contains("聲音") || 
            lowerCommand.contains("voice")) &&
            (lowerCommand.contains("驗證") || lowerCommand.contains("認證") || 
            lowerCommand.contains("授權") || lowerCommand.contains("確認"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.AuthenticateVoice(null),
                response = "請說出授權短語，我會驗證您的聲紋",
                confidence = 0.5f, // 需要 Gemini 聲紋分析
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 11.5: 視覺交易驗證命令（需要 Gemini Vision API）
        if ((lowerCommand.contains("拍照") || lowerCommand.contains("掃描") || 
            lowerCommand.contains("檢查") || lowerCommand.contains("看")) &&
            (lowerCommand.contains("交易") || lowerCommand.contains("qr") || 
            lowerCommand.contains("二維碼") || lowerCommand.contains("螢幕"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.VerifyTransactionVisually(null),
                response = "請拍攝交易畫面或 QR Code，我會進行視覺驗證",
                confidence = 0.5f, // 需要 Gemini Vision
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 12.1: 自主交易代理命令
        if ((lowerCommand.contains("交易代理") || lowerCommand.contains("自動交易") || 
            lowerCommand.contains("機器人") || lowerCommand.contains("trading bot") ||
            lowerCommand.contains("自動買賣")) && 
            (lowerCommand.contains("啟動") || lowerCommand.contains("開始") || 
            lowerCommand.contains("start"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.StartTradingAgent(null),
                response = "正在啟動 AI 自主交易代理，需要配置交易參數",
                confidence = 0.5f, // 需要 Gemini 配置
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 12.1: 停止交易代理
        if ((lowerCommand.contains("停止") || lowerCommand.contains("關閉") || 
            lowerCommand.contains("stop")) &&
            (lowerCommand.contains("代理") || lowerCommand.contains("機器人") || 
            lowerCommand.contains("自動交易"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.StopTradingAgent(null),
                response = "正在停止交易代理",
                confidence = 0.9f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 12.2: 行為安全代理命令
        if ((lowerCommand.contains("安全代理") || lowerCommand.contains("行為監控") || 
            lowerCommand.contains("生物識別") || lowerCommand.contains("security agent")) &&
            (lowerCommand.contains("啟動") || lowerCommand.contains("開啟") || 
            lowerCommand.contains("enable"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.StartSecurityAgent,
                response = "正在啟動行為生物識別安全代理，將持續監控異常活動",
                confidence = 0.55f, // 需要 Gemini 分析
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 12: 檢查代理狀態
        if ((lowerCommand.contains("代理") || lowerCommand.contains("agent") || 
            lowerCommand.contains("機器人")) &&
            (lowerCommand.contains("狀態") || lowerCommand.contains("status") || 
            lowerCommand.contains("情況"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckAgentStatus,
                response = "正在檢查 AI 代理狀態",
                confidence = 0.85f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 12.5: 跨鏈優化代理
        if ((lowerCommand.contains("跨鏈") || lowerCommand.contains("cross chain") || 
            lowerCommand.contains("多鏈")) &&
            (lowerCommand.contains("優化") || lowerCommand.contains("套利") || 
            lowerCommand.contains("機會"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.StartCrossChainOptimizer,
                response = "正在啟動跨鏈優化代理，掃描套利機會和最佳路徑",
                confidence = 0.6f, // 需要 Gemini 配置
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 12.3: 市場情報
        if ((lowerCommand.contains("市場情報") || lowerCommand.contains("market intelligence") || 
            lowerCommand.contains("市場分析") || lowerCommand.contains("多代理"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckMarketIntelligence,
                response = "正在啟動多代理市場情報系統",
                confidence = 0.7f,
                source = "LOCAL_RULES"
            )
        }
        
        // ULTRATHINK Phase 12.4: 風險狀態
        if ((lowerCommand.contains("風險") || lowerCommand.contains("risk")) &&
            (lowerCommand.contains("狀態") || lowerCommand.contains("評估") || 
            lowerCommand.contains("管理") || lowerCommand.contains("分析"))) {
            return WearOSAIResult(
                success = true,
                action = WearOSWalletAction.CheckRiskStatus,
                response = "正在啟動主動風險管理代理",
                confidence = 0.75f,
                source = "LOCAL_RULES"
            )
        }
        
        // 未知命令 - 提供更友好的回應（更新支援的命令列表）
        return WearOSAIResult(
            success = true,
            action = null,
            response = "我不明白「$command」的意思。請說「查看餘額」、「交易歷史」、「代幣價格」、「Gas 費用」、「最新新聞」、「市場情緒」、「錢包安全」、「跨鏈轉移」、「DeFi 收益」、「鯨魚動向」、「NFT 分析」、「合約審計」、「投資組合優化」、「語音驗證」、「視覺驗證」、「啟動交易代理」或「啟動安全代理」等指令。",
            confidence = 0.5f, // 降低信心度，讓 Gemini 有機會處理未知指令
            source = "LOCAL_RULES"
        )
    }
    
    fun parseAction(command: String): WearOSWalletAction {
        return processCommand(command).action ?: WearOSWalletAction.Unknown
    }
}

/**
 * Wear OS AI 使用量追蹤器
 */
class WearOSAIUsageTracker(private val preferences: SharedPreferences) {
    
    // Wear OS 更嚴格的限制 (考慮電池和性能)
    private val maxDailyRequests = 10
    
    fun canMakeAPICall(): Boolean {
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val todayRequests = preferences.getInt("wearos_requests_$today", 0)
        return todayRequests < maxDailyRequests
    }
    
    fun recordAPICall() {
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val currentRequests = preferences.getInt("wearos_requests_$today", 0)
        preferences.edit().putInt("wearos_requests_$today", currentRequests + 1).apply()
        
        // 記錄月度使用量
        val month = "${System.currentTimeMillis() / (30L * 24 * 60 * 60 * 1000)}"
        val monthlyRequests = preferences.getInt("wearos_monthly_$month", 0)
        preferences.edit().putInt("wearos_monthly_$month", monthlyRequests + 1).apply()
    }
    
    fun getStats(): WearOSUsageStats {
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val todayRequests = preferences.getInt("wearos_requests_$today", 0)
        
        val month = "${System.currentTimeMillis() / (30L * 24 * 60 * 60 * 1000)}"
        val monthlyRequests = preferences.getInt("wearos_monthly_$month", 0)
        
        return WearOSUsageStats(
            todayRequests = todayRequests,
            remainingRequests = maxOf(0, maxDailyRequests - todayRequests),
            monthlyUsage = monthlyRequests,
            estimatedCost = calculateWearOSEstimatedCost(monthlyRequests)
        )
    }
    
    private fun calculateWearOSEstimatedCost(monthlyRequests: Int): Float {
        // Wear OS 優化的輕量級 prompt (更少 tokens)
        val avgInputTokens = monthlyRequests * 80  // 減少輸入 tokens
        val avgOutputTokens = monthlyRequests * 50 // 減少輸出 tokens
        
        val inputCostPer1M = 0.075f
        val outputCostPer1M = 0.30f
        
        val inputCost = avgInputTokens * inputCostPer1M / 1_000_000f
        val outputCost = avgOutputTokens * outputCostPer1M / 1_000_000f
        
        return inputCost + outputCost
    }
}
