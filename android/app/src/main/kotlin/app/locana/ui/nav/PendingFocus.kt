package app.locana.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** "Open the map on this person": set by a notification, taken by the map once it has the person's pin. */
@Singleton
class PendingFocus @Inject constructor() {
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()
    fun request(id: String?) { _userId.value = id }
    fun consume() { _userId.value = null }
}
