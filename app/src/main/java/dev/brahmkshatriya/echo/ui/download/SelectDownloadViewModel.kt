package dev.brahmkshatriya.echo.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed.Companion.pagedDataOfFirst
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.download.Downloader
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getAs
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// Loads the tracks of a playlist/album and tracks which ones the user selects
// for the multi-select download sheet.
class SelectDownloadViewModel(
    app: App,
    private val extensionLoader: ExtensionLoader,
    private val downloader: Downloader,
) : ViewModel() {

    private val throwableFlow = app.throwFlow

    // null while loading.
    val tracksFlow = MutableStateFlow<List<Track>?>(null)
    val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    val listFlow = tracksFlow.combine(selectedIds) { tracks, selected ->
        tracks?.map { (it as EchoMediaItem) to (it.id in selected) }
    }

    private var started = false
    fun load(extensionId: String, item: EchoMediaItem) {
        if (started) return
        started = true
        viewModelScope.launch(Dispatchers.IO) {
            val ext = extensionLoader.music.getExtension(extensionId)
            val tracks = if (ext == null) emptyList()
            else loadTracks(ext, item).getOrElse { throwableFlow.emit(it); emptyList() }
            // Default-select only tracks that aren't already downloaded.
            val downloaded = downloader.flow.value.map { it.download.trackId }.toSet()
            selectedIds.value = tracks.map { it.id }.filterNot { it in downloaded }.toSet()
            tracksFlow.value = tracks
        }
    }

    private suspend fun loadTracks(ext: Extension<*>, item: EchoMediaItem): Result<List<Track>> =
        when (item) {
            is Album -> ext.getAs<AlbumClient, List<Track>> {
                val album = loadAlbum(item)
                loadTracks(album)?.pagedDataOfFirst()?.loadAll() ?: emptyList()
            }

            is Playlist -> ext.getAs<PlaylistClient, List<Track>> {
                val playlist = loadPlaylist(item)
                loadTracks(playlist).pagedDataOfFirst().loadAll()
            }

            is Track -> Result.success(listOf(item))
            else -> Result.success(emptyList())
        }

    fun toggle(item: EchoMediaItem) {
        selectedIds.value = selectedIds.value.toMutableSet().apply {
            if (!add(item.id)) remove(item.id)
        }
    }

    fun toggleAll(select: Boolean) {
        selectedIds.value =
            if (select) tracksFlow.value?.map { it.id }?.toSet() ?: emptySet() else emptySet()
    }

    fun selectedTracks(): List<Track> =
        tracksFlow.value?.filter { it.id in selectedIds.value } ?: emptyList()
}
