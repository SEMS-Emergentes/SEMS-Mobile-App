package pe.edu.upc.sems.shared.network.auth

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import pe.edu.upc.sems.shared.session.SessionManager

/** Adds `Authorization: Bearer <token>` to every request except `/auth/` endpoints. */
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.isAuthEndpoint() || request.header(AUTHORIZATION) != null) {
            return chain.proceed(request)
        }
        // OkHttp runs interceptors on its own worker threads, so blocking here is safe.
        val token = runBlocking { sessionManager.session.first() }?.tokens?.token
        return chain.proceed(if (token != null) request.withBearer(token) else request)
    }
}
