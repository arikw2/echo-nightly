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
 * HTTP client for kennyy.com.br — a public Qobuz proxy requiring no authentication.
 *
 * Endpoints (inferred from Stash project analysis):
 *   GET /api/get-music?q=<query>                          → SearchResponse
 *   GET /api/download-music?track_id=<id>&format_id=<id> → DownloadResponse
 */
class KennyyApiClient(
    private val http: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val BASE = "https://kennyy.com.br"
    }

    suspend fun search(query: String): List<QobuzTrack> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder().url("$BASE/api/get-music?q=$encoded").build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string() ?: return@withContext emptyList()
        }
        runCatching {
            json.decodeFromString<SearchResponse>(body).data?.tracks?.items ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun getStreamUrl(trackId: String, formatId: Int): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/api/download-music?track_id=$trackId&format_id=$formatId")
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string() ?: return@withContext null
        }
        runCatching {
            json.decodeFromString<DownloadResponse>(body).data?.url
        }.getOrNull()
    }
}
