package pe.edu.upc.sems.organizations.presentation.sitedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.domain.model.Zone
import pe.edu.upc.sems.organizations.domain.model.ZoneDraft
import pe.edu.upc.sems.organizations.domain.model.ZoneType
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SelectedSite
import pe.edu.upc.sems.shared.session.SessionManager

/** Bottom sheet to add or edit a zone. [zoneId] is null for a new zone. */
data class ZoneEditor(
    val zoneId: String? = null,
    val name: String = "",
    /** Same default as the web app. */
    val zoneType: ZoneType = ZoneType.SALES_FLOOR,
    val operatesOffHours: Boolean = false,
    val nameError: OrgFieldError? = null,
    val isSaving: Boolean = false,
    val error: FormError? = null,
)

data class SiteDetailUiState(
    val isLoading: Boolean = true,
    val loadError: FormError? = null,
    val site: Site? = null,
    val zones: List<Zone> = emptyList(),
    val role: MembershipRole = MembershipRole.UNKNOWN,
    val zoneEditor: ZoneEditor? = null,
    val confirmingArchive: Boolean = false,
    val isArchiving: Boolean = false,
    val archiveError: FormError? = null,
    /** One-shot: the site was archived; the screen goes back. */
    val archived: Boolean = false,
) {
    /** Backend: PUT/DELETE /sites and zone endpoints use modificaLocal (ORG_ADMIN or SUPERVISOR). */
    val canEdit: Boolean get() = role.canEditSites
}

/** "Detalle del local" (M28): supply data and zones. */
@HiltViewModel
class SiteDetailViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteDetailUiState())
    val uiState: StateFlow<SiteDetailUiState> = _uiState.asStateFlow()

    private var siteId: String? = null
    private var loadJob: Job? = null

    fun start(siteId: String) {
        if (this.siteId == siteId) return
        this.siteId = siteId
        refresh()
    }

    /** Also called when coming back from "Editar local". A load already in progress is reused. */
    fun refresh() {
        val siteId = siteId ?: return
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val role = MembershipRole.from(sessionManager.session.first()?.membership?.role)
            val site = async { repository.getSite(siteId) }
            val zones = async { repository.listZones(siteId) }
            val siteResult = site.await()
            val zonesResult = zones.await()
            _uiState.update {
                when {
                    siteResult is ApiResult.Failure -> it.copy(isLoading = false, loadError = siteResult.error.toFormError())
                    zonesResult is ApiResult.Failure -> it.copy(isLoading = false, loadError = zonesResult.error.toFormError())
                    else -> it.copy(
                        isLoading = false,
                        loadError = null,
                        site = (siteResult as ApiResult.Success).data,
                        zones = (zonesResult as ApiResult.Success).data,
                        role = role,
                    )
                }
            }
        }
    }

    // region Zones

    fun onAddZone() {
        if (_uiState.value.canEdit) _uiState.update { it.copy(zoneEditor = ZoneEditor()) }
    }

    fun onZoneClick(zone: Zone) {
        if (!_uiState.value.canEdit) return
        _uiState.update {
            it.copy(zoneEditor = ZoneEditor(zone.id, zone.name, zone.zoneType, zone.operatesOffHours))
        }
    }

    fun onZoneEditorDismiss() {
        _uiState.update { it.copy(zoneEditor = null) }
    }

    fun onZoneNameChange(value: String) = updateEditor { it.copy(name = value, nameError = null, error = null) }

    fun onZoneTypeChange(value: ZoneType) = updateEditor { it.copy(zoneType = value) }

    fun onZoneOffHoursChange(value: Boolean) = updateEditor { it.copy(operatesOffHours = value) }

    fun onSaveZone() {
        val siteId = siteId ?: return
        val editor = _uiState.value.zoneEditor ?: return
        if (editor.isSaving) return
        if (editor.name.isBlank()) {
            updateEditor { it.copy(nameError = OrgFieldError.REQUIRED) }
            return
        }
        updateEditor { it.copy(isSaving = true, error = null) }
        val draft = ZoneDraft(editor.name, editor.zoneType, editor.operatesOffHours)
        viewModelScope.launch {
            val result = if (editor.zoneId == null) {
                repository.createZone(siteId, draft)
            } else {
                repository.updateZone(editor.zoneId, draft)
            }
            afterZoneChange(result)
        }
    }

    /** Archives the zone being edited (the backend changes its status, it does not delete it). */
    fun onArchiveZone() {
        val editor = _uiState.value.zoneEditor ?: return
        val zoneId = editor.zoneId ?: return
        if (editor.isSaving) return
        updateEditor { it.copy(isSaving = true, error = null) }
        viewModelScope.launch { afterZoneChange(repository.archiveZone(zoneId)) }
    }

    private suspend fun afterZoneChange(result: ApiResult<*>) {
        when (result) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(zoneEditor = null) }
                siteId?.let { id ->
                    val zones = repository.listZones(id)
                    if (zones is ApiResult.Success) _uiState.update { it.copy(zones = zones.data) }
                }
            }
            // 409: a zone with that name already exists in the site.
            is ApiResult.Failure -> updateEditor { it.copy(isSaving = false, error = result.error.toFormError()) }
        }
    }

    private fun updateEditor(transform: (ZoneEditor) -> ZoneEditor) {
        _uiState.update { state -> state.zoneEditor?.let { state.copy(zoneEditor = transform(it)) } ?: state }
    }

    // endregion

    // region Archive the site

    fun onArchiveSiteRequest() {
        if (_uiState.value.canEdit) _uiState.update { it.copy(confirmingArchive = true, archiveError = null) }
    }

    fun onArchiveSiteDismiss() {
        _uiState.update { it.copy(confirmingArchive = false) }
    }

    fun onArchiveSiteConfirm() {
        val siteId = siteId ?: return
        _uiState.update { it.copy(confirmingArchive = false, isArchiving = true, archiveError = null) }
        viewModelScope.launch {
            when (val result = repository.archiveSite(siteId)) {
                is ApiResult.Success -> {
                    replaceActiveSiteIfArchived(siteId)
                    _uiState.update { it.copy(isArchiving = false, archived = true) }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isArchiving = false, archiveError = result.error.toFormError()) }
            }
        }
    }

    /** Like the web app: if the active site was archived, the first remaining site becomes the active one. */
    private suspend fun replaceActiveSiteIfArchived(archivedSiteId: String) {
        val active = sessionManager.session.first()?.selectedSite ?: return
        if (active.siteId != archivedSiteId) return
        val remaining = (repository.listSites(active.organizationId) as? ApiResult.Success)?.data.orEmpty()
            .filter { it.id != archivedSiteId }
        sessionManager.selectSite(remaining.firstOrNull()?.let { SelectedSite(it.organizationId, it.id, it.name) })
    }

    fun onArchivedHandled() {
        _uiState.update { it.copy(archived = false) }
    }

    // endregion
}
