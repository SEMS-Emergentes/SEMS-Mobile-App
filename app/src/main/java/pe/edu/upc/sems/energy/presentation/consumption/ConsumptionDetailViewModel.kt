package pe.edu.upc.sems.energy.presentation.consumption

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

data class ConsumptionDetailUiState(
    /** First load of the device. Changing the period only sets [isLoadingPeriod]. */
    val isLoading: Boolean = true,
    val error: FormError? = null,
    val deviceName: String? = null,
    /** "Local San Isidro · Refrigeración", whichever parts are known. */
    val place: String? = null,
    val period: ConsumptionPeriod = ConsumptionPeriod.TODAY,
    val isLoadingPeriod: Boolean = false,
    /** The readings of the period could not be loaded (the header still shows). */
    val periodError: FormError? = null,
    val summary: ConsumptionSummary? = null,
)

/** "Detalle de consumo" of one device (Figma M13). */
@HiltViewModel
class ConsumptionDetailViewModel @Inject constructor(
    private val devices: DeviceRepository,
    private val energy: EnergyRepository,
    private val organizations: OrganizationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsumptionDetailUiState())
    val uiState: StateFlow<ConsumptionDetailUiState> = _uiState.asStateFlow()

    private var deviceId: String? = null
    private var job: Job? = null

    fun start(deviceId: String) {
        if (this.deviceId == deviceId) return
        this.deviceId = deviceId
        load()
    }

    fun retry() = load()

    fun onPeriodChange(period: ConsumptionPeriod) {
        if (period == _uiState.value.period && _uiState.value.periodError == null) return
        _uiState.update { it.copy(period = period) }
        job?.cancel()
        job = viewModelScope.launch { loadReadings(period) }
    }

    private fun load() {
        val id = deviceId ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            val device = when (val result = devices.getDevice(id)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = result.error.toFormError()) }
                    return@launch
                }
            }
            // Site and zone names only decorate the header: without them the screen still works.
            val place = coroutineScope {
                val siteName = async {
                    val access = (organizations.getMyOrganization(userId) as? ApiResult.Success)?.data
                    access?.let { (organizations.listSites(it.organization.id) as? ApiResult.Success)?.data }
                        ?.firstOrNull { it.id == device.siteId }?.name
                }
                val zoneName = async {
                    device.zoneId?.let { zoneId ->
                        (organizations.listZones(device.siteId) as? ApiResult.Success)?.data
                            ?.firstOrNull { it.id == zoneId }?.name
                    }
                }
                listOfNotNull(siteName.await(), zoneName.await()).joinToString(" · ").ifEmpty { null }
            }
            _uiState.update { it.copy(isLoading = false, deviceName = device.name, place = place) }
            loadReadings(_uiState.value.period)
        }
    }

    private suspend fun loadReadings(period: ConsumptionPeriod) {
        val id = deviceId ?: return
        val userId = sessionManager.session.first()?.user?.userId ?: return
        _uiState.update { it.copy(isLoadingPeriod = true, periodError = null) }
        val now = Instant.now()
        when (val result = energy.deviceReadingsInRange(userId, id, periodStart(period, now), now)) {
            is ApiResult.Success -> _uiState.update {
                if (it.period != period) it else it.copy(isLoadingPeriod = false, summary = summarize(result.data, period, now))
            }
            is ApiResult.Failure -> _uiState.update {
                if (it.period != period) it else it.copy(isLoadingPeriod = false, periodError = result.error.toFormError())
            }
        }
    }
}
