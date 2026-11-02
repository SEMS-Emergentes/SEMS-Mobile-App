package pe.edu.upc.sems.iam.presentation.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.iam.presentation.common.AuthValidation
import pe.edu.upc.sems.iam.presentation.common.FieldError
import pe.edu.upc.sems.shared.util.withSlowNotice
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult

enum class RegisterError { EMAIL_TAKEN, NO_CONNECTION, TIMEOUT, GENERIC }

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val termsAccepted: Boolean = false,
    val fullNameError: FieldError? = null,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val termsError: FieldError? = null,
    val isLoading: Boolean = false,
    val isSlow: Boolean = false,
    val error: RegisterError? = null,
)

/**
 * Sign-up (M02 without the RUC: the company's RUC is asked in "Primeros pasos").
 * The backend returns a session, so on success the app enters directly.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFullNameChange(value: String) {
        if (value.length > AuthValidation.MAX_FULL_NAME_LENGTH) return
        _uiState.update { it.copy(fullName = value, fullNameError = null, error = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        if (value.length > AuthValidation.MAX_PASSWORD_LENGTH) return
        _uiState.update { it.copy(password = value, passwordError = null, error = null) }
    }

    fun onTermsAcceptedChange(value: Boolean) {
        _uiState.update { it.copy(termsAccepted = value, termsError = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isLoading) return

        val fullName = state.fullName.trim()
        val email = state.email.trim()
        val fullNameError = if (fullName.isEmpty()) FieldError.REQUIRED else null
        val emailError = AuthValidation.emailError(email)
        val passwordError = AuthValidation.newPasswordError(state.password)
        val termsError = if (state.termsAccepted) null else FieldError.TERMS_REQUIRED
        if (listOf(fullNameError, emailError, passwordError, termsError).any { it != null }) {
            _uiState.update {
                it.copy(
                    fullNameError = fullNameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    termsError = termsError,
                    error = null,
                )
            }
            return
        }

        _uiState.update { it.copy(fullName = fullName, email = email, isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                authRepository.register(fullName, email, state.password)
            }
            _uiState.update {
                when (result) {
                    // The session is stored: the app leaves the access screens on its own.
                    is ApiResult.Success -> it
                    is ApiResult.Failure -> it.copy(isLoading = false, isSlow = false, error = result.error.toRegisterError())
                }
            }
        }
    }

    private fun ApiError.toRegisterError(): RegisterError = when (this) {
        // 409 "Email already exists".
        is ApiError.Http -> if (code == 409) RegisterError.EMAIL_TAKEN else RegisterError.GENERIC
        ApiError.NoConnection -> RegisterError.NO_CONNECTION
        ApiError.Timeout -> RegisterError.TIMEOUT
        is ApiError.Unauthorized, is ApiError.Serialization, is ApiError.Unknown -> RegisterError.GENERIC
    }
}
