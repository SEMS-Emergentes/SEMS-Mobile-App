package pe.edu.upc.sems.iam.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pe.edu.upc.sems.iam.data.mapper.toAuthTokens
import pe.edu.upc.sems.iam.data.mapper.toSessionUser
import pe.edu.upc.sems.iam.data.remote.AuthService
import pe.edu.upc.sems.iam.data.remote.dto.LoginRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.LogoutRequestDto
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.safeApiCall
import pe.edu.upc.sems.shared.session.SessionManager

class AuthRepositoryImpl @Inject constructor(
    private val service: AuthService,
    private val sessionManager: SessionManager,
) : AuthRepository {

    override suspend fun login(emailAddress: String, password: String): ApiResult<Unit> =
        when (val result = safeApiCall { service.login(LoginRequestDto(emailAddress, password)) }) {
            is ApiResult.Success -> {
                sessionManager.saveLogin(result.data.toAuthTokens(), result.data.toSessionUser())
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }

    override suspend fun logout() {
        val refreshToken = sessionManager.session.first()?.tokens?.refreshToken
        if (refreshToken != null) {
            // Best effort: the user must be able to log out even offline or with Render asleep.
            safeApiCall { service.logout(LogoutRequestDto(refreshToken)) }
        }
        sessionManager.clear()
    }
}
