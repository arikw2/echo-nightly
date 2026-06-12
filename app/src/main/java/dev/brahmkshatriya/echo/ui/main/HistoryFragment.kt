package dev.brahmkshatriya.echo.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.MaterialSharedAxis
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.databinding.FragmentHistoryBinding
import dev.brahmkshatriya.echo.databinding.ItemPlaylistTrackBinding
import dev.brahmkshatriya.echo.extensions.MediaState
import dev.brahmkshatriya.echo.ui.player.PlayerViewModel
import dev.brahmkshatriya.echo.utils.ContextUtils.observe
import dev.brahmkshatriya.echo.utils.image.ImageUtils.loadInto
import dev.brahmkshatriya.echo.utils.ui.AnimationUtils.setupTransition
import dev.brahmkshatriya.echo.utils.ui.AutoClearedValue.Companion.autoCleared
import dev.brahmkshatriya.echo.utils.ui.UiUtils.marquee
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HistoryFragment : Fragment(R.layout.fragment_history) {

    private var binding by autoCleared<FragmentHistoryBinding>()
    private val historyViewModel by viewModel<HistoryViewModel>()
    private val playerViewModel by activityViewModel<PlayerViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTransition(view, false, MaterialSharedAxis.X)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear_history) {
                historyViewModel.clear()
                true
            } else false
        }

        val adapter = HistoryAdapter { state ->
            playerViewModel.play(state.extensionId, state.item, false)
        }
        binding.recyclerView.adapter = adapter
        observe(historyViewModel.items) { adapter.submitList(it) }
    }

    class HistoryAdapter(
        private val onItemClicked: (MediaState.Unloaded<Track>) -> Unit,
    ) : ListAdapter<MediaState.Unloaded<Track>, HistoryAdapter.ViewHolder>(DiffCallback) {

        object DiffCallback : DiffUtil.ItemCallback<MediaState.Unloaded<Track>>() {
            override fun areItemsTheSame(a: MediaState.Unloaded<Track>, b: MediaState.Unloaded<Track>) =
                a.item.id == b.item.id

            override fun areContentsTheSame(a: MediaState.Unloaded<Track>, b: MediaState.Unloaded<Track>) =
                a == b
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPlaylistTrackBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            binding.playlistItemClose.isVisible = false
            binding.playlistItemDrag.isVisible = false
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = getItem(position).item
            with(holder.binding) {
                track.cover.loadInto(playlistItemImageView, R.drawable.ic_music)
                playlistItemTitle.text = track.title
                playlistItemTitle.marquee()
                playlistItemAuthor.text = track.subtitleWithOutE
                playlistItemAuthor.marquee()
                playlistItemNowPlaying.isVisible = false
                playlistCurrentItem.isVisible = false
                root.setOnClickListener { onItemClicked(getItem(holder.bindingAdapterPosition)) }
            }
        }

        class ViewHolder(val binding: ItemPlaylistTrackBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
