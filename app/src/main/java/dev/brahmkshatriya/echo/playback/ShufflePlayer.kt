package dev.brahmkshatriya.echo.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import dev.brahmkshatriya.echo.playback.ResumptionUtils.recoverRecents

@Suppress("unused")
@OptIn(UnstableApi::class)
class ShufflePlayer(
    private val player: ExoPlayer,
    private val context: Context? = null,
) : ForwardingPlayer(player) {

    init {
        resetShuffleOrder()
    }

    private fun getQueue() = (0 until mediaItemCount).map { player.getMediaItemAt(it) }

    private var isShuffled = false
    private var original = getQueue()

    // Pin ExoPlayer's internal ShuffleOrder to identity so its "shuffled" order
    // equals the physical (already manually-shuffled) queue. Without this,
    // ExoPlayer rebuilds a random DefaultShuffleOrder on every addMediaItems
    // (e.g. AA background playlist expansion) → double-shuffle, wrong next/prev.
    private fun resetShuffleOrder() {
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(player.mediaItemCount))
    }

    // A random slot strictly after the current item, so appended tracks join
    // the shuffle instead of always landing at the very end.
    private fun shuffledInsertIndex(): Int {
        val lower = (currentMediaItemIndex + 1).coerceAtMost(mediaItemCount)
        if (lower >= mediaItemCount) return mediaItemCount
        return (lower..mediaItemCount).random()
    }

    override fun setShuffleModeEnabled(enabled: Boolean) {
        if (enabled) original = getQueue()
        isShuffled = enabled
        changeQueue(if (enabled) smartShuffle(original) else original)
        player.shuffleModeEnabled = enabled
        resetShuffleOrder()
    }

    private fun smartShuffle(tracks: List<MediaItem>): List<MediaItem> {
        val ctx = context ?: return tracks.shuffled()
        val recentIds = ctx.recoverRecents().map { it.item.id }.toSet()
        if (recentIds.isEmpty()) return tracks.shuffled()
        val currId = currentMediaItem?.mediaId
        val shuffled = tracks.shuffled()
        val (recent, other) = shuffled.partition { it.mediaId != currId && it.mediaId in recentIds }
        return other + recent
    }

    override fun hasNextMediaItem(): Boolean {
        return currentMediaItemIndex < mediaItemCount - 1
    }

    @Suppress("UNUSED_PARAMETER")
    private fun log(name: String) {
//        println(name)
//        println("$isShuffled list ${original.size}: ${original.map { it.mediaMetadata.title }}")
//        println("player ${mediaItemCount}: ${getQueue().map { it.mediaMetadata.title }}")
    }

    private fun changeQueue(list: List<MediaItem>) {
        log("Change queue")
        if (list.size <= 1) return
        val currentMediaItem = list.first { it.mediaId == currentMediaItem?.mediaId }
        val index = list.indexOf(currentMediaItem)
        val before = list.take(index) - currentMediaItem
        val after = list.takeLast(list.size - index) - currentMediaItem
        if (currentMediaItemIndex > 0) player.removeMediaItems(0, currentMediaItemIndex)
        player.addMediaItems(0, before)
        player.removeMediaItems(currentMediaItemIndex + 1, mediaItemCount)
        player.addMediaItems(currentMediaItemIndex + 1, after)
        resetShuffleOrder()
    }

    fun onMediaItemChanged(old: MediaItem, new: MediaItem) {
        original = original.toMutableList().apply {
            val index = indexOf(old).takeIf { it != -1 } ?: return
            set(index, new)
        }
        log("Change media item")
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        original = original + mediaItem
        if (isShuffled && mediaItemCount > 0) player.addMediaItem(shuffledInsertIndex(), mediaItem)
        else player.addMediaItem(mediaItem)
        resetShuffleOrder()
        log("Add media item")
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        original = original + mediaItems
        if (isShuffled && mediaItemCount > 0) mediaItems.forEach {
            player.addMediaItem(shuffledInsertIndex(), it)
        } else player.addMediaItems(mediaItems)
        resetShuffleOrder()
        log("Add media items")
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        original = original + mediaItem
        player.addMediaItem(index, mediaItem)
        resetShuffleOrder()
        log("Add media item at $index")
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        original = original + mediaItems
        player.addMediaItems(index, mediaItems)
        resetShuffleOrder()
        log("Add media items at $index")
    }

    private fun getItemAt(index: Int) = player.getMediaItemAt(index).let {
        original.first { item -> item.mediaId == it.mediaId }
    }

    override fun removeMediaItem(index: Int) {
        original = original - getItemAt(index)
        player.removeMediaItem(index)
        resetShuffleOrder()
        log("Remove media item at $index")
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        original =
            original - (fromIndex until toIndex).map { getItemAt(it) }.toSet()
        player.removeMediaItems(fromIndex, toIndex)
        resetShuffleOrder()
        log("Remove media items from $fromIndex to $toIndex")
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        original = original.toMutableList().apply {
            val originalIndex = indexOf(getItemAt(index)).takeIf { it != -1 }!!
            set(originalIndex, mediaItem)
        }
        player.replaceMediaItem(index, mediaItem)
        resetShuffleOrder()
        log("Replace media item at $index")
    }

    override fun replaceMediaItems(
        fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>
    ) {
        original = original.toMutableList().apply {
            val originalIndexes = (fromIndex until toIndex).map { i ->
                indexOf(getItemAt(i)).takeIf { it != -1 }!!
            }
            originalIndexes.forEachIndexed { i, originalIndex ->
                set(originalIndex, mediaItems[i])
            }
        }
        player.replaceMediaItems(fromIndex, toIndex, mediaItems)
        resetShuffleOrder()
        log("Replace media items from $fromIndex to $toIndex")
    }

    // After a fresh queue is set while shuffle is already on, actually shuffle it
    // (preserving the current track); otherwise just keep the identity order.
    private fun applyShuffleIfNeeded() {
        if (isShuffled) changeQueue(smartShuffle(getQueue())) else resetShuffleOrder()
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        original = listOf(mediaItem)
        player.setMediaItem(mediaItem)
        resetShuffleOrder()
        log("Set media item")
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        original = listOf(mediaItem)
        player.setMediaItem(mediaItem, resetPosition)
        resetShuffleOrder()
        log("Set media item")
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        original = listOf(mediaItem)
        player.setMediaItem(mediaItem, startPositionMs)
        resetShuffleOrder()
        log("Set media item")
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        original = mediaItems
        player.setMediaItems(mediaItems)
        applyShuffleIfNeeded()
        log("Set media items")
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        original = mediaItems
        player.setMediaItems(mediaItems, resetPosition)
        applyShuffleIfNeeded()
        log("Set media items")
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        original = mediaItems
        player.setMediaItems(
            mediaItems,
            startIndex.coerceAtMost(mediaItems.size - 1),
            startPositionMs
        )
        applyShuffleIfNeeded()
        log("Set media items")
    }

    override fun clearMediaItems() {
        original = emptyList()
        player.clearMediaItems()
        resetShuffleOrder()
        log("Clear media items")
    }

}
