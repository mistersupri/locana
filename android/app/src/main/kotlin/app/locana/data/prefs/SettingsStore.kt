package app.locana.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settings: DataStore<Preferences> by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class MapLayers(val members: Boolean = true, val places: Boolean = true, val tiles: Boolean = true, val territories: Boolean = true, val landmarks: Boolean = true)

data class Settings(
    val onboardingSeen: Boolean,
    val theme: ThemeMode,
    val layers: MapLayers,
    /** Mirror of the server's "collect location" answer, for restarts and reboots. Never set locally. */
    val lastKnownCollect: Boolean,
    val backgroundExplained: Boolean,
)

/** Small user preferences. Anything that must be enforced lives on the server, not here. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val kOnboarding = booleanPreferencesKey("onboarding_seen")
    private val kTheme = stringPreferencesKey("theme")
    private val kLayers = stringPreferencesKey("layers")
    private val kCollect = booleanPreferencesKey("last_known_collect")
    private val kBgExplained = booleanPreferencesKey("background_explained")
    private val kSeq = longPreferencesKey("sample_sequence")
    private val kInstall = stringPreferencesKey("install_id")
    private val kLastNotification = stringPreferencesKey("last_notification_at")

    val settings: Flow<Settings> = context.settings.data.map { p ->
        val l = p[kLayers]?.split(',')?.toSet()
        Settings(
            onboardingSeen = p[kOnboarding] ?: false,
            theme = p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            layers = if (l == null) MapLayers() else MapLayers("members" in l, "places" in l, "tiles" in l, "territories" in l, "landmarks" in l),
            lastKnownCollect = p[kCollect] ?: false,
            backgroundExplained = p[kBgExplained] ?: false,
        )
    }

    suspend fun snapshot(): Settings = settings.first()

    suspend fun setOnboardingSeen() = context.settings.edit { it[kOnboarding] = true }
    suspend fun setTheme(t: ThemeMode) = context.settings.edit { it[kTheme] = t.name }
    suspend fun setBackgroundExplained() = context.settings.edit { it[kBgExplained] = true }
    suspend fun setLastKnownCollect(v: Boolean) = context.settings.edit { it[kCollect] = v }

    suspend fun setLayers(l: MapLayers) = context.settings.edit {
        it[kLayers] = listOfNotNull(
            "members".takeIf { l.members }, "places".takeIf { l.places }, "tiles".takeIf { l.tiles },
            "territories".takeIf { l.territories }, "landmarks".takeIf { l.landmarks },
        ).joinToString(",")
    }

    /** Monotonic per install; the server uses it with timestamps to drop out-of-order samples. */
    suspend fun nextSequence(): Long {
        var next = 0L
        context.settings.edit {
            next = (it[kSeq] ?: 0L) + 1
            it[kSeq] = next
        }
        return next
    }

    suspend fun installId(): String {
        var id = ""
        context.settings.edit {
            id = it[kInstall] ?: UUID.randomUUID().toString().also { v -> it[kInstall] = v }
        }
        return id
    }

    suspend fun lastNotificationAt(): String? = context.settings.data.first()[kLastNotification]
    suspend fun setLastNotificationAt(iso: String) = context.settings.edit { it[kLastNotification] = iso }

    /** On sign-out: forget everything tied to the account, keep device-level choices (theme). */
    suspend fun clearAccountState() = context.settings.edit {
        it.remove(kCollect)
        it.remove(kLastNotification)
    }
}
