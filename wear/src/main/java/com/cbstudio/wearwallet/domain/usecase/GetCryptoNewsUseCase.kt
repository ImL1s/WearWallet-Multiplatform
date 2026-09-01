package com.cbstudio.wearwallet.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class GetCryptoNewsUseCase {
    fun getLatestCryptoNews(): Flow<CryptoNewsResponse> = 
        flowOf(CryptoNewsResponse(emptyList(), MarketSentiment("Neutral", "Stable", 0.5f, emptyList())))
    fun getTokenNewsSummary(token: String): Flow<TokenNewsSummary> = 
        flowOf(TokenNewsSummary(token, "Neutral", 0, 0f, 0f, emptyList()))
    fun getMarketSentimentAnalysis(): Flow<MarketSentiment> = 
        flowOf(MarketSentiment("Neutral", "Stable", 0.5f, emptyList()))
}

data class CryptoNewsResponse(val news: List<NewsItem>, val marketSentiment: MarketSentiment)
data class NewsItem(val title: String, val summary: String, val url: String)
data class MarketSentiment(
    val overall: String,
    val trendDirection: String,
    val confidence: Float,
    val keyFactors: List<String>
)
data class TokenNewsSummary(
    val tokenSymbol: String,
    val overallTrend: String,
    val totalNews: Int,
    val positivePct: Float,
    val negativePct: Float,
    val keyHeadlines: List<String>
)
data class SentimentScore(val positivePct: Float, val negativePct: Float)
