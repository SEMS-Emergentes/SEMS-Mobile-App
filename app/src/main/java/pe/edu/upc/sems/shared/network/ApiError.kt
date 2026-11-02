package pe.edu.upc.sems.shared.network

/**
 * Network failure, already classified so the UI can pick the right message.
 * [message] is the backend's own text when it sent one (both SEMS error shapes carry a `message` field).
 */
sealed interface ApiError {
    /** No internet or the host could not be reached. */
    data object NoConnection : ApiError

    /**
     * The request timed out. On Render's free plan this usually means the server is waking up,
     * so the UI should show "Conectando…" and allow a retry instead of a hard error.
     */
    data object Timeout : ApiError

    /** HTTP 401. Note that `/auth/login` also answers 401 for wrong credentials. */
    data class Unauthorized(val message: String?) : ApiError

    /** Any other non-2xx HTTP response. */
    data class Http(val code: Int, val message: String?) : ApiError

    /** The response could not be parsed into the expected DTO. */
    data class Serialization(val cause: Throwable) : ApiError

    data class Unknown(val cause: Throwable) : ApiError
}
