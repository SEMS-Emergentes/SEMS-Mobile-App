package pe.edu.upc.sems.organizations.presentation.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.Organization
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

sealed interface SitesUiState {
    data object Loading : SitesUiState

    /** No company yet: the screen shows "Primeros pasos", like the web app's sites view. */
    data object NoOrganization : SitesUiState

    data class Error(val error: FormError) : SitesUiState

    data class Success(
        val organization: Organization,
        val role: MembershipRole,
        val sites: List<Site>,
        val activeSiteId: String?,
    ) : SitesUiState {
        val canManageOrganization: Boolean get() = role.canManageOrganization
    }
}

/** "Locales": the organization and its sites (web: SitesView.vue). */
@HiltViewModel
class SitesViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SitesUiState>(SitesUiState.Loading)
    val uiState: StateFlow<SitesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Called when the screen is shown, so it reflects changes made in the forms. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val session = sessionManager.session.first() ?: return@launch
            // Keep the current content while refreshing; only the first load shows the spinner.
            if (_uiState.value !is SitesUiState.Success) _uiState.value = SitesUiState.Loading

            val access = when (val result = repository.getMyOrganization(session.user.userId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> {
                    _uiState.value = SitesUiState.Error(result.error.toFormError())
                    return@launch
                }
            }
            if (access == null) {
                _uiState.value = SitesUiState.NoOrganization
                return@launch
            }
            when (val sites = repository.listSites(access.organization.id)) {
                is ApiResult.Success -> _uiState.value = SitesUiState.Success(
                    organization = access.organization,
                    role = access.role,
                    sites = sites.data,
                    activeSiteId = session.selectedSite?.siteId,
                )
                is ApiResult.Failure -> _uiState.value = SitesUiState.Error(sites.error.toFormError())
            }
        }
    }

    init {
        // The active site badge follows the session (e.g. after creating the first site).
        viewModelScope.launch {
            sessionManager.session.collect { session ->
                _uiState.update { state ->
                    if (state is SitesUiState.Success) state.copy(activeSiteId = session?.selectedSite?.siteId) else state
                }
            }
        }
    }
}
