package pe.edu.upc.sems.shared.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pe.edu.upc.sems.BuildConfig
import pe.edu.upc.sems.shared.network.auth.AuthInterceptor
import pe.edu.upc.sems.shared.network.auth.TokenAuthenticator
import pe.edu.upc.sems.shared.network.auth.TokenRefreshApi
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** OkHttp client without JWT handling. Used only for `POST /auth/refresh`, so a refresh can never trigger itself. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UnauthenticatedClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Render's free plan sleeps; waking it up was measured at ~78 s. */
    private const val TIMEOUT_SECONDS = 90L

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // The backend may add fields; old app versions must keep working.
        ignoreUnknownKeys = true
        // Unknown enum values or nulls fall back to the DTO's default value.
        coerceInputValues = true
        // Null DTO fields are omitted from requests and missing ones decode as null.
        explicitNulls = false
    }

    @Provides
    @Singleton
    @UnauthenticatedClient
    fun provideUnauthenticatedOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    /** Main client: adds the JWT and refreshes it on 401. Shares the connection pool with the base client. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @UnauthenticatedClient baseClient: OkHttpClient,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = baseClient.newBuilder()
        .apply {
            // First, so the debug log shows the (redacted) Authorization header.
            interceptors().add(0, authInterceptor)
        }
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()

    @Provides
    @Singleton
    fun provideTokenRefreshApi(
        @UnauthenticatedClient client: OkHttpClient,
        json: Json,
    ): TokenRefreshApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()
        .create(TokenRefreshApi::class.java)
}
