package app.locana.di

import android.content.Context
import androidx.room.Room
import app.locana.BuildConfig
import app.locana.data.api.LocanaApi
import app.locana.data.auth.AuthInterceptor
import app.locana.data.auth.KeystoreCipher
import app.locana.data.auth.TokenCipher
import app.locana.data.auth.TokenRefresher
import app.locana.data.auth.TokenStore
import app.locana.data.db.CacheDao
import app.locana.data.db.LocanaDatabase
import app.locana.data.realtime.RealtimeClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        coerceInputValues = true
    }

    @Provides @Named("baseUrl")
    fun baseUrl(): String {
        val url = BuildConfig.API_BASE_URL
        // Defense in depth with network_security_config: non-debug builds never talk plain HTTP.
        check(BuildConfig.ALLOW_CLEARTEXT || url.startsWith("https://")) { "Release builds require an https API URL" }
        return url
    }

    @Provides @Named("wsUrl")
    fun wsUrl(@Named("baseUrl") base: String): String = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws"

    @Provides @Singleton @Named("bare")
    fun bareClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun client(@Named("bare") bare: OkHttpClient, auth: AuthInterceptor, refresher: TokenRefresher): OkHttpClient =
        bare.newBuilder()
            .addInterceptor(auth)
            .authenticator(refresher)
            .pingInterval(25, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    // Headers only: bodies carry coordinates and tokens, which must not reach logcat.
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    })
                }
            }
            .build()

    @Provides @Singleton
    fun api(client: OkHttpClient, json: Json, @Named("baseUrl") base: String): LocanaApi = Retrofit.Builder()
        .baseUrl(base)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LocanaApi::class.java)

    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): LocanaDatabase =
        Room.databaseBuilder(ctx, LocanaDatabase::class.java, "locana.db").build()

    @Provides fun cacheDao(db: LocanaDatabase): CacheDao = db.cache()

    @Provides @Singleton
    fun tokenCipher(): TokenCipher = KeystoreCipher()

    @Provides @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun realtime(client: OkHttpClient, @Named("wsUrl") ws: String, tokens: TokenStore, refresher: TokenRefresher, scope: CoroutineScope): RealtimeClient =
        RealtimeClient(client, ws, tokens, refresher, scope)
}
