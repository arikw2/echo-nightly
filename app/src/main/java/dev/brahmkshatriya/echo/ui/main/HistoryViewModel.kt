package dev.brahmkshatriya.echo.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.extensions.MediaState
import dev.brahmkshatriya.echo.playback.ResumptionUtils.recoverRecents
import dev.brahmkshatriya.echo.playback.ResumptionUtils.clearRecents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val app: App,
) : ViewModel() {

    private val _items = MutableStateFlow<List<MediaState.Unloaded<Track>>>(emptyList())
    val items = _items.asStateFlow()

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            _items.value = app.context.recoverRecents()
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            clearRecents(app.context)
            _items.value = emptyList()
        }
    }

    init {
        reload()
    }
}
