package net.velalab.veladrive.core.destination

import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ShareResolution {
    data class Resolved(val destination: Destination) : ShareResolution
    data class Unsupported(val detail: String) : ShareResolution
    data class CouldNotResolve(val detail: String) : ShareResolution
}

class GoogleMapsShareResolver(
    private val localResolver: DestinationResolver = DestinationResolver(),
    private val httpClient: OkHttpClient = defaultClient()
) {
    suspend fun resolve(sharedText: String): ShareResolution {
        localResolver.resolveLocally(sharedText)?.let {
            return ShareResolution.Resolved(it)
        }

        val shortUrl = extractSupportedGoogleShortUrl(sharedText)
            ?: return ShareResolution.Unsupported("payload=${sharedText.take(500)}")

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(shortUrl)
                    .get()
                    .header("User-Agent", "VelaDrive/0.1")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val redirectUrls = buildList {
                        add(response.request.url.toString())
                        var prior = response.priorResponse
                        while (prior != null) {
                            add(prior.request.url.toString())
                            prior = prior.priorResponse
                        }
                    }

                    resolveFromGoogleRedirectUrls(redirectUrls)
                        ?.let { ShareResolution.Resolved(it) }
                        ?: resolveFromGoogleMapsHtml(response.body.string())
                            ?.let { ShareResolution.Resolved(it) }
                        ?: ShareResolution.CouldNotResolve(
                            buildString {
                                append("payload=")
                                append(sharedText.take(500))
                                append("\nredirects=")
                                append(redirectUrls.joinToString(" -> ").take(1200))
                                append("\nhtml=loaded-but-no-supported-coordinate-pattern")
                            }
                        )
                }
            }.getOrElse { error ->
                ShareResolution.CouldNotResolve(
                    "payload=${sharedText.take(500)}\nerror=${error.javaClass.simpleName}: ${error.message.orEmpty().take(500)}"
                )
            }
        }
    }

    internal fun resolveFromGoogleRedirectUrls(urls: List<String>): Destination? {
        return urls.asSequence()
            .filter(::isAllowedGoogleMapsDestinationUrl)
            .mapNotNull(localResolver::resolveLocally)
            .firstOrNull()
    }

    internal fun resolveFromGoogleMapsHtml(html: String): Destination? {
        htmlCoordinatePatterns.forEach { pattern ->
            pattern.find(html)?.let { match ->
                val lat = match.groupValues[1].toDoubleOrNull()
                val lon = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    return Destination(
                        latitude = lat,
                        longitude = lon,
                        source = DestinationSource.GOOGLE_MAPS_LINK
                    )
                }
            }
        }
        return null
    }

    companion object {
        private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        private val supportedShortHosts = setOf("maps.app.goo.gl", "goo.gl")
        private val htmlCoordinatePatterns = listOf(
            Regex("""/@(-?\d{1,2}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)"""),
            Regex("""[?&](?:center|ll)=(-?\d{1,2}(?:\.\d+)?)(?:%2C|,)(-?\d{1,3}(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""["']latitude["']\s*:\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*["']longitude["']\s*:\s*(-?\d{1,3}(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""!3d(-?\d{1,2}(?:\.\d+)?)!4d(-?\d{1,3}(?:\.\d+)?)""")
        )

        fun extractSupportedGoogleShortUrl(text: String): String? {
            return urlRegex.findAll(text)
                .map { it.value.trimEnd('.', ',', ')', ']', '}', '>', '"', '\'') }
                .firstOrNull { raw ->
                    runCatching {
                        val url = raw.toHttpUrlOrNull() ?: return@firstOrNull false
                        val host = url.host.lowercase()
                        host in supportedShortHosts &&
                            (host != "goo.gl" || url.encodedPath.startsWith("/maps"))
                    }.getOrDefault(false)
                }
        }

        fun isAllowedGoogleMapsDestinationUrl(rawUrl: String): Boolean {
            return runCatching {
                val url = rawUrl.toHttpUrlOrNull() ?: return@runCatching false
                val host = url.host.lowercase()
                host == "google.com" ||
                    host.endsWith(".google.com") ||
                    host == "maps.app.goo.gl" ||
                    (host == "goo.gl" && url.encodedPath.startsWith("/maps"))
            }.getOrDefault(false)
        }

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .callTimeout(Duration.ofSeconds(10))
                .build()
    }
}
