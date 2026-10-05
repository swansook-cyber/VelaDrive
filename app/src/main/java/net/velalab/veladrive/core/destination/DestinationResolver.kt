package net.velalab.veladrive.core.destination

import java.net.URLDecoder

private val coordinateRegex = Regex(
    pattern = """(?<![0-9.])(-?\d{1,2}(?:\.\d+)?)\s*[, ]\s*(-?\d{1,3}(?:\.\d+)?)(?![0-9.])"""
)
private val googleAtRegex = Regex("""@(-?\d{1,2}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)""")
private val googleDataRegex = Regex("""!3d(-?\d{1,2}(?:\.\d+)?)!4d(-?\d{1,3}(?:\.\d+)?)""")
private val queryValueRegex = Regex("""[?&](?:q|query|destination)=([^&#]+)""", RegexOption.IGNORE_CASE)

class DestinationResolver {
    fun resolveLocally(sharedText: String): Destination? {
        val text = sharedText.trim()

        googleAtRegex.find(text)?.let { match ->
            return destination(match.groupValues[1], match.groupValues[2], DestinationSource.GOOGLE_MAPS_LINK)
        }

        googleDataRegex.find(text)?.let { match ->
            return destination(match.groupValues[1], match.groupValues[2], DestinationSource.GOOGLE_MAPS_LINK)
        }

        queryValueRegex.find(text)?.let { match ->
            val decoded = runCatching {
                URLDecoder.decode(match.groupValues[1], "UTF-8")
            }.getOrDefault(match.groupValues[1])

            coordinateRegex.find(decoded)?.let { coords ->
                return destination(
                    coords.groupValues[1],
                    coords.groupValues[2],
                    DestinationSource.GOOGLE_MAPS_LINK
                )
            }
        }

        if (text.startsWith("geo:", ignoreCase = true)) {
            coordinateRegex.find(text.removePrefix("geo:"))?.let { match ->
                return destination(match.groupValues[1], match.groupValues[2], DestinationSource.GEO_URI)
            }
        }

        coordinateRegex.find(text)?.let { match ->
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
