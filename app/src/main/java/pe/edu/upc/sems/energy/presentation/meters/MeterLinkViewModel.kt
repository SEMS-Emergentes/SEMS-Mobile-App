package pe.edu.upc.sems.energy.presentation.meters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.edu.upc.sems.energy.domain.model.MeterDraft
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.domain.model.Zone
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

data class MeterLinkUiState(
    val serial: String = "",
    val model: String = "",
    val sites: List<Site> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val selectedSiteId: String? = null,
    /** Null means that the meter belongs to the site without an assigned zone. */
    val selectedZoneId: String? = null,
    val loading: Boolean = true,
    val loadingZones: Boolean = false,
    val saving: Boolean = false,
    val loadError: FormError? = null,
    val zoneError: FormError? = null,
    val submitError: FormError? = null,
    val serialInvalid: Boolean = false,
    val siteMissing: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class MeterLinkViewModel @Inject constructor(
    private val energy: EnergyRepository,
    private val organizations: OrganizationRepository,
    private val sessions: SessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MeterLinkUiState())
    val uiState: StateFlow<MeterLinkUiState> = _uiState.asStateFlow()

    fun load(prefill: QrMeterPayload? = null) = viewModelScope.launch {
        val old = _uiState.value
        _uiState.value = old.copy(loading = true, loadError = null)
        val session = sessions.session.first()
        if (session == null) {
            _uiState.value = old.copy(loading = false, loadError = FormError.GENERIC)
            return@launch
        }
        val access = when (val result = organizations.getMyOrganization(session.user.userId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                _uiState.value = old.copy(loading = false, loadError = result.error.toFormError())
                return@launch
            }
        }
        val activeSiteId = session.selectedSite?.siteId
        val sites = if (access == null) emptyList() else when (val result = organizations.listSites(access.organization.id)) {
            is ApiResult.Success -> result.data.filter { site ->
                site.id == activeSiteId && (access.siteId == null || site.id == access.siteId)
            }
            is ApiResult.Failure -> {
                _uiState.value = old.copy(loading = false, loadError = result.error.toFormError())
                return@launch
            }
        }
        val siteId = activeSiteId?.takeIf { id -> sites.any { it.id == id } }
        val zonesResult: ApiResult<List<Zone>>? = if (siteId == null) null else organizations.listZones(siteId)
        val zones = (zonesResult as? ApiResult.Success<List<Zone>>)?.data.orEmpty()
        _uiState.value = old.copy(
            serial = prefill?.serial ?: old.serial,
            model = prefill?.model ?: old.model,
            sites = sites,
            zones = zones,
            selectedSiteId = siteId,
            selectedZoneId = null,
            loading = false,
            loadingZones = false,
            zoneError = (zonesResult as? ApiResult.Failure)?.error?.toFormError(),
        )
    }

    fun onSerial(value: String) {
        _uiState.value = _uiState.value.copy(serial = value, serialInvalid = false, submitError = null)
    }

    fun onModel(value: String) {
        _uiState.value = _uiState.value.copy(model = value)
    }

    fun selectSite(id: String) {
        if (_uiState.value.sites.none { it.id == id }) return
        _uiState.value = _uiState.value.copy(
            selectedSiteId = id, selectedZoneId = null, zones = emptyList(),
            loadingZones = true, siteMissing = false, zoneError = null, submitError = null,
        )
        viewModelScope.launch {
            when (val result = organizations.listZones(id)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(zones = result.data, loadingZones = false)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    loadingZones = false, zoneError = result.error.toFormError(),
                )
            }
        }
    }

    fun retryZones() {
        _uiState.value.selectedSiteId?.let(::selectSite)
    }

    fun selectZone(id: String?) {
        if (id != null && _uiState.value.zones.none { it.id == id }) return
        _uiState.value = _uiState.value.copy(selectedZoneId = id, submitError = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.loading || state.loadingZones || state.saving || state.done) return
        val payload = parseMeterQr(state.serial)
        val siteId = state.selectedSiteId?.takeIf { id -> state.sites.any { it.id == id } }
        if (payload == null || siteId == null) {
            _uiState.value = state.copy(serialInvalid = payload == null, siteMissing = siteId == null)
            return
        }
        _uiState.value = state.copy(saving = true, submitError = null, serialInvalid = false, siteMissing = false)
        viewModelScope.launch {
            val session = sessions.session.first()
            if (session == null) {
                _uiState.value = _uiState.value.copy(saving = false, submitError = FormError.GENERIC)
                return@launch
            }
            val draft = MeterDraft(payload.serial, state.model.trim().takeIf { it.isNotBlank() } ?: payload.model)
            when (val result = energy.createMeter(session.user.userId, siteId, state.selectedZoneId, draft)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(saving = false, done = true)
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    saving = false, submitError = result.error.toFormError(),
                )
            }
        }
    }
}
