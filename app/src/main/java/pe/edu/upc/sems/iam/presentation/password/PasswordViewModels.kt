package pe.edu.upc.sems.iam.presentation.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.iam.presentation.common.AuthValidation
import pe.edu.upc.sems.iam.presentation.common.FieldError
import pe.edu.upc.sems.iam.presentation.common.withSlowNotice
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult

/** Failures of the password screens. INVALID_LINK only applies to M30. */
enum class PasswordError { INVALID_LINK, NO_CONNECTION, TIMEOUT, GENERIC }

private fun ApiError.toPasswordError(): PasswordError = when (this) {
    ApiError.NoConnection -> PasswordError.NO_CONNECTION
    ApiError.Timeout -> PasswordError.TIMEOUT
    // The form is validated first, so a client error on reset means a wrong or expired token.
    is ApiError.Unauthorized -> PasswordError.INVALID_LINK
    is ApiError.Http -> if (code in 400..499) PasswordError.INVALID_LINK else PasswordError.GENERIC
    is ApiError.Serialization, is ApiError.Unknown -> PasswordError.GENERIC
}

// region M03 — Recupera tu contraseña

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: FieldError? = null,
    val isLoading: Boolean = false,
    val isSlow: Boolean = false,
    val error: PasswordError? = null,
    /** One-shot: set when the link was requested; the screen then opens M04 and calls [ForgotPasswordViewModel.onSentHandled]. */
    val sentTo: String? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    /** Prefills the email typed on the login screen. */
    fun prefill(email: String) {
        if (_uiState.value.email.isEmpty()) _uiState.update { it.copy(email = email.trim()) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, error = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isLoading) return
        val email = state.email.trim()
        AuthValidation.emailError(email)?.let { error ->
            _uiState.update { it.copy(emailError = error) }
            return
        }
        _uiState.update { it.copy(email = email, isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                authRepository.requestPasswordReset(email)
            }
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isLoading = false, isSlow = false, sentTo = email)
                    // INVALID_LINK cannot happen here: the backend answers the same for any valid email.
                    is ApiResult.Failure -> it.copy(
                        isLoading = false,
                        isSlow = false,
                        error = result.error.toPasswordError().takeUnless { e -> e == PasswordError.INVALID_LINK }
                            ?: PasswordError.GENERIC,
                    )
                }
            }
        }
    }

    fun onSentHandled() {
        _uiState.update { it.copy(sentTo = null) }
    }
}

// endregion

// region M04 — Revisa tu correo

data class ResetLinkSentUiState(
    /** Seconds until "Reenviar enlace" is enabled again. */
    val resendSecondsLeft: Int = ResetLinkSentViewModel.RESEND_COOLDOWN_SECONDS,
    val isResending: Boolean = false,
    val resent: Boolean = false,
    val error: PasswordError? = null,
)

@HiltViewModel
class ResetLinkSentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetLinkSentUiState())
    val uiState: StateFlow<ResetLinkSentUiState> = _uiState.asStateFlow()

    private var countdown: Job? = null

    init {
        startCountdown()
    }

    fun onResend(email: String) {
        val state = _uiState.value
        if (state.isResending || state.resendSecondsLeft > 0) return
        _uiState.update { it.copy(isResending = true, resent = false, error = null) }
        viewModelScope.launch {
            val result = authRepository.requestPasswordReset(email)
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isResending = false, resent = true)
                    is ApiResult.Failure -> it.copy(isResending = false, error = result.error.toPasswordError())
                }
            }
            if (result is ApiResult.Success) startCountdown()
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        _uiState.update { it.copy(resendSecondsLeft = RESEND_COOLDOWN_SECONDS) }
        countdown = viewModelScope.launch {
            while (_uiState.value.resendSecondsLeft > 0) {
                delay(1_000)
                _uiState.update { it.copy(resendSecondsLeft = it.resendSecondsLeft - 1) }
            }
        }
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}

// endregion

// region M30 — Crea una nueva contraseña

data class ResetPasswordUiState(
    /** False when the token came with the link (App Links): the paste field is hidden. */
    val needsLink: Boolean = true,
    val link: String = "",
    val linkError: FieldError? = null,
    val password: String = "",
    val confirmation: String = "",
    val passwordError: FieldError? = null,
    val confirmationError: FieldError? = null,
    val isLoading: Boolean = false,
    val isSlow: Boolean = false,
    val error: PasswordError? = null,
    /** One-shot: the password was changed; the screen goes back to the login. */
    val done: Boolean = false,
) {
    val hasMinLength: Boolean get() = password.length >= AuthValidation.RESET_MIN_PASSWORD_LENGTH
    val passwordsMatch: Boolean get() = password.isNotEmpty() && password == confirmation
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private var tokenFromLink: String? = null

    /** Token received with an App Link (`/reset-password?token=…`); null when the user has to paste the link. */
    fun setTokenFromLink(token: String?) {
        if (token.isNullOrBlank() || tokenFromLink != null) return
        tokenFromLink = token
        _uiState.update { it.copy(needsLink = false) }
    }

    fun onLinkChange(value: String) {
        _uiState.update { it.copy(link = value, linkError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        if (value.length > AuthValidation.MAX_PASSWORD_LENGTH) return
        _uiState.update { it.copy(password = value, passwordError = null, error = null) }
    }

    fun onConfirmationChange(value: String) {
        if (value.length > AuthValidation.MAX_PASSWORD_LENGTH) return
        _uiState.update { it.copy(confirmation = value, confirmationError = null, error = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isLoading) return

        val token = tokenFromLink ?: AuthValidation.extractResetToken(state.link)
        val linkError = when {
            !state.needsLink -> null
            state.link.isBlank() -> FieldError.REQUIRED
            token == null -> FieldError.INVALID_RESET_LINK
            else -> null
        }
        val passwordError = AuthValidation.resetPasswordError(state.password)
        val confirmationError = when {
            state.confirmation.isEmpty() -> FieldError.REQUIRED
            state.confirmation != state.password -> FieldError.PASSWORDS_DO_NOT_MATCH
            else -> null
        }
        if (token == null || listOf(linkError, passwordError, confirmationError).any { it != null }) {
            _uiState.update {
                it.copy(linkError = linkError, passwordError = passwordError, confirmationError = confirmationError)
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                authRepository.resetPassword(token, state.password)
            }
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isLoading = false, isSlow = false, done = true)
                    is ApiResult.Failure -> it.copy(isLoading = false, isSlow = false, error = result.error.toPasswordError())
                }
            }
        }
    }

    fun onDoneHandled() {
        _uiState.update { it.copy(done = false) }
    }
}

// endregion
