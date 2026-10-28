package app.locana.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The circle shown on the Map and Circle tabs. Shared so both tabs agree. */
@Singleton
class CircleSelection @Inject constructor() {
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    fun select(id: String?) {
        _selected.value = id
    }

    /** Keeps the selection valid when circles change. */
    fun reconcile(ids: List<String>) {
        if (_selected.value !in ids) _selected.value = ids.firstOrNull()
    }
}
