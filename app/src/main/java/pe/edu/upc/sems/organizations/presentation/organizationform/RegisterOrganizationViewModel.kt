package pe.edu.upc.sems.organizations.presentation.organizationform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.organizations.domain.model.BusinessType
import pe.edu.upc.sems.organizations.domain.model.NewOrganization
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.domain.usecase.RefreshMembershipUseCase
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.util.withSlowNotice

data class RegisterOrganizationUiState(
    val legalName: String = "",
    val tradeName: String = "",
    val taxId: String = "",
    /** Same default as the web app. */
    val businessType: BusinessType = BusinessType.SUPERMARKET,
    val legalNameError: OrgFieldError? = null,
    val taxIdError: OrgFieldError? = null,
    val isLoading: Boolean = false,
    val isSlow: Boolean = false,
    val error: FormError? = null,
    /** One-shot: the organization was registered; the screen goes back. */
    val done: Boolean = false,
)

/**
 * "Registra tu organización" (first step of "Primeros pasos"). The backend makes the owner ORG_ADMIN;
 * afterwards the membership is refreshed so the drawer shows the organization and the role.
 */
@HiltViewModel
class RegisterOrganizationViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
    private val refreshMembership: RefreshMembershipUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterOrganizationUiState())
    val uiState: StateFlow<RegisterOrganizationUiState> = _uiState.asStateFlow()

    fun onLegalNameChange(value: String) {
        _uiState.update { it.copy(legalName = value, legalNameError = null, error = null) }
    }

    fun onTradeNameChange(value: String) {
        _uiState.update { it.copy(tradeName = value, error = null) }
    }

    /** Only digits, up to 11 (RUC). */
    fun onTaxIdChange(value: String) {
        val digits = value.filter(Char::isDigit).take(RUC_LENGTH)
        _uiState.update { it.copy(taxId = digits, taxIdError = null, error = null) }
    }

    fun onBusinessTypeChange(value: BusinessType) {
        _uiState.update { it.copy(businessType = value) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isLoading) return
        val legalNameError = if (state.legalName.isBlank()) OrgFieldError.REQUIRED else null
        // Peruvian RUC: 11 digits. Checked here to save a round trip, as the web app does.
        val taxIdError = when {
            state.taxId.isEmpty() -> OrgFieldError.REQUIRED
            !RUC_REGEX.matches(state.taxId) -> OrgFieldError.INVALID_RUC
            else -> null
        }
        if (legalNameError != null || taxIdError != null) {
            _uiState.update { it.copy(legalNameError = legalNameError, taxIdError = taxIdError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val ownerUserId = sessionManager.session.first()?.user?.userId
            if (ownerUserId == null) {
                _uiState.update { it.copy(isLoading = false, error = FormError.GENERIC) }
                return@launch
            }
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                repository.createOrganization(
                    ownerUserId,
                    NewOrganization(state.legalName, state.tradeName, state.taxId, state.businessType),
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    // New membership (ORG_ADMIN) for the drawer and the menu.
                    refreshMembership()
                    _uiState.update { it.copy(isLoading = false, isSlow = false, done = true) }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, isSlow = false, error = result.error.toFormError()) }
            }
        }
    }

    fun onDoneHandled() {
        _uiState.update { it.copy(done = false) }
    }

    companion object {
        const val RUC_LENGTH = 11
        private val RUC_REGEX = Regex("^\\d{11}$")
    }
}
