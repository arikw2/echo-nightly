package dev.brahmkshatriya.echo.playback

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.CallSuper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.download.Downloader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.isClient
import dev.brahmkshatriya.echo.extensions.MediaState
import dev.brahmkshatriya.echo.extensions.builtin.offline.OfflineExtension
import dev.brahmkshatriya.echo.playback.ResumptionUtils.recoverRecents
import dev.brahmkshatriya.echo.utils.CacheUtils.getFromCache
import dev.brahmkshatriya.echo.utils.CacheUtils.saveToCache
import dev.brahmkshatriya.echo.utils.CoroutineUtils.await
import dev.brahmkshatriya.echo.utils.CoroutineUtils.future
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

@UnstableApi
abstract class AndroidAutoCallback(
    open val app: App,
    open val scope: CoroutineScope,
    open val extensionList: StateFlow<List<MusicExtension>>,
    open val downloadFlow: StateFlow<List<Downloader.Info>>
) : MediaLibrarySession.Callback {

    val context get() = app.context

    @CallSuper
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        // Tell Android Auto to render browsable items (playlists/albums) as a
        // large-art grid like Spotify, and tracks as a list.
        val contentStyle = MediaLibraryService.LibraryParams.Builder()
            .setExtras(
                bundleOf(
                    CONTENT_STYLE_SUPPORTED to true,
                    CONTENT_STYLE_BROWSABLE_HINT to CONTENT_STYLE_GRID,
                    CONTENT_STYLE_PLAYABLE_HINT to CONTENT_STYLE_LIST,
                )
            ).build()
        return Futures.immediateFuture(
            LibraryResult.ofItem(browsableItem(ROOT, "", browsable = false), contentStyle)
        )
    }

    @OptIn(UnstableApi::class)
    @CallSuper
    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val extensions = extensionList.value
        if (parentId == ROOT) {
            // Single-extension mode: show the Combine extension's feeds directly
            // as top-level tabs (Home / Search / Library), like Spotify on Auto,
            // instead of an extension picker.
            val ext = extensions.firstOrNull { it.id == COMBINE_ID }
                ?: extensions.firstOrNull()
                ?: return@future LibraryResult.ofItemList(emptyList(), null)
            return@future LibraryResult.ofItemList(feedTabs(ext, context), null)
        }
        val extId = parentId.substringAfter("$ROOT/").substringBefore("/")
        val extension = extensions.first { it.id == extId }
        val searchQuery = params?.extras?.getString("search_query") ?: ""
        val type = parentId.substringAfter("$extId/").substringBefore("/")
        when (type) {
            ALBUM -> extension.getList<AlbumClient> {
                val id = parentId.substringAfter("$ALBUM/").substringBefore("/")
                val unloaded = itemMap[id] as Album
                getTracks(context, id, extId, page) {
                    val album = loadAlbum(unloaded)
                    album to loadTracks(album)
                }
            }

            PLAYLIST -> extension.getList<PlaylistClient> {
                val id = parentId.substringAfter("$PLAYLIST/").substringBefore("/")
                val unloaded = itemMap[id] as Playlist
                getTracks(context, id, extId, page) {
                    val playlist = loadPlaylist(unloaded)
                    playlist to loadTracks(playlist)
                }
            }

            RADIO -> extension.getList<RadioClient> {
                val id = parentId.substringAfter("$RADIO/").substringBefore("/")
                val radio = itemMap[id] as Radio
                getTracks(context, id, extId, page) {
                    radio to loadTracks(radio)
                }
            }

            ARTIST -> extension.getList<ArtistClient> {
                val id = parentId.substringAfter("$ARTIST/").substringBefore("/")
                val artist = loadArtist(Artist(id, ""))
                loadFeed(artist).toMediaItems(artist.id, context, extId, page)
            }

            LIST -> extension.getList<ExtensionClient> {
                val id = parentId.substringAfter("$LIST/").substringBefore("/")
                getListsItems(context, id, extId)
            }

            SHELF -> extension.getList<ExtensionClient> {
                val id = parentId.substringAfter("$SHELF/").substringBefore("/")
                getShelfItems(context, id, extId, page)
            }

            FEED -> extension.getList<ExtensionClient> {
                val id = parentId.substringAfter("$FEED/").substringBefore("/")
                getFeedItems(context, id, extId, page)
            }

            RECENT -> LibraryResult.ofItemList(
                context.recoverRecents().map { it.item.toItem(context, it.extensionId) },
                null
            )

            HOME -> extension.getFeed<HomeFeedClient>(
                context, parentId, HOME, page
            ) { loadHomeFeed() }

            LIBRARY -> extension.getFeed<LibraryFeedClient>(
                context, parentId, LIBRARY, page
            ) { loadLibraryFeed() }

            SEARCH -> extension.getFeed<SearchFeedClient>(
                context, parentId, SEARCH, page
            ) { loadSearchFeed(searchQuery) }

            else -> LibraryResult.ofItemList(feedTabs(extension, context), null)
        }
    }

    @OptIn(UnstableApi::class)
    @CallSuper
    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val items = searchCache[query] ?: loadSearch(query).also { searchCache[query] = it }
        LibraryResult.ofItemList(items, params)
    }

    @CallSuper
    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
        val items = loadSearch(query)
        searchCache[query] = items
        session.notifySearchResultChanged(browser, query, items.size, params)
        LibraryResult.ofVoid()
    }

    private val searchCache = HashMap<String, List<MediaItem>>()
    private suspend fun loadSearch(query: String): List<MediaItem> {
        val extensions = extensionList.value
        val ext = extensions.firstOrNull { it.id == COMBINE_ID }
            ?: extensions.firstOrNull() ?: return emptyList()
        return runCatching {
            val client = ext.instance.value().getOrThrow() as? SearchFeedClient
                ?: return emptyList()
            val data = client.loadSearchFeed(query).firstPagedData()
            data.loadPage(null).data.toMediaItems(context, ext.id)
        }.getOrElse {
            it.printStackTrace()
            emptyList()
        }
    }

    @CallSuper
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) = scope.future {
        val new = mediaItems.mapNotNull {
            if (it.mediaId.startsWith("auto/")) {
                val id = it.mediaId.substringAfter("auto/")
                val (track, extId, con) =
                    context.getFromCache<Triple<Track, String, EchoMediaItem?>>(id, "auto")
                        ?: return@mapNotNull null
                MediaItemUtils.build(
                    app,
                    downloadFlow.value,
                    MediaState.Unloaded(extId, track),
                    con
                )
            } else it
        }
        val future = super.onSetMediaItems(
            mediaSession, controller, new, startIndex, startPositionMs
        )
        future.await(context)
    }

    companion object {
        private const val ROOT = "root"
        private const val LIBRARY = "library"
        private const val HOME = "home"
        private const val SEARCH = "search"
        private const val FEED = "feed"
        private const val SHELF = "shelf"
        private const val LIST = "list"
        private const val RECENT = "recent"

        private const val ARTIST = "artist"
        private const val USER = "user"
        private const val ALBUM = "album"
        private const val PLAYLIST = "playlist"
        private const val RADIO = "radio"

        // The Combine extension is the only one surfaced on Android Auto.
        private const val COMBINE_ID = "echo_combine"

        // Android Auto content-style hints: render browsable items as a big-art
        // grid (like Spotify), tracks as a list.
        private const val CONTENT_STYLE_SUPPORTED =
            "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_BROWSABLE_HINT =
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT =
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_GRID = 2
        private const val CONTENT_STYLE_LIST = 1

        // Top-level tabs for an extension: Home / Search / Library. Android Auto
        // renders these root browsable children as the tab bar.
        private suspend fun feedTabs(extension: Extension<*>, context: Context) = listOfNotNull(
            if (extension.isClient<HomeFeedClient>())
                browsableItem("$ROOT/${extension.id}/$HOME", context.getString(R.string.home))
            else null,
            browsableItem("$ROOT/${extension.id}/$RECENT", "Recent"),
            if (extension.isClient<SearchFeedClient>())
                browsableItem("$ROOT/${extension.id}/$SEARCH", context.getString(R.string.search))
            else null,
            if (extension.isClient<LibraryFeedClient>())
                browsableItem("$ROOT/${extension.id}/$LIBRARY", context.getString(R.string.library))
            else null,
        )

        private fun Resources.getUri(int: Int): Uri {
            val scheme = ContentResolver.SCHEME_ANDROID_RESOURCE
            val pkg = getResourcePackageName(int)
            val type = getResourceTypeName(int)
            val name = getResourceEntryName(int)
            val uri = "$scheme://$pkg/$type/$name"
            return uri.toUri()
        }

        private fun ImageHolder.toUri(context: Context) = when (this) {
            is ImageHolder.ResourceUriImageHolder -> uri.toUri()
            is ImageHolder.NetworkRequestImageHolder -> request.url.toUri()
            is ImageHolder.ResourceIdImageHolder -> context.resources.getUri(resId)
            is ImageHolder.HexColorImageHolder -> "".toUri()
        }

        private fun browsableItem(
            id: String,
            title: String,
            subtitle: String? = null,
            browsable: Boolean = true,
            artWorkUri: Uri? = null,
            type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        ) = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsPlayable(false)
                    .setIsBrowsable(browsable)
                    .setMediaType(type)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artWorkUri)
                    .build()
            )
            .build()

        private fun Track.toItem(
            context: Context, extensionId: String, con: EchoMediaItem? = null
        ): MediaItem {
            context.saveToCache(id, Triple(this, extensionId, con), "auto")
            return MediaItem.Builder()
                .setMediaId("auto/$id")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setTitle(title)
                        .setArtist(subtitleWithE)
                        .setAlbumTitle(album?.title)
                        .setArtworkUri(cover?.toUri(context))
                        .build()
                ).build()
        }

        private suspend fun Extension<*>.toMediaItem(context: Context) = browsableItem(
            "$ROOT/$id", name, context.getString(R.string.extension),
            instance.value().isSuccess,
            metadata.icon?.toUri(context)
        )

        @OptIn(UnstableApi::class)
        val notSupported =
            LibraryResult.ofError<ImmutableList<MediaItem>>(SessionError.ERROR_NOT_SUPPORTED)

        @OptIn(UnstableApi::class)
        val errorIo = LibraryResult.ofError<ImmutableList<MediaItem>>(SessionError.ERROR_IO)

        suspend inline fun <reified C> Extension<*>.getList(
            block: C.() -> List<MediaItem>
        ): LibraryResult<ImmutableList<MediaItem>> = runCatching {
            val client = instance.value().getOrThrow() as? C ?: return@runCatching notSupported
            LibraryResult.ofItemList(
                client.block(),
                MediaLibraryService.LibraryParams.Builder()
                    .setOffline(client is OfflineExtension)
                    .build()
            )
        }.getOrElse {
            it.printStackTrace()
            errorIo
        }


        // Strong maps: Android Auto re-queries a node when navigating back to it,
        // so browsed items must survive GC or the node returns "no items".
        private val itemMap = HashMap<String, EchoMediaItem>()
        private fun EchoMediaItem.toMediaItem(
            context: Context, extId: String
        ): MediaItem = when (this) {
            is Track -> toItem(context, extId)
            else -> {
                val id = hashCode().toString()
                itemMap[id] = this
                val (page, type) = when (this) {
                    is Artist, is Radio -> USER to MediaMetadata.MEDIA_TYPE_MIXED
                    is Album -> ALBUM to MediaMetadata.MEDIA_TYPE_ALBUM
                    is Playlist -> PLAYLIST to MediaMetadata.MEDIA_TYPE_PLAYLIST
                    else -> throw IllegalStateException("Invalid type")
                }
                browsableItem(
                    "$ROOT/$extId/$page/$id",
                    title,
                    subtitleWithE,
                    true,
                    cover?.toUri(context),
                    type
                )
            }
        }

        private val listsMap = HashMap<String, Shelf.Lists<*>>()
        private fun getListsItems(
            context: Context, id: String, extId: String
        ) = run {
            val shelf = listsMap[id]!!
            when (shelf) {
                is Shelf.Lists.Categories -> shelf.list.map { it.toMediaItem(context, extId) }
                is Shelf.Lists.Items -> shelf.list.filterNot { it.isPodcast() }
                    .map { it.toMediaItem(context, extId) }
                is Shelf.Lists.Tracks -> shelf.list.filterNot { it.isPodcast() }
                    .map { it.toItem(context, extId) }
            } + listOfNotNull(
                shelf.more?.let { more ->
                    val moreId = shelf.id
                    feedMap[moreId] = more
                    browsableItem(
                        "$ROOT/$extId/$FEED/$moreId",
                        context.getString(R.string.more)
                    )
                }
            )
        }

        private fun Shelf.toMediaItem(
            context: Context, extId: String
        ): MediaItem = when (this) {
            is Shelf.Category -> {
                val items = feed
                if (items != null) feedMap[id] = items
                browsableItem("$ROOT/$extId/$FEED/$id", title, subtitle, items != null)
            }

            is Shelf.Item -> media.toMediaItem(context, extId)
            is Shelf.Lists<*> -> {
                val id = "${id.hashCode()}"
                listsMap[id] = this
                browsableItem("$ROOT/$extId/$LIST/$id", title, subtitle)
            }
        }

        // Podcasts (Spotify shows/episodes) aren't playable via the Deezer
        // stream source, so hide them. Their ids are spotify:show:.. / :episode:..
        private fun EchoMediaItem.isPodcast() =
            id.contains(":show:") || id.contains(":episode:")

        private fun Track.isPodcast() = id.contains(":episode:")

        // Drop podcast show/episode tiles from a shelf list (single items + the
        // entries inside Lists rows).
        private fun List<Shelf>.dropPodcasts() = mapNotNull { shelf ->
            when (shelf) {
                is Shelf.Item -> if (shelf.media.isPodcast()) null else shelf
                is Shelf.Lists.Items ->
                    shelf.copy(list = shelf.list.filterNot { it.isPodcast() })
                is Shelf.Lists.Tracks ->
                    shelf.copy(list = shelf.list.filterNot { it.isPodcast() })
                else -> shelf
            }
        }

        // Flatten search/feed shelves into individual media items so results show
        // directly (tracks playable, albums/playlists/artists browsable).
        private fun List<Shelf>.toMediaItems(context: Context, extId: String) = dropPodcasts().flatMap { shelf ->
            when (shelf) {
                is Shelf.Item -> listOf(shelf.media.toMediaItem(context, extId))
                is Shelf.Category -> listOf(shelf.toMediaItem(context, extId))
                is Shelf.Lists.Items -> shelf.list.map { it.toMediaItem(context, extId) }
                is Shelf.Lists.Tracks -> shelf.list.map { it.toItem(context, extId) }
                is Shelf.Lists.Categories -> shelf.list.map { it.toMediaItem(context, extId) }
            }
        }


        // THIS PROBABLY BREAKS GOING BACK TBH, NEED TO TEST
        private val shelvesMap = HashMap<String, PagedData<Shelf>>()
        private val continuations = HashMap<Pair<String, Int>, String?>()
        private suspend fun getShelfItems(
            context: Context, id: String, extId: String, page: Int
        ): List<MediaItem> {
            val shelf = shelvesMap[id]!!
            val (list, next) = shelf.loadPage(continuations[id to page])
            continuations[id to page + 1] = next
            return listOfNotNull(
                *list.dropPodcasts().map { it.toMediaItem(context, extId) }.toTypedArray()
            )
        }

        // Paged data for top-level feeds (home/library/search/artist) and category "more"
        // feeds, keyed by a stable browse id so paging survives across onGetChildren calls.
        private val feedPagedMap = HashMap<String, PagedData<Shelf>>()

        private suspend fun Feed<Shelf>.firstPagedData() =
            getPagedData(notSortTabs.firstOrNull()).pagedData

        private val feedMap = HashMap<String, Feed<Shelf>>()
        private suspend fun Feed<Shelf>.toMediaItems(
            id: String, context: Context, extId: String, page: Int
        ): List<MediaItem> {
            val key = "artist/${id.hashCode()}"
            val data = feedPagedMap.getOrPut(key) { firstPagedData() }
            val (list, next) = data.loadPage(continuations[key to page])
            continuations[key to page + 1] = next
            return list.dropPodcasts().map { it.toMediaItem(context, extId) }
        }

        private suspend fun getFeedItems(
            context: Context, id: String, extId: String, page: Int
        ): List<MediaItem> {
            val feed = feedMap[id] ?: return emptyList()
            val key = "$FEED/$id"
            val data = feedPagedMap.getOrPut(key) { feed.firstPagedData() }
            val (list, next) = data.loadPage(continuations[key to page])
            continuations[key to page + 1] = next
            return list.dropPodcasts().map { it.toMediaItem(context, extId) }
        }

        private suspend inline fun <reified T> Extension<*>.getFeed(
            context: Context,
            parentId: String,
            page: String,
            pageNumber: Int,
            getFeed: T.() -> Feed<Shelf>
        ) = getList<T> {
            val data = feedPagedMap.getOrPut(parentId) { getFeed().firstPagedData() }
            val (list, next) = data.loadPage(continuations[parentId to pageNumber])
            continuations[parentId to pageNumber + 1] = next
            list.dropPodcasts().map { it.toMediaItem(context, this@getFeed.id) }
        }

        // Incremental, re-query-stable paging. The extension's PagedData is not
        // safe to re-page (a second loadPage(null) can return empty -> "no
        // items" on back), so we advance the cursor forward ONCE, append into a
        // growing cached list, and always serve pages from that list. This keeps
        // the first page fast (no full loadAll) while staying stable on back.
        private const val PAGE_SIZE = 50
        private class TrackPaging(
            val item: EchoMediaItem,
            val data: PagedData<Track>,
            val loaded: MutableList<Track> = mutableListOf(),
            var continuation: String? = null,
            var started: Boolean = false,
            var done: Boolean = false,
        )

        private val tracksMap = HashMap<String, TrackPaging>()
        private suspend fun getTracks(
            context: Context,
            id: String,
            extId: String,
            page: Int,
            getTracks: suspend () -> Pair<EchoMediaItem, Feed<Track>?>
        ): List<MediaItem> {
            val paging = tracksMap.getOrPut(id) {
                val (mediaItem, feed) = getTracks()
                val data = feed?.run { getPagedData(tabs.firstOrNull()) }?.pagedData
                    ?: PagedData.empty()
                TrackPaging(mediaItem, data)
            }
            val need = (page + 1) * PAGE_SIZE
            while (!paging.done && paging.loaded.size < need) {
                val (chunk, next) = paging.data.loadPage(paging.continuation.takeIf { paging.started })
                paging.started = true
                paging.loaded.addAll(chunk.filterNot { it.isPodcast() })
                paging.continuation = next
                if (next == null) paging.done = true
            }
            val from = page * PAGE_SIZE
            if (from >= paging.loaded.size) return emptyList()
            val to = minOf(from + PAGE_SIZE, paging.loaded.size)
            return paging.loaded.subList(from, to).map { it.toItem(context, extId, paging.item) }
        }
    }

}