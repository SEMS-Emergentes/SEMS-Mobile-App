package pe.edu.upc.sems.organizations.presentation.gettingstarted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionManager

data class GettingStartedUiState(
    /** True once the steps are known for sure (organization and sites loaded at least once). */
    val isKnown: Boolean = false,
    val isLoading: Boolean = true,
    val organizationId: String? = null,
    val siteCount: Int = 0,
    /** ORG_ADMIN, or no organization yet (the user will own it). */
    val canManage: Boolean = true,
    /** The last reload failed: the card keeps the last known steps and offers a retry. */
    val loadFailed: Boolean = false,
) {
    val hasOrganization: Boolean get() = organizationId != null
    val hasSites: Boolean get() = siteCount > 0
    val completedSteps: Int get() = listOf(hasOrganization, hasSites).count { it }

    /**
     * Like the web app: the card disappears once every step is done. It is shown only when a pending
     * step is known for sure, never while loading: an account with company and site must not see it.
     */
    val isVisible: Boolean get() = isKnown && !(hasOrganization && hasSites)

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
    private var lastSession: Session? = null

    init {
        viewModelScope.launch {
            sessionManager.session
                // Reload when the organization or the active site changes (new company, new site).
                .distinctUntilChangedBy { Triple(it?.user?.userId, it?.membership, it?.selectedSite?.siteId) }
                .collect { session ->
                    lastSession = session
                    load(session)
                }
        }
    }

    fun retry() {
        load(lastSession)
    }

    private fun load(session: Session?) {
        loadJob?.cancel()
        val userId = session?.user?.userId ?: run {
            // Logged out: nothing is known about the next account.
            _uiState.value = GettingStartedUiState()
            return
        }
        // Keep the last known steps while reloading, so the card does not blink.
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            // A null membership means "not loaded yet" right after login (logout clears the session) as well
            // as "no company": ask the backend instead of assuming the company is missing.
            val membership = session.membership
            val organizationId: String
            val canManage: Boolean
            if (membership != null) {
                organizationId = membership.organizationId
                canManage = membership.role == MembershipRole.ORG_ADMIN.name
            } else {
                when (val result = repository.getMyOrganization(userId)) {
                    is ApiResult.Success -> {
                        val access = result.data
                        if (access == null) {
                            _uiState.value = GettingStartedUiState(isKnown = true, isLoading = false, canManage = true)
                            return@launch
                        }
                        organizationId = access.organization.id
                        canManage = access.role == MembershipRole.ORG_ADMIN
                    }
                    is ApiResult.Failure -> {
                        failed()
                        return@launch
                    }
                }
            }
            when (val sites = repository.listSites(organizationId)) {
                is ApiResult.Success -> _uiState.value = GettingStartedUiState(
                    isKnown = true,
                    isLoading = false,
                    organizationId = organizationId,
                    siteCount = sites.data.size,
                    canManage = canManage,
                )
                is ApiResult.Failure -> failed()
            }
        }
    }

    /** Unknown steps stay hidden; known ones stay visible with a retry. */
    private fun failed() {
        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
    }
}
