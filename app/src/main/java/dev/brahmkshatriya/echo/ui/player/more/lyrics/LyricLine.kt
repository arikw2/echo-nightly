package dev.brahmkshatriya.echo.ui.player.more.lyrics

import dev.brahmkshatriya.echo.common.models.Lyrics

sealed class LyricLine {
    abstract val startTime: Long
    abstract val endTime: Long

    data class Single(val item: Lyrics.Item) : LyricLine() {
        override val startTime get() = item.startTime
        override val endTime get() = item.endTime
    }

    data class WordGroup(val words: List<Lyrics.Item>) : LyricLine() {
        override val startTime get() = words.firstOrNull()?.startTime ?: 0L
        override val endTime get() = words.lastOrNull()?.endTime ?: 0L
    }
}
