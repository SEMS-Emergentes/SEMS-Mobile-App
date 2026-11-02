package pe.edu.upc.sems.iam.presentation.common

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Field validation errors shared by the access forms. */
enum class FieldError {
    REQUIRED,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT,
    RESET_PASSWORD_TOO_SHORT,
    PASSWORDS_DO_NOT_MATCH,
    TERMS_REQUIRED,
    INVALID_RESET_LINK,
}

object AuthValidation {
    /** Same rule as the web app and the backend (RegisterRequest: @Size(min = 6, max = 100)). */
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_PASSWORD_LENGTH = 100

    /**
     * The backend asks for more on reset (ResetPasswordRequest: @Size(min = 8)) than on sign-up (6).
     * Known backend inconsistency, reported to the team.
     */
    const val RESET_MIN_PASSWORD_LENGTH = 8

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

    /** Sign-up password. Login only checks that the field is not empty. */
    fun newPasswordError(password: String): FieldError? = when {
        password.isEmpty() -> FieldError.REQUIRED
        password.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
        else -> null
    }

    /** Password set from the reset link (M30). */
    fun resetPasswordError(password: String): FieldError? = when {
        password.isEmpty() -> FieldError.REQUIRED
        password.length < RESET_MIN_PASSWORD_LENGTH -> FieldError.RESET_PASSWORD_TOO_SHORT
        else -> null
    }

    private val TOKEN_PARAM = Regex("[?&]token=([^&#\\s]+)")

    /**
     * Gets the reset token from what the user pasted: the whole link from the email
     * (`https://…/reset-password?token=…`) or just the token. Null when nothing usable was pasted.
     */
    fun extractResetToken(pasted: String): String? {
        val text = pasted.trim()
        if (text.isEmpty()) return null
        TOKEN_PARAM.find(text)?.let { match ->
            // decode(String, Charset) needs API 33; minSdk is 29.
            return java.net.URLDecoder.decode(match.groupValues[1], "UTF-8").takeIf { it.isNotBlank() }
        }
        // A bare token: no spaces and not some other URL.
        return text.takeUnless { it.contains(' ') || it.contains("://") || it.contains('/') }
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
