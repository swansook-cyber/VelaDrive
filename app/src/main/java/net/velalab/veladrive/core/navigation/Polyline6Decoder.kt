package net.velalab.veladrive.core.navigation

object Polyline6Decoder {
    fun decode(encoded: String): List<RoutePoint> {
        if (encoded.isBlank()) return emptyList()

        val points = mutableListOf<RoutePoint>()
        var index = 0
        var latitude = 0L
        var longitude = 0L

        while (index < encoded.length) {
            val latResult = decodeValue(encoded, index)
            latitude += latResult.value
            index = latResult.nextIndex

            if (index >= encoded.length) break

            val lonResult = decodeValue(encoded, index)
            longitude += lonResult.value
            index = lonResult.nextIndex

            points += RoutePoint(
                latitude = latitude / 1_000_000.0,
                longitude = longitude / 1_000_000.0
            )
        }

        return points
    }

    private fun decodeValue(encoded: String, startIndex: Int): DecodeResult {
        var index = startIndex
        var result = 0L
        var shift = 0

        while (index < encoded.length) {
            val chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f).toLong() shl shift)
            shift += 5
            if (chunk < 0x20) break
        }

        val value = if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
        return DecodeResult(value, index)
    }

    private data class DecodeResult(val value: Long, val nextIndex: Int)
}
