package net.velalab.veladrive.core.navigation

data class RouteOptions(
    val avoidUnpaved: Boolean = true,
    val avoidFerry: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidTolls: Boolean = false
) {
    fun valhallaAutoOptions(): Map<String, Any> =
        mapOf(
            "exclude_unpaved" to avoidUnpaved,
            "use_ferry" to if (avoidFerry) 0.0 else 0.5,
            "use_highways" to if (avoidHighways) 0.0 else 0.5,
            "use_tolls" to if (avoidTolls) 0.0 else 0.5
        )

    fun summaryLabel(): String {
        val items = buildList {
            if (avoidUnpaved) add("เลี่ยงลูกรัง")
            if (avoidFerry) add("เลี่ยงเรือ")
            if (avoidHighways) add("เลี่ยงทางด่วน")
            if (avoidTolls) add("เลี่ยงทางเสียเงิน")
        }
        return if (items.isEmpty()) "มาตรฐาน" else items.joinToString(" • ")
    }
}
