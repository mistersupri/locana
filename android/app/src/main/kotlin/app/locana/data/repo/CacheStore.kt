package app.locana.data.repo

import app.locana.data.db.CacheDao
import app.locana.data.db.CacheEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class Cached<T>(val value: T, val updatedAtMs: Long)

/** Typed JSON snapshots in Room. Used to show the last known data while offline, with its age. */
@Singleton
class CacheStore @Inject constructor(private val dao: CacheDao, private val json: Json) {
    suspend fun <T> read(key: String, ser: KSerializer<T>): Cached<T>? =
        dao.get(key)?.let { e -> runCatching { Cached(json.decodeFromString(ser, e.json), e.updatedAtMs) }.getOrNull() }

    suspend fun <T> write(key: String, ser: KSerializer<T>, value: T) {
        dao.put(CacheEntry(key, json.encodeToString(ser, value), System.currentTimeMillis()))
    }

    suspend fun clear() = dao.clear()
}
