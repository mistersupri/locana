package app.locana.data.repo

import app.locana.data.api.LocanaApi
import app.locana.data.api.MarkReadRequest
import app.locana.data.api.NotificationPageDto
import app.locana.data.api.apiCall
import app.locana.data.api.apiUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(private val api: LocanaApi) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    suspend fun page(cursor: String?): Result<NotificationPageDto> = apiCall { api.notifications(cursor, 30) }.onSuccess { _unread.value = it.unread }

    suspend fun markAllRead() = apiUnit { api.markRead(MarkReadRequest()) }.onSuccess { _unread.value = 0 }
    suspend fun markRead(id: String) = apiUnit { api.markRead(MarkReadRequest(listOf(id))) }.onSuccess { _unread.value = (_unread.value - 1).coerceAtLeast(0) }

    fun bump() {
        _unread.value += 1
    }
}
