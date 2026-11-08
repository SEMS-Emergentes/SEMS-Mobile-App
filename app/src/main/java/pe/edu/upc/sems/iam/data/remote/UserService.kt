package pe.edu.upc.sems.iam.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/** The logged-in user's own profile (backend UserController). Unlike `/auth/`, these carry the JWT. */
interface UserService {

    @GET("users/me")
    suspend fun me(): UserDto

    /** The backend replaces both fields: an empty one means "clear it", so the form always sends both. */
    @PUT("users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequestDto): UserDto
}

@Serializable
data class UserDto(
    @SerialName("userId") val userId: String,
    @SerialName("emailAddress") val emailAddress: String,
    @SerialName("roles") val roles: List<String> = emptyList(),
    @SerialName("fullName") val fullName: String? = null,
    @SerialName("segment") val segment: String? = null,
)

@Serializable
data class UpdateProfileRequestDto(
    @SerialName("fullName") val fullName: String,
    @SerialName("segment") val segment: String,
)
