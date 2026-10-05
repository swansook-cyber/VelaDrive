package net.velalab.veladrive.core.destination

private val coordinateRegex = Regex(
    pattern = """(?<![0-9.])(-?\d{1,2}(?:\.\d+)?)\s*[, ]\s*(-?\d{1,3}(?:\.\d+)?)(?![0-9.])"""
)
private val googleAtRegex = Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)""")
private val googleQueryRegex = Regex("""[?&](?:q|query|destination)=(-?\d{1,2}(?:\.\d+)?)[,%2C ]+(-?\d{1,3}(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

class DestinationResolver {
    fun resolveLocally(sharedText: String): Destination? {
        googleAtRegex.find(sharedText)?.let { match ->
            return destination(match.groupValues[1], match.groupValues[2], DestinationSource.GOOGLE_MAPS_LINK)
        }
        googleQueryRegex.find(sharedText)?.let { match ->
            return destination(match.groupValues[1], match.groupValues[2], DestinationSource.GOOGLE_MAPS_LINK)
        }
        coordinateRegex.find(sharedText)?.let { match ->
            return destination(match.groupValues[1], match.groupValues[2], DestinationSource.RAW_COORDINATES)
        }
        return null
    }

    private fun destination(lat: String, lon: String, source: DestinationSource): Destination? {
        val latitude = lat.toDoubleOrNull() ?: return null
        val longitude = lon.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Destination(latitude, longitude, source = source)
    }
}
