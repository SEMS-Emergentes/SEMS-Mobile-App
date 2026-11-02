package pe.edu.upc.sems.iam.presentation.common

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Field validation errors shared by the access forms. */
enum class FieldError { REQUIRED, INVALID_EMAIL, PASSWORD_TOO_SHORT, TERMS_REQUIRED }

object AuthValidation {
    /** Same rule as the web app and the backend (RegisterRequest: @Size(min = 6, max = 100)). */
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_PASSWORD_LENGTH = 100

    /** Backend limit for RegisterRequest.fullName. */
    const val MAX_FULL_NAME_LENGTH = 120

    /** After this long, the submit button shows "Conectando…" (a Render cold start can take over a minute). */
    const val SLOW_REQUEST_MS = 4_000L

    // Simple check; the backend is the source of truth. android.util.Patterns is avoided so it runs in JVM tests.
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun emailError(email: String): FieldError? = when {
        email.isEmpty() -> FieldError.REQUIRED
        !EMAIL_REGEX.matches(email) -> FieldError.INVALID_EMAIL
        else -> null
    }

    /** For new passwords (register, reset). Login only checks that the field is not empty. */
    fun newPasswordError(password: String): FieldError? = when {
        password.isEmpty() -> FieldError.REQUIRED
        password.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
        else -> null
    }
}

/** Runs [block] and calls [onSlow] if it is still running after [AuthValidation.SLOW_REQUEST_MS]. */
suspend fun <T> withSlowNotice(onSlow: () -> Unit, block: suspend () -> T): T = coroutineScope {
    val timer = launch {
        delay(AuthValidation.SLOW_REQUEST_MS)
        onSlow()
    }
    try {
        block()
    } finally {
        timer.cancel()
    }
}
