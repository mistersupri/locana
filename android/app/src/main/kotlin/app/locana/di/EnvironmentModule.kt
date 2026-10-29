package app.locana.di

import app.locana.BuildConfig
import app.locana.data.auth.KeystoreCipher
import app.locana.data.auth.TokenCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/** Bindings that depend on where the app runs. Tests replace this module, nothing else. */
@Module
@InstallIn(SingletonComponent::class)
object EnvironmentModule {
    @Provides @Named("baseUrl")
    fun baseUrl(): String {
        val url = BuildConfig.API_BASE_URL
        // Defense in depth with network_security_config: non-debug builds never talk plain HTTP.
        check(BuildConfig.ALLOW_CLEARTEXT || url.startsWith("https://")) { "Release builds require an https API URL" }
        return url
    }

    @Provides @Named("wsUrl")
    fun wsUrl(@Named("baseUrl") base: String): String = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws"

    @Provides @Singleton
    fun tokenCipher(): TokenCipher = KeystoreCipher()
}
