package pe.edu.upc.sems.shared.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/**
 * Runs a Retrofit call and turns every failure into an [ApiError].
 * Coroutine cancellation is rethrown, never reported as an error.
 */
suspend fun <T> safeApiCall(call: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(call())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ApiResult.Failure(e.toApiError())
}

internal fun Throwable.toApiError(): ApiError = when (this) {
    is HttpException -> {
        val message = response()?.errorBody()?.string()?.let(::parseErrorMessage)
        if (code() == 401) ApiError.Unauthorized(message) else ApiError.Http(code(), message)
    }
    is SerializationException -> ApiError.Serialization(this)
    // SocketTimeoutException is checked before the generic IOException branch.
    is SocketTimeoutException -> ApiError.Timeout
    is UnknownHostException -> ApiError.NoConnection
    is IOException -> ApiError.NoConnection
    else -> ApiError.Unknown(this)
}

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Reads `message` from the backend error body. SEMS sends two shapes:
 * `{"timestamp", "message", "error", "status"}` and `{"code", "message"}`.
 */
internal fun parseErrorMessage(body: String): String? = runCatching {
    val root = errorJson.parseToJsonElement(body) as? JsonObject
    (root?.get("message") as? JsonPrimitive)?.takeIf { it.isString }?.content
}.getOrNull()?.takeIf { it.isNotBlank() }
