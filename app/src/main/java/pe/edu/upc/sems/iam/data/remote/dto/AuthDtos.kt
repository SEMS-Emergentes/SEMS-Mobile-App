package pe.edu.upc.sems.iam.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// IAM uses camelCase (Swagger: LoginRequest, LoginResponse, LogoutRequest).

@Serializable
data class LoginRequestDto(
    @SerialName("emailAddress") val emailAddress: String,
    @SerialName("password") val password: String,
)

@Serializable
data class LoginResponseDto(
    @SerialName("token") val token: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("userId") val userId: String,
    @SerialName("emailAddress") val emailAddress: String,
    @SerialName("roles") val roles: List<String> = emptyList(),
    @SerialName("fullName") val fullName: String? = null,
    @SerialName("segment") val segment: String? = null,
)

@Serializable
data class LogoutRequestDto(
    @SerialName("refreshToken") val refreshToken: String,
)
