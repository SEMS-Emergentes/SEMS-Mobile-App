package pe.edu.upc.sems.shared.network

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import pe.edu.upc.sems.shared.network.auth.AUTHORIZATION
import pe.edu.upc.sems.shared.network.auth.isAuthEndpoint

/**
 * Debug-only HTTP log. Full bodies help to check DTOs, but `/auth/` bodies carry passwords and
 * refresh tokens, so those requests are logged without bodies. The JWT header is always redacted.
 */
internal fun debugLoggingInterceptor(
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
): Interceptor {
    val withBodies = HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader(AUTHORIZATION)
    }
    val withoutBodies = HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader(AUTHORIZATION)
    }
    return Interceptor { chain ->
        if (chain.request().url.isAuthEndpoint()) withoutBodies.intercept(chain) else withBodies.intercept(chain)
    }
}
