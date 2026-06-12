package dev.brahmkshatriya.echo.extension.qobuz.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Search ----------

@Serializable
data class SearchResponse(
    val success: Boolean = false,
    val data: SearchData? = null,
    val error: String? = null
)

@Serializable
data class SearchData(
    val tracks: QobuzTracks? = null
)

@Serializable
data class QobuzTracks(
    val items: List<QobuzTrack> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0
)

@Serializable
data class QobuzTrack(
    val id: Long,
    val title: String,
    val duration: Int = 0,
    val isrc: String? = null,
    @SerialName("maximum_bit_depth") val maxBitDepth: Int = 16,
    @SerialName("maximum_sampling_rate") val maxSamplingRate: Double = 44.1,
    @SerialName("streamable") val isStreamable: Boolean = true,
    @SerialName("hires_streamable") val isHiResStreamable: Boolean = false,
    val performer: QobuzPerformer? = null,
    val album: QobuzAlbum? = null
)

@Serializable
data class QobuzPerformer(
    val id: Long = 0,
    val name: String = ""
)

@Serializable
data class QobuzAlbum(
    val id: String = "",
    val title: String = "",
    val image: QobuzImage? = null,
    @SerialName("released_at") val releasedAt: Long? = null
)

@Serializable
data class QobuzImage(
    val small: String? = null,
    val thumbnail: String? = null,
    val large: String? = null
)

// ---------- Download ----------

@Serializable
data class DownloadResponse(
    val success: Boolean = false,
    val data: DownloadData? = null,
    val error: String? = null
)

@Serializable
data class DownloadData(
    val url: String? = null
)
