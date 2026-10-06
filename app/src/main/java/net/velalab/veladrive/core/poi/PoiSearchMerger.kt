package net.velalab.veladrive.core.poi

import kotlin.math.abs

data class PoiSearchOutcome(
    val results: List<PoiSearchResult>,
    val velaFailure: Throwable?,
    val longdoFailure: Throwable?
)

object PoiSearchMerger {
    fun merge(
        savedResults: List<PoiSearchResult>,
        velaResults: Result<List<PoiSearchResult>>,
        longdoResults: Result<List<PoiSearchResult>>
    ): PoiSearchOutcome {
        val merged = mutableListOf<PoiSearchResult>()
        sequenceOf(
            // Personal saved places stay first because they are user-owned data.
            savedResults,
            // Provider priority is intentionally fixed:
            // Vela POI -> Longdo -> Google fallback outside this merger.
            velaResults.getOrDefault(emptyList()),
            longdoResults.getOrDefault(emptyList())
        ).flatten().forEach { candidate ->
            if (merged.none { accepted -> accepted.isSamePlace(candidate) }) {
                merged += candidate
            }
        }

        return PoiSearchOutcome(
            results = merged,
            velaFailure = velaResults.exceptionOrNull(),
            longdoFailure = longdoResults.exceptionOrNull()
        )
    }

    private fun PoiSearchResult.isSamePlace(other: PoiSearchResult): Boolean {
        val stableId = id?.trim()?.takeIf(String::isNotEmpty)
        val otherStableId = other.id?.trim()?.takeIf(String::isNotEmpty)
        if (
            stableId != null &&
            otherStableId != null &&
            source == other.source &&
            stableId == otherStableId
        ) {
            return true
        }

        return normalizePoiText(name) == normalizePoiText(other.name) &&
            abs(latitude - other.latitude) <= COORDINATE_TOLERANCE_DEGREES &&
            abs(longitude - other.longitude) <= COORDINATE_TOLERANCE_DEGREES
    }

    private const val COORDINATE_TOLERANCE_DEGREES = 0.0001
}
