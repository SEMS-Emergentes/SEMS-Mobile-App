package pe.edu.upc.sems.iam.presentation.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.iam.domain.model.AuthenticatedAccount
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.iam.presentation.common.FieldError
import pe.edu.upc.sems.iam.presentation.common.withSlowNotice
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult

enum class VerifyError { INVALID_CODE, NO_CONNECTION, TIMEOUT, GENERIC }

data class VerifyAccountUiState(
    val code: String = "",
    val codeError: FieldError? = null,
    val isLoading: Boolean = false,
    val isSlow: Boolean = false,
    val error: VerifyError? = null,
    /** Set when the code was accepted: the success screen (M29) is shown. */
    val verifiedAccount: AuthenticatedAccount? = null,
)

/**
 * Fallback flow, only used when the backend requires email verification (REQUIRE_VERIFICATION):
 * the user types the code from the email, then sees M29. The session returned by /auth/verify
 * is stored only on "Continuar", so M29 is visible before the app switches to the main graph.
 */
@HiltViewModel
class VerifyAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyAccountUiState())
    val uiState: StateFlow<VerifyAccountUiState> = _uiState.asStateFlow()

    fun onCodeChange(value: String) {
        _uiState.update { it.copy(code = value, codeError = null, error = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isLoading) return
        val code = state.code.trim()
        if (code.isEmpty()) {
            _uiState.update { it.copy(codeError = FieldError.REQUIRED) }
            return
        }

        _uiState.update { it.copy(code = code, isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                authRepository.verifyAccount(code)
            }
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isLoading = false, isSlow = false, verifiedAccount = result.data)
                    is ApiResult.Failure -> it.copy(isLoading = false, isSlow = false, error = result.error.toVerifyError())
                }
            }
        }
    }

    /** "Continuar" on M29: stores the session; the app then shows the main graph. */
    fun onContinue() {
        val account = _uiState.value.verifiedAccount ?: return
        viewModelScope.launch { authRepository.startSession(account) }
    }

    private fun ApiError.toVerifyError(): VerifyError = when (this) {
        // A wrong or expired code is rejected with a client error.
        is ApiError.Unauthorized -> VerifyError.INVALID_CODE
        is ApiError.Http -> if (code in 400..499) VerifyError.INVALID_CODE else VerifyError.GENERIC
        ApiError.NoConnection -> VerifyError.NO_CONNECTION
        ApiError.Timeout -> VerifyError.TIMEOUT
        is ApiError.Serialization, is ApiError.Unknown -> VerifyError.GENERIC
    }
}
