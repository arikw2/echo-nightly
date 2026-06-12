package dev.brahmkshatriya.echo.playback.listener

import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class CrossfadeListener(
    private val player: Player,
    private val scope: CoroutineScope,
    private val settings: SharedPreferences,
) : Player.Listener {

    private var fadeOutJob: Job? = null
    private var fadeInJob: Job? = null
    private var monitorJob: Job? = null

    private val crossfadeDurationMs get() =
        (settings.getString(CROSSFADE_DURATION, "0")?.toIntOrNull() ?: 0) * 1000L

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startMonitoring() else monitorJob?.cancel()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        fadeOutJob?.cancel()
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            startFadeIn()
        } else {
            fadeInJob?.cancel()
            scope.launch(Dispatchers.Main) { player.volume = 1f }
        }
    }

    private fun startFadeIn() {
        val duration = crossfadeDurationMs
        if (duration <= 0) return
        fadeInJob?.cancel()
        fadeInJob = scope.launch(Dispatchers.Main) {
            player.volume = 0f
            val steps = FADE_STEPS
            val stepDelay = duration / steps
            repeat(steps) { i ->
                delay(stepDelay)
                if (isActive) player.volume = (i + 1f) / steps
            }
            player.volume = 1f
        }
    }

    private fun startFadeOut(remainingMs: Long) {
        if (player.volume < 0.99f) return
        fadeOutJob?.cancel()
        fadeOutJob = scope.launch(Dispatchers.Main) {
            val steps = FADE_STEPS
            val stepDelay = remainingMs / steps
            for (i in steps downTo 1) {
                if (isActive) player.volume = i.toFloat() / steps
                delay(stepDelay)
            }
        }
    }

    fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val duration = crossfadeDurationMs
                if (duration <= 0) continue
                val (pos, dur) = withContext(Dispatchers.Main) {
                    player.currentPosition to player.duration
                }
                if (dur > 0 && pos > 0) {
                    val remaining = dur - pos
                    if (remaining in 1 until duration) {
                        withContext(Dispatchers.Main) { startFadeOut(remaining) }
                        break
                    }
                }
            }
        }
    }

    companion object {
        const val CROSSFADE_DURATION = "crossfade_duration"
        private const val FADE_STEPS = 20
        private const val POLL_INTERVAL_MS = 250L
    }
}
