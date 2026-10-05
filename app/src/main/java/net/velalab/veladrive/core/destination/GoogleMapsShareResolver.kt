package net.velalab.veladrive.core.destination

import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ShareResolution {
    data class Resolved(val destination: Destination) : ShareResolution
    data object Unsupported : ShareResolution
    data object CouldNotResolve : ShareResolution
}

class GoogleMapsShareResolver(
    private val localResolver: DestinationResolver = DestinationResolver(),
    private val httpClient: OkHttpClient = defaultClient()
) {
    suspend fun resolve(sharedText: String): ShareResolution {
        localResolver.resolveLocally(sharedText)?.let {
            return ShareResolution.Resolved(it)
        }

        val shortUrl = extractSupportedGoogleShortUrl(sharedText) ?: return ShareResolution.Unsupported

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(shortUrl)
                    .get()
                    .header("User-Agent", "VelaDrive/0.1")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    if (!isAllowedGoogleMapsDestinationUrl(finalUrl)) {
                        return@use ShareResolution.CouldNotResolve
                    }

                    localResolver.resolveLocally(finalUrl)
                        ?.let { ShareResolution.Resolved(it) }
                        ?: ShareResolution.CouldNotResolve
                }
            }.getOrDefault(ShareResolution.CouldNotResolve)
        }
    }

    companion object {
        private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        private val supportedShortHosts = setOf("maps.app.goo.gl", "goo.gl")

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
