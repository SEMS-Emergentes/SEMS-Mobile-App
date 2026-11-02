package pe.edu.upc.sems.iam.data.remote

import pe.edu.upc.sems.iam.data.remote.dto.ForgotPasswordRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.LoginRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.LoginResponseDto
import pe.edu.upc.sems.iam.data.remote.dto.LogoutRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.RegisterRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.ResetPasswordRequestDto
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

    /**
     * Sends the reset link by email. Always 200 with the same message, whether the account exists or not,
     * so the endpoint cannot be used to find registered emails.
     */
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequestDto)

    /** The backend requires newPassword to have at least 8 characters (sign-up only requires 6). */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequestDto)

    /** 204 with an empty body. */
    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<Unit>
}
