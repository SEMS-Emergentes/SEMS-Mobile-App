package pe.edu.upc.sems.iam.data.remote

import pe.edu.upc.sems.iam.data.remote.dto.LoginRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.LoginResponseDto
import pe.edu.upc.sems.iam.data.remote.dto.LogoutRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.RegisterRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.VerifyRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** IAM auth endpoints. `/auth/` requests never carry the JWT (see AuthInterceptor). */
interface AuthService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    /** Returns a full session; 409 "Email already exists" when the email is taken. */
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): LoginResponseDto

    /** Activates an account with the emailed code and returns a full session. */
    @POST("auth/verify")
    suspend fun verify(@Body body: VerifyRequestDto): LoginResponseDto

    /** 204 with an empty body. */
    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<Unit>
}
