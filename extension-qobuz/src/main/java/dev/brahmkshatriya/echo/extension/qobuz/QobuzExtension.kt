package dev.brahmkshatriya.echo.extension.qobuz

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.qobuz.api.KennyyApiClient
import dev.brahmkshatriya.echo.extension.qobuz.api.SquidApiClient
import dev.brahmkshatriya.echo.extension.qobuz.models.QobuzTrack
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Suppress("unused")
class QobuzExtension : ExtensionClient, TrackClient, SearchFeedClient, LoginClient.CustomInput {

    companion object {
        private const val QUALITY_KEY = "quality"
        private const val SQUID_COOKIE_KEY = "squid_cookie"

        // Qobuz format IDs (passed to the proxy's download endpoint)
        private const val FORMAT_CD = 6      // FLAC 16-bit / 44.1 kHz
        private const val FORMAT_HIRES = 7   // FLAC 24-bit / up to 96 kHz
        private const val FORMAT_MAX = 27    // FLAC 24-bit / up to 192 kHz
    }

    // ---------- Lifecycle ----------

    private var settings: Settings? = null
    private var squidCookie: String? = null

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val kennyy = KennyyApiClient(http, json)
    private val squid = SquidApiClient(http, json)

    override suspend fun onExtensionSelected() {}
    override suspend fun onInitialize() {}

    // ---------- Settings ----------

    override suspend fun getSettingItems(): List<Setting> = listOf(
        SettingList(
            title = "Audio Quality",
            key = QUALITY_KEY,
            summary = "Higher quality uses more data. Falls back to a lower tier if unavailable.",
            entryTitles = listOf(
                "CD   — FLAC 16-bit / 44.1 kHz",
                "Hi-Res — FLAC 24-bit / up to 96 kHz",
                "Max  — FLAC 24-bit / up to 192 kHz"
            ),
            entryValues = listOf("cd", "hires", "max"),
            defaultEntryIndex = 0
        )
    )

    override fun setSettings(settings: Settings) {
        this.settings = settings
        squidCookie = settings.getString(SQUID_COOKIE_KEY)
    }

    // ---------- Login (squid.wtf optional fallback) ----------

    override val forms = listOf(
        LoginClient.Form(
            key = "squid_auth",
            label = "squid.wtf (optional fallback)",
            icon = LoginClient.InputField.Type.Misc,
            inputFields = listOf(
                LoginClient.InputField(
                    type = LoginClient.InputField.Type.Misc,
                    key = "cookie",
                    label = "captcha_verified_at cookie value",
                    isRequired = true
                )
            )
        )
    )

    override suspend fun onLogin(key: String, data: Map<String, String?>): List<User> {
        val cookie = data["cookie"]?.takeIf { it.isNotBlank() } ?: return emptyList()
        squidCookie = cookie
        settings?.putString(SQUID_COOKIE_KEY, cookie)
        return listOf(User("squid_user", "squid.wtf (verified)", cover = null))
    }

    override fun setLoginUser(user: User?) {
        if (user == null) {
            squidCookie = null
            settings?.putString(SQUID_COOKIE_KEY, null)
        }
    }

    override suspend fun getCurrentUser(): User? {
        return if (!squidCookie.isNullOrBlank())
            User("squid_user", "squid.wtf (verified)", cover = null)
        else null
    }

    // ---------- SearchFeedClient ----------

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        if (query.isBlank()) return emptyList<Shelf>().toFeed()
        val formatId = preferredFormatId()
        val tracks = kennyy.search(query)
        return tracks.map { it.toEchoTrack(formatId).toShelf() }.toFeed()
    }

    // ---------- TrackClient ----------

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track

    override suspend fun loadStreamableMedia(
        streamable: Streamable, isDownload: Boolean
    ): Streamable.Media {
        val trackId = streamable.extras["track_id"]
            ?: error("Streamable is missing track_id — was it created by this extension?")
        val formatId = streamable.extras["format_id"]?.toIntOrNull() ?: FORMAT_CD
        val source = streamable.extras["source"] ?: "kennyy"

        val url = tryGetStreamUrl(source, trackId, formatId)
            ?: tryGetStreamUrl(if (source == "kennyy") "squid" else "kennyy", trackId, formatId)
            ?: throw Exception("Both kennyy.com.br and squid.wtf failed to return a stream URL for track $trackId")

        return url.toServerMedia()
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    // ---------- Private helpers ----------

    private suspend fun tryGetStreamUrl(source: String, trackId: String, formatId: Int): String? =
        runCatching {
            when (source) {
                "kennyy" -> kennyy.getStreamUrl(trackId, formatId)
                "squid" -> squid.getStreamUrl(trackId, formatId, squidCookie)
                else -> null
            }
        }.getOrNull()

    private fun preferredFormatId(): Int = when (settings?.getString(QUALITY_KEY)) {
        "hires" -> FORMAT_HIRES
        "max" -> FORMAT_MAX
        else -> FORMAT_CD
    }

    private fun QobuzTrack.toEchoTrack(preferredFormat: Int): Track {
        // Downgrade format if the track doesn't support the requested tier
        val format = when {
            preferredFormat == FORMAT_MAX && maxBitDepth < 24 -> FORMAT_HIRES
            preferredFormat >= FORMAT_HIRES && !isHiResStreamable -> FORMAT_CD
            else -> preferredFormat
        }

        val qualityLabel = when (format) {
            FORMAT_MAX -> "FLAC ${maxBitDepth}bit / ${maxSamplingRate.toInt()} kHz"
            FORMAT_HIRES -> "FLAC Hi-Res ${maxBitDepth}bit"
            else -> "FLAC CD"
        }

        val extras = mapOf(
            "track_id" to id.toString(),
            "format_id" to format.toString()
        )

        val streamables = buildList {
            // Primary: kennyy (no auth required)
            add(
                Streamable.server(
                    id = "kennyy_${id}_$format",
                    quality = format,
                    title = "kennyy • $qualityLabel",
                    extras = extras + ("source" to "kennyy")
                )
            )
            // Fallback: squid.wtf (only if cookie is present)
            if (!squidCookie.isNullOrBlank()) {
                add(
                    Streamable.server(
                        id = "squid_${id}_$format",
                        quality = format - 1,   // slightly lower priority so kennyy is tried first
                        title = "squid.wtf • $qualityLabel",
                        extras = extras + ("source" to "squid")
                    )
                )
            }
        }

        return Track(
            id = id.toString(),
            title = title,
            duration = if (duration > 0) duration * 1000L else null,
            isrc = isrc,
            cover = (album?.image?.large ?: album?.image?.thumbnail ?: album?.image?.small)
                ?.toImageHolder(),
            artists = performer?.let { listOf(Artist(it.id.toString(), it.name)) } ?: emptyList(),
            streamables = streamables
        )
    }
}
