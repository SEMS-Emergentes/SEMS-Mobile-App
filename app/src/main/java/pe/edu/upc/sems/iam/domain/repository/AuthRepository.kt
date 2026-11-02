package pe.edu.upc.sems.iam.domain.repository

import pe.edu.upc.sems.shared.network.ApiResult

interface AuthRepository {

    /** Logs in and stores the session. A 401 here means wrong credentials. */
    suspend fun login(emailAddress: String, password: String): ApiResult<Unit>

    /** Revokes the refresh token on the backend (best effort) and always clears the local session. */
    suspend fun logout()
}
