package com.cbstudio.wearwallet.core.security

interface SideEffectTracker {
    fun onSign()
    fun onBroadcast()
    fun onNetworkSend()
    fun onDbWrite()
}

object NoOpSideEffectTracker : SideEffectTracker {
    override fun onSign() {}
    override fun onBroadcast() {}
    override fun onNetworkSend() {}
    override fun onDbWrite() {}
}

object GlobalSideEffectTracker {
    var instance: SideEffectTracker = NoOpSideEffectTracker
}
