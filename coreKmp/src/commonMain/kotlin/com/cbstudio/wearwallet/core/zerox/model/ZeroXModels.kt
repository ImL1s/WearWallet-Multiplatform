package com.cbstudio.wearwallet.core.zerox.model

import kotlinx.serialization.Serializable

/**
 * 0x Swap API v2 Quote Response
 * 
 * All fields are nullable to handle different API versions and response formats.
 * 0x v2 uses different endpoints:
 * - /swap/permit2/quote (for Permit2 flow)
 * - /swap/allowance-holder/quote (for traditional approve flow)
 */
@Serializable
data class ZeroXQuoteResponse(
    val chainId: Int = 0,
    val price: String = "",
    val grossPrice: String? = null,
    val estimatedPriceImpact: String? = null,
    val value: String = "0",
    val gasPrice: String? = null,
    val gas: String? = null,
    val estimatedGas: String? = null,
    val protocolFee: String? = null,
    val minimumProtocolFee: String? = null,
    val buyTokenAddress: String = "",
    val buyAmount: String = "0",
    val grossBuyAmount: String? = null,
    val sellTokenAddress: String = "",
    val sellAmount: String = "0",
    val grossSellAmount: String? = null,
    val sources: List<ZeroXSource>? = null,
    val allowanceTarget: String? = null,
    val sellTokenToEthRate: String? = null,
    val buyTokenToEthRate: String? = null,
    val to: String = "",
    val data: String = "",
    val decodedUniqueId: String? = null,
    val guaranteedPrice: String? = null,
    val orders: List<ZeroXOrder>? = null,
    val route: ZeroXRoute? = null,
    val issues: ZeroXIssues? = null,
    val liquidityAvailable: Boolean? = null,
    val minBuyAmount: String? = null,
    val totalNetworkFee: String? = null,
    val zid: String? = null,
    val transaction: ZeroXTransaction? = null
) {
    // Helper properties to get transaction data from either top-level or nested object
    val effectiveTo: String get() = to.ifEmpty { transaction?.to ?: "" }
    val effectiveData: String get() = data.ifEmpty { transaction?.data ?: "" }
    val effectiveGas: String? get() = gas ?: transaction?.gas
    val effectiveGasPrice: String? get() = gasPrice ?: transaction?.gasPrice
    val effectiveValue: String get() = if (value != "0" && value.isNotEmpty()) value else (transaction?.value ?: "0")
}

@Serializable
data class ZeroXTransaction(
    val to: String? = null,
    val data: String? = null,
    val gas: String? = null,
    val gasPrice: String? = null,
    val value: String? = null
)

@Serializable
data class ZeroXSource(
    val name: String = "",
    val proportion: String = ""
)

@Serializable
data class ZeroXOrder(
    val type: String? = null,
    val source: String? = null
)

@Serializable
data class ZeroXRoute(
    val fills: List<ZeroXFill>? = null
)

@Serializable
data class ZeroXFill(
    val from: String? = null,
    val to: String? = null,
    val source: String? = null,
    val proportionBps: String? = null
)

@Serializable
data class ZeroXIssues(
    val allowance: ZeroXAllowanceIssue? = null,
    val balance: ZeroXBalanceIssue? = null,
    val simulationIncomplete: Boolean? = null,
    val invalidSourcesPassed: List<String>? = null
)

@Serializable
data class ZeroXAllowanceIssue(
    val actual: String? = null,
    val expected: String? = null,
    val spender: String? = null
)

@Serializable
data class ZeroXBalanceIssue(
    val token: String? = null,
    val actual: String? = null,
    val expected: String? = null
)

@Serializable
data class ZeroXErrorResponse(
    val code: Int? = null,
    val reason: String? = null,
    val message: String? = null,
    val validationErrors: List<ZeroXValidationError>? = null
)

@Serializable
data class ZeroXValidationError(
    val field: String? = null,
    val code: Int? = null,
    val reason: String? = null
)

@Serializable
data class ZeroXPriceResponse(
    val chainId: Int = 0,
    val price: String = "",
    val estimatedPriceImpact: String? = null,
    val value: String? = null,
    val gasPrice: String? = null,
    val gas: String? = null,
    val estimatedGas: String? = null,
    val protocolFee: String? = null,
    val minimumProtocolFee: String? = null,
    val buyTokenAddress: String = "",
    val buyAmount: String = "0",
    val sellTokenAddress: String = "",
    val sellAmount: String = "0",
    val sources: List<ZeroXSource>? = null,
    val allowanceTarget: String? = null,
    val sellTokenToEthRate: String? = null,
    val buyTokenToEthRate: String? = null,
    val liquidityAvailable: Boolean? = null,
    val minBuyAmount: String? = null,
    val totalNetworkFee: String? = null,
    val zid: String? = null
)
