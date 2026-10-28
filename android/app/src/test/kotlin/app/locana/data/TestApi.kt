package app.locana.data

import app.locana.data.api.LocanaApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val testJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }

fun MockWebServer.api(): LocanaApi = Retrofit.Builder()
    .baseUrl(url("/"))
    .client(OkHttpClient())
    .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(LocanaApi::class.java)
