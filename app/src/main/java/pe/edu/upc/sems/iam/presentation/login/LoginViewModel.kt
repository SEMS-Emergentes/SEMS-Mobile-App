package pe.edu.upc.sems.iam.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult

enum class FieldError { REQUIRED, INVALID_EMAIL }

/** Login failures shown in the banner (M01). */
enum class LoginError { INVALID_CREDENTIALS, NO_CONNECTION, TIMEOUT, GENERIC }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isLoading: Boolean = false,
    /** The request is taking long (Render waking up): show "Conectando…". */
    val isSlow: Boolean = false,
    val error: LoginError? = null,
)

/**
 * Login form (M00/M01). On success the session is stored and the app switches to the main graph
 * on its own (SemsApp observes the session), so no navigation event is needed here.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, error = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isLoading) return

        val email = state.email.trim()
        val emailError = when {
            email.isEmpty() -> FieldError.REQUIRED
            !EMAIL_REGEX.matches(email) -> FieldError.INVALID_EMAIL
            else -> null
        }
        val passwordError = if (state.password.isEmpty()) FieldError.REQUIRED else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError, error = null) }
            return
        }

        _uiState.update { it.copy(email = email, isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val slowTimer = launch {
                delay(SLOW_REQUEST_MS)
                _uiState.update { it.copy(isSlow = true) }
            }
            val result = authRepository.login(email, state.password)
            slowTimer.cancel()
            _uiState.update {
                when (result) {
                    // Keep the loading state until the app leaves this screen.
                    is ApiResult.Success -> it
                    is ApiResult.Failure -> it.copy(
                        isLoading = false,
                        isSlow = false,
                        error = result.error.toLoginError(),
                    )
                }
            }
        }
    }

    private fun ApiError.toLoginError(): LoginError = when (this) {
        is ApiError.Unauthorized -> LoginError.INVALID_CREDENTIALS
        ApiError.NoConnection -> LoginError.NO_CONNECTION
        ApiError.Timeout -> LoginError.TIMEOUT
        is ApiError.Http, is ApiError.Serialization, is ApiError.Unknown -> LoginError.GENERIC
    }

    companion object {
        /** After this long, the button shows "Conectando…" (Render cold start can take ~80 s). */
        const val SLOW_REQUEST_MS = 4_000L

        // Simple check; the backend is the source of truth. android.util.Patterns is avoided so it runs in JVM tests.
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
