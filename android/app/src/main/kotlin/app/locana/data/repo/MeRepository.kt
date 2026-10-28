package app.locana.data.repo

import app.locana.data.api.BlockDto
import app.locana.data.api.CosmeticsRequest
import app.locana.data.api.DeleteAccountRequest
import app.locana.data.api.DeviceRequest
import app.locana.data.api.LocanaApi
import app.locana.data.api.MeDto
import app.locana.data.api.PrivacyDto
import app.locana.data.api.PrivacyPatch
import app.locana.data.api.ReportRequest
import app.locana.data.api.UpdateProfileRequest
import app.locana.data.api.apiCall
import app.locana.data.api.apiUnit
import app.locana.data.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeRepository @Inject constructor(
    private val api: LocanaApi,
    private val cache: CacheStore,
    private val settings: SettingsStore,
) {
    companion object { const val KEY = "me" }

    private val _me = MutableStateFlow<MeDto?>(null)
    /** Latest known profile; null until loaded. */
    val me: StateFlow<MeDto?> = _me.asStateFlow()

    suspend fun loadCached(): MeDto? = cache.read(KEY, MeDto.serializer())?.value?.also { if (_me.value == null) _me.value = it }

    suspend fun refresh(): Result<MeDto> = apiCall { api.me() }.onSuccess { store(it) }

    private suspend fun store(m: MeDto) {
        _me.value = m
        cache.write(KEY, MeDto.serializer(), m)
    }

    suspend fun update(req: UpdateProfileRequest): Result<MeDto> = apiCall { api.updateMe(req) }.onSuccess { store(it) }

    suspend fun cosmetics(frame: String?, title: String?, marker: String?): Result<MeDto> =
        apiCall { api.cosmetics(CosmeticsRequest(frame, title, marker)) }.onSuccess { store(it) }

    suspend fun privacy(patch: PrivacyPatch): Result<PrivacyDto> = apiCall { api.privacy(patch) }.onSuccess { p ->
        _me.value?.let { store(it.copy(privacy = p)) }
    }

    suspend fun registerDevice(model: String, appVersion: String) =
        apiUnit { api.registerDevice(DeviceRequest(settings.installId(), model.take(80), appVersion)) }

    /** Pretty-printed JSON of everything the server holds about the user. */
    suspend fun export(): Result<String> = apiCall {
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), api.export())
    }

    suspend fun deleteAccount(password: String?) = apiUnit { api.deleteAccount(DeleteAccountRequest(password = password)) }

    suspend fun deleteHistory(): Result<Int> = apiCall { api.deleteHistory().deleted }

    suspend fun blocks(): Result<List<BlockDto>> = apiCall { api.blocks() }
    suspend fun block(userId: String) = apiUnit { api.block(userId) }
    suspend fun unblock(userId: String) = apiUnit { api.unblock(userId) }
    suspend fun report(userId: String, reason: String, details: String?, circleId: String?) =
        apiCall { api.report(userId, ReportRequest(reason, details?.takeIf { it.isNotBlank() }, circleId)) }

    fun clear() {
        _me.value = null
    }
}
