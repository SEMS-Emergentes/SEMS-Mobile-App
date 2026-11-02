package pe.edu.upc.sems.iam.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// IAM uses camelCase (Swagger: LoginRequest, LoginResponse, LogoutRequest, RegisterRequest, VerifyRequest,
// ForgotPasswordRequest, ResetPasswordRequest).

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

@Serializable
data class RegisterRequestDto(
    @SerialName("emailAddress") val emailAddress: String,
    @SerialName("password") val password: String,
    /** IAM RoleName. No default value on purpose: kotlinx does not encode defaults and the backend requires it. */
    @SerialName("role") val role: String,
    @SerialName("fullName") val fullName: String,
    @SerialName("segment") val segment: String? = null,
)

@Serializable
data class VerifyRequestDto(
    @SerialName("token") val token: String,
)

@Serializable
data class ForgotPasswordRequestDto(
    @SerialName("emailAddress") val emailAddress: String,
)

@Serializable
data class ResetPasswordRequestDto(
    @SerialName("token") val token: String,
    @SerialName("newPassword") val newPassword: String,
)
