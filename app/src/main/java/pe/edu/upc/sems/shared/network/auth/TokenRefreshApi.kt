package pe.edu.upc.sems.shared.network.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Only the refresh call, used by [TokenAuthenticator]. Lives in shared so the network layer does not
 * depend on the iam context; login, register and logout belong to iam.
 * Synchronous [Call] because OkHttp authenticators are blocking.
 */
interface TokenRefreshApi {
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshRequestDto): Call<RefreshResponseDto>
}

/** IAM uses camelCase. */
@Serializable
data class RefreshRequestDto(
    @SerialName("refreshToken") val refreshToken: String,
)

/** `LoginResponse` in Swagger; only the tokens are needed here (unknown keys are ignored). */
@Serializable
data class RefreshResponseDto(
    @SerialName("token") val token: String,
    @SerialName("refreshToken") val refreshToken: String,
)
