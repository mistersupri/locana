package app.locana.data.places

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.locana.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** A restaurant, shop, museum or building from Google Places. */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val type: String? = null,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val address: String? = null,
    val openNow: Boolean? = null,
    val mapsUri: String? = null,
    val phone: String? = null,
    val website: String? = null,
)

/** The kinds of place the map can list around you. The emoji is what the marker shows. */
enum class NearbyKind(val label: String, val emoji: String, val types: List<String>) {
    FOOD("Food", "🍴", listOf("restaurant")),
    CAFE("Cafés", "☕", listOf("cafe", "coffee_shop")),
    SHOPS("Shops", "🛍️", listOf("shopping_mall", "supermarket", "convenience_store")),
    SIGHTS("Sights", "🏛️", listOf("tourist_attraction", "museum", "place_of_worship")),
    PARKS("Parks", "🌳", listOf("park", "national_park")),
}

sealed class PlacesError(message: String) : Exception(message) {
    data object NotEnabled : PlacesError("Places is not switched on for this Google Maps key. Enable \"Places API (New)\" for the project in Google Cloud.")
    data class Failed(val reason: String) : PlacesError(reason)
}

@Serializable private data class Text(val text: String? = null)
@Serializable private data class Loc(val latitude: Double, val longitude: Double)
@Serializable private data class Hours(val openNow: Boolean? = null)
@Serializable
private data class ApiPlace(
    val id: String,
    val displayName: Text? = null,
    val location: Loc? = null,
    val primaryTypeDisplayName: Text? = null,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val formattedAddress: String? = null,
    val currentOpeningHours: Hours? = null,
    val googleMapsUri: String? = null,
    val nationalPhoneNumber: String? = null,
    val websiteUri: String? = null,
)
@Serializable private data class ApiName(val displayName: Text? = null)
@Serializable private data class ApiList2(val places: List<ApiName> = emptyList())
@Serializable private data class ApiList(val places: List<ApiPlace> = emptyList())

/**
 * Google Places (New) over REST, using the app's Maps key. The key is restricted to this app in Google
 * Cloud, so each request names the package and the signing certificate like the Maps SDK does.
 * Nothing is requested until the person taps a place or a category.
 */
@Singleton
class NearbyPlaces @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("bare") private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val detailFields = "id,displayName,location,primaryTypeDisplayName,rating,userRatingCount,formattedAddress,currentOpeningHours.openNow,googleMapsUri,nationalPhoneNumber,websiteUri"

    val available: Boolean get() = BuildConfig.PLACES_API_KEY.isNotBlank()

    suspend fun details(placeId: String): Result<Place> = withContext(Dispatchers.IO) {
        runCatching {
            val req = base("https://places.googleapis.com/v1/places/$placeId", detailFields).get().build()
            json.decodeFromString<ApiPlace>(execute(req)).toPlace() ?: error("no location")
        }
    }

    suspend fun nearby(lat: Double, lon: Double, radiusM: Double, kind: NearbyKind): Result<List<Place>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildString {
                append("{\"includedTypes\":[").append(kind.types.joinToString(",") { "\"$it\"" }).append("],")
                append("\"maxResultCount\":20,\"rankPreference\":\"POPULARITY\",")
                append("\"locationRestriction\":{\"circle\":{\"center\":{\"latitude\":$lat,\"longitude\":$lon},\"radius\":$radiusM}}}")
            }
            val req = base("https://places.googleapis.com/v1/places:searchNearby", "places.id,places.displayName,places.location,places.primaryTypeDisplayName,places.rating,places.userRatingCount,places.formattedAddress")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            json.decodeFromString<ApiList>(execute(req)).places.mapNotNull { it.toPlace() }
        }
    }

    private val names = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * The nearest named place to a spot where someone stayed ("Alfamart Margonda", "Taman Kota"), or null.
     * Closest first within 60 m, any kind of place; answers are remembered so a day's timeline asks once.
     */
    suspend fun nameAt(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val key = "%.4f,%.4f".format(lat, lon)
        names[key]?.let { return@withContext it.ifEmpty { null } }
        runCatching {
            val body = "{\"maxResultCount\":1,\"rankPreference\":\"DISTANCE\",\"locationRestriction\":{\"circle\":{\"center\":{\"latitude\":$lat,\"longitude\":$lon},\"radius\":60.0}}}"
            val req = base("https://places.googleapis.com/v1/places:searchNearby", "places.displayName")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            json.decodeFromString<ApiList2>(execute(req)).places.firstOrNull()?.displayName?.text
        }.getOrNull().also { if (it != null) names[key] = it }
    }

    private fun base(url: String, fields: String) = Request.Builder().url(url)
        .header("X-Goog-Api-Key", BuildConfig.PLACES_API_KEY)
        .header("X-Goog-FieldMask", fields)
        .header("X-Android-Package", context.packageName)
        .apply { signatureSha1()?.let { header("X-Android-Cert", it) } }

    private fun execute(req: Request): String = http.newCall(req).execute().use { r ->
        val text = r.body?.string().orEmpty()
        when {
            r.isSuccessful -> text
            r.code == 403 || r.code == 401 -> throw PlacesError.NotEnabled
            else -> throw PlacesError.Failed("Places answered ${r.code}")
        }
    }

    /** SHA-1 of the certificate this APK is signed with, as Google expects it (upper-case hex, no colons). */
    private fun signatureSha1(): String? = runCatching {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
        }
        bytes?.let { MessageDigest.getInstance("SHA-1").digest(it).joinToString("") { b -> "%02X".format(b) } }
    }.getOrNull()

    private fun ApiPlace.toPlace(): Place? {
        val l = location ?: return null
        return Place(
            id = id, name = displayName?.text ?: "Place", lat = l.latitude, lon = l.longitude, type = primaryTypeDisplayName?.text,
            rating = rating, ratingCount = userRatingCount, address = formattedAddress, openNow = currentOpeningHours?.openNow,
            mapsUri = googleMapsUri, phone = nationalPhoneNumber, website = websiteUri,
        )
    }
}
