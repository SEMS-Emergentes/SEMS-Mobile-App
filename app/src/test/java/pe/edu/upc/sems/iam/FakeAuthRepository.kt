package pe.edu.upc.sems.iam

import kotlinx.coroutines.CompletableDeferred
import pe.edu.upc.sems.iam.domain.model.AuthenticatedAccount
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.SessionUser

/** Records calls and answers with configurable results; [gate] holds calls open to observe loading states. */
class FakeAuthRepository : AuthRepository {
    val loginCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<Triple<String, String, String>>()
    val verifyCalls = mutableListOf<String>()
    val startedSessions = mutableListOf<AuthenticatedAccount>()

    var loginResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var registerResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var verifyResult: ApiResult<AuthenticatedAccount> = ApiResult.Success(ACCOUNT)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun login(emailAddress: String, password: String): ApiResult<Unit> {
        loginCalls += emailAddress to password
        gate?.await()
        return loginResult
    }

    override suspend fun register(fullName: String, emailAddress: String, password: String): ApiResult<Unit> {
        registerCalls += Triple(fullName, emailAddress, password)
        gate?.await()
        return registerResult
    }

    override suspend fun verifyAccount(code: String): ApiResult<AuthenticatedAccount> {
        verifyCalls += code
        gate?.await()
        return verifyResult
    }

    override suspend fun startSession(account: AuthenticatedAccount) {
        startedSessions += account
    }

    override suspend fun logout() = Unit

    companion object {
        val ACCOUNT = AuthenticatedAccount(
            tokens = AuthTokens("jwt-1", "refresh-1"),
            user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null),
        )
    }
}
