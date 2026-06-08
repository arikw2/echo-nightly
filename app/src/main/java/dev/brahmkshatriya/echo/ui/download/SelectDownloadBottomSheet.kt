package dev.brahmkshatriya.echo.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.databinding.DialogPlaylistSaveBinding
import dev.brahmkshatriya.echo.ui.common.GridAdapter.Companion.configureGridLayout
import dev.brahmkshatriya.echo.ui.playlist.SelectableMediaAdapter
import dev.brahmkshatriya.echo.utils.ContextUtils.observe
import dev.brahmkshatriya.echo.utils.Serializer.getSerialized
import dev.brahmkshatriya.echo.utils.Serializer.putSerialized
import dev.brahmkshatriya.echo.utils.ui.AutoClearedValue.Companion.autoCleared
import dev.brahmkshatriya.echo.utils.ui.UiUtils.configureBottomBar
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class SelectDownloadBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(extensionId: String, item: EchoMediaItem) =
            SelectDownloadBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("extensionId", extensionId)
                    putSerialized("item", item)
                }
            }
    }

    private val args by lazy { requireArguments() }
    private val extensionId by lazy { args.getString("extensionId")!! }
    private val item by lazy { args.getSerialized<EchoMediaItem>("item")!!.getOrThrow() }

    private val viewModel by viewModel<SelectDownloadViewModel>()
    private val downloadViewModel by activityViewModel<DownloadViewModel>()

    private val adapter by lazy {
        SelectableMediaAdapter { _, media -> viewModel.toggle(media) }
    }

    private var binding by autoCleared<DialogPlaylistSaveBinding>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = DialogPlaylistSaveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configureBottomBar(binding.saveCont)
        binding.save.text = getString(R.string.download)
        binding.save.setIconResource(R.drawable.ic_download_for_offline)
        binding.save.setOnClickListener {
            downloadViewModel.addTracksToDownload(
                requireActivity(), extensionId, viewModel.selectedTracks(), item
            )
            dismiss()
        }
        configureGridLayout(
            binding.recyclerView,
            adapter.withHeader { viewModel.toggleAll(it) },
            false
        )
        observe(viewModel.listFlow) { list ->
            binding.loading.root.isVisible = list == null
            binding.recyclerView.isVisible = list != null
            if (list != null) {
                adapter.submitList(list)
                binding.save.isEnabled = list.any { it.second }
            }
        }
        viewModel.load(extensionId, item)
    }
}
