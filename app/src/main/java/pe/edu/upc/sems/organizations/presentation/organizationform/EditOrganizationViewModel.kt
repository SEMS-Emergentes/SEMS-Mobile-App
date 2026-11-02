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
import pe.edu.upc.sems.organizations.domain.model.OrganizationDetails
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.domain.usecase.RefreshMembershipUseCase
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.util.withSlowNotice

data class EditOrganizationUiState(
    val isLoadingOrganization: Boolean = true,
    val loadError: FormError? = null,
    val organizationId: String? = null,
    val legalName: String = "",
    val tradeName: String = "",
    /** Read only: the backend does not allow changing the RUC. */
    val taxId: String = "",
    val businessType: BusinessType = BusinessType.SUPERMARKET,
    val legalNameError: OrgFieldError? = null,
    val isLoading: Boolean = false,
    val isSlow: Boolean = false,
    val error: FormError? = null,
    val done: Boolean = false,
)

/** "Editar organización" (web: SitesView.vue, ORG_ADMIN only). */
@HiltViewModel
class EditOrganizationViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
    private val refreshMembership: RefreshMembershipUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditOrganizationUiState())
    val uiState: StateFlow<EditOrganizationUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoadingOrganization = true, loadError = null) }
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            when (val result = repository.getMyOrganization(userId)) {
                is ApiResult.Success -> {
                    val organization = result.data?.organization
                    _uiState.update {
                        if (organization == null) {
                            it.copy(isLoadingOrganization = false, loadError = FormError.NOT_FOUND)
                        } else {
                            it.copy(
                                isLoadingOrganization = false,
                                organizationId = organization.id,
                                legalName = organization.legalName,
                                tradeName = organization.tradeName.orEmpty(),
                                taxId = organization.taxId,
                                businessType = organization.businessType,
                            )
                        }
                    }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoadingOrganization = false, loadError = result.error.toFormError()) }
            }
        }
    }

    fun onLegalNameChange(value: String) {
        _uiState.update { it.copy(legalName = value, legalNameError = null, error = null) }
    }

    fun onTradeNameChange(value: String) {
        _uiState.update { it.copy(tradeName = value, error = null) }
    }

    fun onBusinessTypeChange(value: BusinessType) {
        _uiState.update { it.copy(businessType = value) }
    }

    fun onSubmit() {
        val state = _uiState.value
        val organizationId = state.organizationId ?: return
        if (state.isLoading) return
        if (state.legalName.isBlank()) {
            _uiState.update { it.copy(legalNameError = OrgFieldError.REQUIRED) }
            return
        }
        _uiState.update { it.copy(isLoading = true, isSlow = false, error = null) }
        viewModelScope.launch {
            val result = withSlowNotice(onSlow = { _uiState.update { it.copy(isSlow = true) } }) {
                repository.updateOrganization(
                    organizationId,
                    OrganizationDetails(state.legalName, state.tradeName, state.businessType),
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    // The drawer shows the legal name.
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
}
