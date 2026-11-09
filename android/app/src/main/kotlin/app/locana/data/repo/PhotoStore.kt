package app.locana.data.repo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.locana.data.api.LocanaApi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile photos, fetched once per version and kept in memory. The version comes from the profile or
 * member data, so a changed photo has a new key and the old one simply ages out. People without a
 * photo, or whose photo the server will not show us, are remembered so we do not ask again.
 */
@Singleton
class PhotoStore @Inject constructor(private val api: LocanaApi) {
    private val cache = LruCache<String, Bitmap>(60)
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun peek(userId: String, version: Long): Bitmap? = cache.get(key(userId, version))

    suspend fun get(userId: String, version: Long): Bitmap? {
        val k = key(userId, version)
        cache.get(k)?.let { return it }
        if (k in missing) return null
        val bmp = runCatching { api.photo(userId).use { BitmapFactory.decodeStream(it.byteStream()) } }.getOrNull()
        if (bmp == null) missing += k else cache.put(k, bmp)
        return bmp
    }

    private fun key(userId: String, version: Long) = "$userId:$version"
}
