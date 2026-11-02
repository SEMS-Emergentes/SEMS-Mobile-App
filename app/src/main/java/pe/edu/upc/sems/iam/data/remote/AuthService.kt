package pe.edu.upc.sems.iam.data.remote

import pe.edu.upc.sems.iam.data.remote.dto.LoginRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.LoginResponseDto
import pe.edu.upc.sems.iam.data.remote.dto.LogoutRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** IAM auth endpoints. `/auth/` requests never carry the JWT (see AuthInterceptor). */
interface AuthService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    /** 200 with an empty body. */
    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<Unit>
}
