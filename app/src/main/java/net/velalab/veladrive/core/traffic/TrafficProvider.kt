package net.velalab.veladrive.core.traffic

data class TrafficStatus(
    val available: Boolean,
    val provider: String,
    val note: String? = null
)

interface TrafficProvider {
    suspend fun status(): TrafficStatus
}

class NoTrafficProvider : TrafficProvider {
    override suspend fun status() = TrafficStatus(
        available = false,
        provider = "none",
        note = "Fallback mode: routing works without live traffic"
    )
}
