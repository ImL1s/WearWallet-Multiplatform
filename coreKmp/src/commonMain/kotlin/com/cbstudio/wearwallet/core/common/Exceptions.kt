package com.cbstudio.wearwallet.core.common

/**
 * 顯式類型未支援操作異常
 * 用於取代假成功 (No-Op Fake Success)，確保未實現或不支援的功能明確拋出或返回失敗
 */
class TypedUnsupportedOperationException(message: String) : UnsupportedOperationException(message)
