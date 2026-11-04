package pe.edu.upc.sems.energy.presentation.meters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.model.MeterStatus
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.domain.model.Zone
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

data class MeterItem(
    val meter: EnergyMeter,
    val siteName: String?,
    val zoneName: String?,
    val latest: EnergyReading?,
    val readingError: Boolean,
)

internal const val UNASSIGNED_ZONE_FILTER = "__unassigned__"

data class MetersContent(
    val items: List<MeterItem>,
    val zones: List<Zone>,
    val zoneError: FormError? = null,
    val selectedZoneId: String? = null,
    val selectedStatus: MeterStatus? = null,
    val canManage: Boolean,
    val actionInProgress: Boolean = false,
    val actionError: FormError? = null,
) {
    val visibleItems: List<MeterItem>
        get() = items.filter { item ->
            (selectedZoneId == null ||
                (selectedZoneId == UNASSIGNED_ZONE_FILTER && item.meter.zoneId == null) ||
                item.meter.zoneId == selectedZoneId) &&
                (selectedStatus == null || item.meter.status == selectedStatus)
        }
}

sealed interface MetersUiState {
    data object Loading : MetersUiState
    data class Error(val error: FormError, val canManage: Boolean) : MetersUiState
    data class Success(val content: MetersContent) : MetersUiState
}

@HiltViewModel
class MetersViewModel @Inject constructor(
    private val energy: EnergyRepository,
    private val organizations: OrganizationRepository,
    private val sessions: SessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MetersUiState>(MetersUiState.Loading)
    val uiState: StateFlow<MetersUiState> = _uiState.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val session = sessions.session.first() ?: return@launch
        // OpenAPI does not declare role restrictions for meter operations; let the API authorize them.
        val canManage = true
        if (_uiState.value !is MetersUiState.Success) _uiState.value = MetersUiState.Loading
        val activeSite = session.selectedSite ?: run {
            _uiState.value = MetersUiState.Error(FormError.NOT_FOUND, canManage)
            return@launch
        }
        val meters = when (val result = energy.listMetersBySite(activeSite.siteId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> {
                _uiState.value = MetersUiState.Error(result.error.toFormError(), canManage)
                return@launch
            }
        }
        val zonesResult = organizations.listZones(activeSite.siteId)
        val zones = (zonesResult as? ApiResult.Success)?.data.orEmpty()
        val items = coroutineScope {
            meters.map { meter ->
                async {
                    val readingResult = energy.latestMeterReading(meter.id)
                    MeterItem(
                        meter = meter,
                        siteName = activeSite.siteName,
                        zoneName = meter.zoneId?.let { id -> zones.firstOrNull { it.id == id }?.name },
                        latest = (readingResult as? ApiResult.Success)?.data,
                        readingError = readingResult is ApiResult.Failure,
                    )
                }
            }.awaitAll()
        }
        val old = (_uiState.value as? MetersUiState.Success)?.content
        _uiState.value = MetersUiState.Success(
            MetersContent(
                items = items,
                zones = zones,
                zoneError = (zonesResult as? ApiResult.Failure)?.error?.toFormError(),
                selectedZoneId = old?.selectedZoneId?.takeIf { id ->
                    id == UNASSIGNED_ZONE_FILTER || zones.any { it.id == id }
                },
                selectedStatus = old?.selectedStatus,
                canManage = canManage,
            ),
        )
    }

    fun selectZoneFilter(zoneId: String?) = update { it.copy(selectedZoneId = zoneId) }

    fun selectStatusFilter(status: MeterStatus?) = update { it.copy(selectedStatus = status) }

    fun reassignZone(meterId: String, zoneId: String?) = performAction {
        energy.assignMeterZone(meterId, zoneId)
    }

    fun unlink(meterId: String) = performAction { energy.deleteMeter(meterId) }

    fun clearActionError() = update { it.copy(actionError = null) }

    private fun performAction(block: suspend () -> ApiResult<*>) {
        val current = (_uiState.value as? MetersUiState.Success)?.content ?: return
        if (!current.canManage || current.actionInProgress) return
        _uiState.value = MetersUiState.Success(current.copy(actionInProgress = true, actionError = null))
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> update {
                    it.copy(actionInProgress = false, actionError = result.error.toFormError())
                }
            }
        }
    }

    private fun update(transform: (MetersContent) -> MetersContent) {
        val state = _uiState.value as? MetersUiState.Success ?: return
        _uiState.value = MetersUiState.Success(transform(state.content))
    }
}
