package pe.edu.upc.sems.iam.domain.repository

import pe.edu.upc.sems.iam.domain.model.AuthenticatedAccount
import pe.edu.upc.sems.shared.network.ApiResult

interface AuthRepository {

    /** Logs in and stores the session. A 401 here means wrong credentials or an unverified account. */
    suspend fun login(emailAddress: String, password: String): ApiResult<Unit>

    /** Creates an account (role STAFF, like the web app) and stores the session: the user enters directly. */
    suspend fun register(fullName: String, emailAddress: String, password: String): ApiResult<Unit>

    /**
     * Activates an account with the code sent by email. Fallback flow, only needed when the backend
     * requires verification. The returned session is not stored until [startSession] is called.
     */
    suspend fun verifyAccount(code: String): ApiResult<AuthenticatedAccount>

    /** Stores a session obtained with [verifyAccount]. */
    suspend fun startSession(account: AuthenticatedAccount)

    /** Revokes the refresh token on the backend (best effort) and always clears the local session. */
    suspend fun logout()
}
