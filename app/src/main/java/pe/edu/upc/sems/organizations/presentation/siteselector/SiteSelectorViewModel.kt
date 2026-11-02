package pe.edu.upc.sems.organizations.presentation.siteselector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SelectedSite
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionManager

data class SiteSelectorUiState(
    /** Active site, from the session: shown right away, even offline. */
    val selected: SelectedSite? = null,
    /** Sites the membership reaches; null while unknown (not loaded yet or offline). */
    val sites: List<Site>? = null,
    val isSheetOpen: Boolean = false,
) {
    /** With a single site the selector is fixed: nothing to choose. */
    val canChange: Boolean get() = (sites?.size ?: 0) > 1
}

/**
 * "Selector de local": which site the user is working on. The choice lives in the session and is sent with
 * site-scoped queries. The web app has no global selector (each view has its own),
 * so this follows the business rule: always show the active site.
 */
@HiltViewModel
class SiteSelectorViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteSelectorUiState())
    val uiState: StateFlow<SiteSelectorUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionManager.session.collect { session ->
                _uiState.update { it.copy(selected = session?.selectedSite) }
            }
        }
        viewModelScope.launch {
            sessionManager.session
                // A new user or organization (login, company registered) needs a fresh list.
                .distinctUntilChangedBy { Triple(it?.user?.userId, it?.membership?.organizationId, it?.membership?.siteId) }
                .collect { session ->
                    if (session?.membership == null) {
                        loadJob?.cancel()
                        _uiState.update { it.copy(sites = null, isSheetOpen = false) }
                    } else {
                        load(session, force = true)
                    }
                }
        }
    }

    /** Called when a main screen is shown (e.g. after creating or archiving a site). */
    fun refresh() {
        viewModelScope.launch {
            sessionManager.session.first()?.takeIf { it.membership != null }?.let { load(it, force = false) }
        }
    }

    fun onOpen() {
        if (!_uiState.value.canChange) return
        _uiState.update { it.copy(isSheetOpen = true) }
        refresh()
    }

    fun onDismiss() {
        _uiState.update { it.copy(isSheetOpen = false) }
    }

    fun onSelect(site: Site) {
        _uiState.update { it.copy(isSheetOpen = false) }
        viewModelScope.launch { sessionManager.selectSite(site.toSelected()) }
    }

    private fun load(session: Session, force: Boolean) {
        if (!force && loadJob?.isActive == true) return
        loadJob?.cancel()
        val membership = session.membership ?: return
        loadJob = viewModelScope.launch {
            when (val result = repository.listSites(membership.organizationId)) {
                is ApiResult.Success -> {
                    val sites = result.data
                    _uiState.update { it.copy(sites = sites) }
                    ensureValidSelection(sites, membership.organizationId, membership.siteId)
                }
                // Offline or Render waking up: keep the cached active site.
                is ApiResult.Failure -> Unit
            }
        }
    }

    /**
     * The active site must be one of the sites the user reaches. Otherwise (none yet, archived, another
     * organization) it becomes the membership's site, or the first one. No sites: no active site.
     */
    private suspend fun ensureValidSelection(sites: List<Site>, organizationId: String, membershipSiteId: String?) {
        val current = sessionManager.session.first()?.selectedSite
        val match = sites.firstOrNull { it.id == current?.siteId }
        val next = when {
            current != null && current.organizationId == organizationId && match != null ->
                // Still valid: only refresh a renamed site.
                if (match.name == current.siteName) return else match
            else -> sites.firstOrNull { it.id == membershipSiteId } ?: sites.firstOrNull()
        }
        if (next == null && current == null) return
        sessionManager.selectSite(next?.toSelected())
    }

    private fun Site.toSelected() = SelectedSite(organizationId, id, name)
}
