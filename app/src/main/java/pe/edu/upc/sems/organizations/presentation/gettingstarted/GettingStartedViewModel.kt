package pe.edu.upc.sems.organizations.presentation.gettingstarted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

data class GettingStartedUiState(
    val isLoading: Boolean = true,
    val organizationId: String? = null,
    /** Null when the sites could not be loaded. */
    val siteCount: Int? = null,
    /** ORG_ADMIN, or no organization yet (the user will own it). */
    val canManage: Boolean = true,
    val loadFailed: Boolean = false,
) {
    val hasOrganization: Boolean get() = organizationId != null
    val hasSites: Boolean get() = (siteCount ?: 0) > 0
    val completedSteps: Int get() = listOf(hasOrganization, hasSites).count { it }

    /** Like the web app: the card disappears once every step is done. */
    val isVisible: Boolean get() = !isLoading && !(hasOrganization && hasSites)

    companion object {
        const val TOTAL_STEPS = 2
    }
}

/**
 * "Primeros pasos" (web: GettingStartedCard.vue): register the company, then add the first site.
 * Meter and equipment steps are added with the energy and devices contexts.
 * Reacts to the session, so it updates after registering the company or creating a site.
 */
@HiltViewModel
class GettingStartedViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GettingStartedUiState())
    val uiState: StateFlow<GettingStartedUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastKey: Pair<String?, String?>? = null

    init {
        viewModelScope.launch {
            sessionManager.session
                // Reload when the organization or the active site changes (new company, new site).
                .map { it?.membership to it?.selectedSite?.siteId }
                .distinctUntilChanged()
                .collect { (membership, siteId) ->
                    lastKey = membership?.organizationId to siteId
                    load(
                        organizationId = membership?.organizationId,
                        canManage = membership == null || membership.role == MembershipRole.ORG_ADMIN.name,
                    )
                }
        }
    }

    fun retry() {
        val organizationId = lastKey?.first ?: return
        load(organizationId, _uiState.value.canManage)
    }

    private fun load(organizationId: String?, canManage: Boolean) {
        loadJob?.cancel()
        if (organizationId == null) {
            _uiState.value = GettingStartedUiState(isLoading = false, organizationId = null, siteCount = 0, canManage = canManage)
            return
        }
        _uiState.update { it.copy(isLoading = true, organizationId = organizationId, canManage = canManage, loadFailed = false) }
        loadJob = viewModelScope.launch {
            val result = repository.listSites(organizationId)
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isLoading = false, siteCount = result.data.size)
                    // Shown as pending with a retry: better than hiding the card or blocking the screen.
                    is ApiResult.Failure -> it.copy(isLoading = false, siteCount = null, loadFailed = true)
                }
            }
        }
    }
}
