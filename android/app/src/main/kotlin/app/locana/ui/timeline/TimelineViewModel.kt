package app.locana.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.repo.CircleRepository
import app.locana.data.repo.LocationRepository
import app.locana.ui.components.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TimelineUi(val day: LocalDate, val name: String, val items: UiState<List<TimelineItem>>)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val location: LocationRepository,
    private val circles: CircleRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val circleId: String = checkNotNull(handle["id"])
    private val userId: String = checkNotNull(handle["userId"])
    private val _ui = MutableStateFlow(TimelineUi(LocalDate.now(), "Timeline", UiState.Loading))
    val ui: StateFlow<TimelineUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            circles.cachedDetail(circleId)?.value?.members?.firstOrNull { it.userId == userId }?.let { m -> _ui.value = _ui.value.copy(name = m.displayName) }
        }
        load()
    }

    private fun load() = viewModelScope.launch {
        val day = _ui.value.day
        _ui.value = _ui.value.copy(items = UiState.Loading)
        val r = location.timeline(circleId, userId, day)
        // Ignore an answer for a day the person has already moved away from.
        if (_ui.value.day != day) return@launch
        _ui.value = _ui.value.copy(items = r.fold({ UiState.Data(buildItems(it.segments)) }, { UiState.Error(it.message ?: "Couldn't load the timeline") }))
    }

    fun retry() { load() }

    fun shift(days: Long) {
        val next = _ui.value.day.plusDays(days)
        if (next.isAfter(LocalDate.now())) return
        _ui.value = _ui.value.copy(day = next)
        load()
    }

    val isToday: Boolean get() = _ui.value.day == LocalDate.now()
}
