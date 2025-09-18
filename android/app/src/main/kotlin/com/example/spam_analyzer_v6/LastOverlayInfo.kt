package com.example.spam_analyzer_v6

object LastOverlayInfo {
    @Volatile var incomingNumber: String? = null
    @Volatile var callId: String? = null
    @Volatile var timestamp: String? = null
    @Volatile var callTo: String? = null
    @Volatile var carrier: String? = null

    fun set(
        incomingNumber: String?,
        callId: String?,
        timestamp: String?,
        callTo: String?,
        carrier: String?
    ) {
        this.incomingNumber = incomingNumber
        this.callId = callId
        this.timestamp = timestamp
        this.callTo = callTo
        this.carrier = carrier
    }
}
