package dev.brahmkshatriya.echo.extension.qobuz.api

import dev.brahmkshatriya.echo.extension.qobuz.models.DownloadResponse
import dev.brahmkshatriya.echo.extension.qobuz.models.QobuzTrack
import dev.brahmkshatriya.echo.extension.qobuz.models.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * HTTP client for squid.wtf — a Qobuz proxy that requires an ALTCHA captcha cookie.
 *
 * To obtain the cookie:
 *   1. Open squid.wtf in a browser and solve the ALTCHA captcha.
 *   2. Copy the value of the `captcha_verified_at` cookie (valid ~30 min).
 *   3. Paste it into the extension's login form.
 */
class SquidApiClient(
    private val http: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val BASE = "https://squid.wtf"
        private const val COOKIE_NAME = "captcha_verified_at"
    }

    suspend fun search(query: String, cookie: String?): List<QobuzTrack> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("$BASE/api/get-music?q=$encoded")
            .applyCookie(cookie)
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string() ?: return@withContext emptyList()
        }
        runCatching {
            json.decodeFromString<SearchResponse>(body).data?.tracks?.items ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun getStreamUrl(trackId: String, formatId: Int, cookie: String?): String? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE/api/download-music?track_id=$trackId&format_id=$formatId")
                .applyCookie(cookie)
                .build()
            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            runCatching {
                json.decodeFromString<DownloadResponse>(body).data?.url
            }.getOrNull()
        }

    private fun Request.Builder.applyCookie(cookie: String?): Request.Builder = apply {
        if (!cookie.isNullOrBlank()) addHeader("Cookie", "$COOKIE_NAME=$cookie")
    }
}
