package pe.edu.upc.sems.iam.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pe.edu.upc.sems.iam.data.mapper.toAuthenticatedAccount
import pe.edu.upc.sems.iam.data.remote.AuthService
import pe.edu.upc.sems.iam.data.remote.dto.ForgotPasswordRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.LoginRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.LoginResponseDto
import pe.edu.upc.sems.iam.data.remote.dto.LogoutRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.RegisterRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.ResetPasswordRequestDto
import pe.edu.upc.sems.iam.data.remote.dto.VerifyRequestDto
import pe.edu.upc.sems.iam.domain.model.AuthenticatedAccount
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.map
import pe.edu.upc.sems.shared.network.safeApiCall
import pe.edu.upc.sems.shared.session.SessionManager

class AuthRepositoryImpl @Inject constructor(
    private val service: AuthService,
    private val sessionManager: SessionManager,
) : AuthRepository {

    override suspend fun login(emailAddress: String, password: String): ApiResult<Unit> =
        storeSession(safeApiCall { service.login(LoginRequestDto(emailAddress, password)) })

    override suspend fun register(fullName: String, emailAddress: String, password: String): ApiResult<Unit> =
        storeSession(
            safeApiCall {
                service.register(
                    RegisterRequestDto(
                        emailAddress = emailAddress,
                        password = password,
                        role = DEFAULT_ROLE,
                        fullName = fullName,
                    ),
                )
            },
        )

    override suspend fun verifyAccount(code: String): ApiResult<AuthenticatedAccount> =
        safeApiCall { service.verify(VerifyRequestDto(code)) }.map { it.toAuthenticatedAccount() }

    override suspend fun startSession(account: AuthenticatedAccount) {
        sessionManager.saveLogin(account.tokens, account.user)
    }

    override suspend fun requestPasswordReset(emailAddress: String): ApiResult<Unit> =
        safeApiCall { service.forgotPassword(ForgotPasswordRequestDto(emailAddress)) }

    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        safeApiCall { service.resetPassword(ResetPasswordRequestDto(token, newPassword)) }

    override suspend fun logout() {
        val refreshToken = sessionManager.session.first()?.tokens?.refreshToken
        if (refreshToken != null) {
            // Best effort: the user must be able to log out even offline or with Render asleep.
            safeApiCall { service.logout(LogoutRequestDto(refreshToken)) }
        }
        sessionManager.clear()
    }

    private suspend fun storeSession(result: ApiResult<LoginResponseDto>): ApiResult<Unit> = when (result) {
        is ApiResult.Success -> {
            startSession(result.data.toAuthenticatedAccount())
            ApiResult.Success(Unit)
        }
        is ApiResult.Failure -> result
    }

    private companion object {
        /** Default IAM account role, the same one the web app sends on sign-up. */
        const val DEFAULT_ROLE = "STAFF"
    }
}
