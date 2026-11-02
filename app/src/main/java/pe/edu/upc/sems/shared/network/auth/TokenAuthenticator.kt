package pe.edu.upc.sems.shared.network.auth

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.SessionEvent
import pe.edu.upc.sems.shared.session.SessionEvents
import pe.edu.upc.sems.shared.session.SessionManager

/**
 * Handles 401 responses: refreshes the JWT once and retries the request.
 *
 * - `/auth/` endpoints are ignored (their 401 means wrong credentials).
 * - Synchronized: when several requests get 401 at once, only the first one refreshes;
 *   the others see the token already changed and just retry with it.
 * - Refresh rejected (4xx) → session cleared and [SessionEvent.Expired] emitted.
 * - Refresh failed (network, timeout, 5xx) → session kept; the original 401 is returned.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    private val refreshApi: TokenRefreshApi,
    private val sessionEvents: SessionEvents,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (request.url.isAuthEndpoint()) return null
        // Retry only once: a second 401 with a fresh token will not be fixed by another refresh.
        if (response.priorResponse != null) return null

        // OkHttp calls authenticators on its own worker threads, so blocking here is safe.
        synchronized(lock) {
            val session = runBlocking { sessionManager.session.first() } ?: return null
            val current = session.tokens

            // Another request already refreshed (or this one was sent before login): reuse the token.
            if (request.bearerToken() != current.token) return request.withBearer(current.token)

            return when (val result = refresh(current.refreshToken)) {
                is RefreshResult.Refreshed -> {
                    runBlocking { sessionManager.updateTokens(result.tokens) }
                    request.withBearer(result.tokens.token)
                }
                RefreshResult.Rejected -> {
                    runBlocking { sessionManager.clear() }
                    sessionEvents.emit(SessionEvent.Expired)
                    null
                }
                RefreshResult.Failed -> null
            }
        }
    }

    private fun refresh(refreshToken: String): RefreshResult = try {
        val response = refreshApi.refresh(RefreshRequestDto(refreshToken)).execute()
        val body = response.body()
        when {
            response.isSuccessful && body != null ->
                RefreshResult.Refreshed(AuthTokens(body.token, body.refreshToken))
            response.code() in 400..499 -> RefreshResult.Rejected
            else -> RefreshResult.Failed
        }
    } catch (e: IOException) {
        RefreshResult.Failed
    } catch (e: SerializationException) {
        RefreshResult.Failed
    }

    private sealed interface RefreshResult {
        data class Refreshed(val tokens: AuthTokens) : RefreshResult
        data object Rejected : RefreshResult
        data object Failed : RefreshResult
    }
}
