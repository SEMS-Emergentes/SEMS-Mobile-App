package pe.edu.upc.sems.shared.network.auth

import okhttp3.HttpUrl
import okhttp3.Request

/**
 * True for IAM endpoints under `/api/v1/auth/` (login, register, refresh, logout, verify, password reset).
 * They never carry the JWT and their 401 means "wrong credentials", not "session expired".
 */
internal fun HttpUrl.isAuthEndpoint(): Boolean =
    pathSegments.zipWithNext().any { (version, resource) -> version == "v1" && resource == "auth" }

internal fun Request.withBearer(token: String): Request =
    newBuilder().header(AUTHORIZATION, "$BEARER_PREFIX$token").build()

internal fun Request.bearerToken(): String? = header(AUTHORIZATION)?.removePrefix(BEARER_PREFIX)

internal const val AUTHORIZATION = "Authorization"
private const val BEARER_PREFIX = "Bearer "
