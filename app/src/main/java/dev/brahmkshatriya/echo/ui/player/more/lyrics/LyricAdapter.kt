package dev.brahmkshatriya.echo.ui.player.more.lyrics

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import dev.brahmkshatriya.echo.databinding.ItemLoadingBinding
import dev.brahmkshatriya.echo.databinding.ItemLyricBinding
import dev.brahmkshatriya.echo.ui.common.UiViewModel
import dev.brahmkshatriya.echo.ui.feed.FeedLoadingAdapter
import dev.brahmkshatriya.echo.ui.player.PlayerColors.Companion.defaultPlayerColors
import dev.brahmkshatriya.echo.utils.ui.AnimationUtils.applyTranslationYAnimation
import dev.brahmkshatriya.echo.utils.ui.scrolling.ScrollAnimListAdapter
import dev.brahmkshatriya.echo.utils.ui.scrolling.ScrollAnimViewHolder

class LyricAdapter(
    val uiViewModel: UiViewModel, val listener: Listener,
) : ScrollAnimListAdapter<LyricLine, LyricAdapter.ViewHolder>(DiffCallback) {
    fun interface Listener {
        fun onLyricSelected(adapter: LyricAdapter, lyric: LyricLine)
    }

    object DiffCallback : DiffUtil.ItemCallback<LyricLine>() {
        override fun areItemsTheSame(oldItem: LyricLine, newItem: LyricLine): Boolean {
            if (oldItem::class != newItem::class) return false
            return when {
                oldItem is LyricLine.Single && newItem is LyricLine.Single ->
                    oldItem.item.text == newItem.item.text
                oldItem is LyricLine.WordGroup && newItem is LyricLine.WordGroup ->
                    oldItem.words.map { it.text } == newItem.words.map { it.text }
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: LyricLine, newItem: LyricLine) = oldItem == newItem
    }

    inner class ViewHolder(val binding: ItemLyricBinding) : ScrollAnimViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val lyric = getItem(bindingAdapterPosition) ?: return@setOnClickListener
                listener.onLyricSelected(this@LyricAdapter, lyric)
            }
        }
    }

    private fun getItemOrNull(position: Int) = runCatching { getItem(position) }.getOrNull()

    private var currentPos = -1
    private var currentTime = 0L

    private fun ViewHolder.render() {
        val pos = bindingAdapterPosition
        if (pos < 0) return
        val line = getItemOrNull(pos) ?: return
        val colors = uiViewModel.playerColors.value ?: binding.root.context.defaultPlayerColors()
        val fullColor = colors.onBackground or -0x1000000

        when (line) {
            is LyricLine.Single -> {
                binding.root.text = line.item.text.trim().trim('\n').ifEmpty { "♪" }
                binding.root.alpha = if (pos <= currentPos) 1f else 0.5f
                binding.root.setTextColor(fullColor)
            }
            is LyricLine.WordGroup -> {
                binding.root.alpha = 1f
                val dimColor = (fullColor and 0x00FFFFFF) or (0x80 shl 24)
                val effectiveTime = when {
                    pos < currentPos -> Long.MAX_VALUE
                    pos == currentPos -> currentTime
                    else -> Long.MIN_VALUE
                }
                binding.root.text = buildWordSpannable(line, effectiveTime, fullColor, dimColor)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemLyricBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.render()
        holder.itemView.applyTranslationYAnimation(scrollY)
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        holder.render()
    }

    fun updateColors() {
        onEachViewHolder { render() }
    }

    fun updateCurrent(pos: Int, time: Long) {
        currentPos = pos
        currentTime = time
        onEachViewHolder { render() }
    }

    private fun buildWordSpannable(
        line: LyricLine.WordGroup,
        effectiveTime: Long,
        highlightColor: Int,
        dimColor: Int,
    ): SpannableString {
        val sb = StringBuilder()
        data class WordSpan(val start: Int, val end: Int, val highlighted: Boolean)
        val spans = mutableListOf<WordSpan>()
        line.words.forEachIndexed { i, word ->
            val start = sb.length
            sb.append(word.text)
            spans.add(WordSpan(start, sb.length, word.startTime <= effectiveTime))
            if (i < line.words.size - 1) sb.append(" ")
        }
        return SpannableString(sb).apply {
            for ((start, end, highlighted) in spans) {
                setSpan(
                    ForegroundColorSpan(if (highlighted) highlightColor else dimColor),
                    start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    class Loading(
        parent: ViewGroup,
        val binding: ItemLoadingBinding = ItemLoadingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    ) : FeedLoadingAdapter.ViewHolder(binding.root)
}
