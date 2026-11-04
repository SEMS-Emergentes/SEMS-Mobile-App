package pe.edu.upc.sems.energy.presentation.monitoring

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
import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.devices.domain.model.DeviceStatus
import pe.edu.upc.sems.devices.domain.model.DeviceTypes
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

data class EquipmentReadingItem(
    val device: Device,
    val consumption: DeviceConsumption?,
    val current: EnergyReading?,
    val readingError: Boolean,
)

data class MonitoringContent(
    val siteName: String?,
    val selectedDays: Int,
    val zoneConsumption: SiteZoneConsumption?,
    val zoneError: FormError?,
    val equipment: List<EquipmentReadingItem>,
    val equipmentError: FormError?,
)

sealed interface MonitoringUiState {
    data object Loading : MonitoringUiState
    data class Success(val content: MonitoringContent) : MonitoringUiState
}

sealed interface HistoryUiState {
    data object Idle : HistoryUiState
    data object Loading : HistoryUiState
    data class Error(val error: FormError) : HistoryUiState
    data class Success(val readings: List<EnergyReading>) : HistoryUiState
}

@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val devices: DeviceRepository,
    private val energy: EnergyRepository,
    private val sessions: SessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MonitoringUiState>(MonitoringUiState.Loading)
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<HistoryUiState>(HistoryUiState.Idle)
    val history: StateFlow<HistoryUiState> = _history.asStateFlow()

    fun selectDays(days: Int) {
        if (days !in setOf(7, 14, 30)) return
        refresh(days)
    }

    fun refresh(days: Int = (uiState.value as? MonitoringUiState.Success)?.content?.selectedDays ?: 14) =
        viewModelScope.launch {
            val session = sessions.session.first()
            val site = session?.selectedSite
            if (session == null || site == null) {
                _uiState.value = MonitoringUiState.Success(
                    MonitoringContent(null, days, null, null, emptyList(), null),
                )
                return@launch
            }
            _uiState.value = MonitoringUiState.Loading
            val (zoneResult, deviceResult, consumptionResult) = coroutineScope {
                val zone = async { energy.siteZoneConsumption(site.siteId, days) }
                val deviceList = async { devices.listDevices(session.user.userId) }
                val consumptions = async { energy.listDeviceConsumptions(session.user.userId) }
                Triple(zone.await(), deviceList.await(), consumptions.await())
            }
            val visibleDevices = (deviceResult as? ApiResult.Success)?.data
                ?.filter {
                    it.siteId == site.siteId &&
                        it.status != DeviceStatus.REMOVED &&
                        !it.type.equals(DeviceTypes.LEGACY_METER, ignoreCase = true)
                }
                .orEmpty()
            val consumptions = (consumptionResult as? ApiResult.Success)?.data.orEmpty()
            val equipment = coroutineScope {
                visibleDevices.map { device ->
                    async {
                        val current = energy.currentDeviceReading(device.id)
                        EquipmentReadingItem(
                            device = device,
                            consumption = consumptions.filter { it.deviceId == device.id }
                                .maxByOrNull { it.periodEnd ?: java.time.Instant.MIN },
                            current = (current as? ApiResult.Success)?.data,
                            readingError = current is ApiResult.Failure,
                        )
                    }
                }.awaitAll()
            }
            _uiState.value = MonitoringUiState.Success(
                MonitoringContent(
                    siteName = site.siteName,
                    selectedDays = days,
                    zoneConsumption = (zoneResult as? ApiResult.Success)?.data,
                    zoneError = (zoneResult as? ApiResult.Failure)?.error?.toFormError(),
                    equipment = equipment.filter { it.current != null || it.consumption != null },
                    equipmentError = when {
                        deviceResult is ApiResult.Failure -> deviceResult.error.toFormError()
                        consumptionResult is ApiResult.Failure -> consumptionResult.error.toFormError()
                        equipment.any { it.readingError } -> FormError.GENERIC
                        else -> null
                    },
                ),
            )
        }

    fun loadHistory(deviceId: String) = viewModelScope.launch {
        _history.value = HistoryUiState.Loading
        _history.value = when (val result = energy.deviceReadingHistory(deviceId, limit = 50)) {
            is ApiResult.Success -> HistoryUiState.Success(
                result.data.sortedByDescending { it.measuredAt },
            )
            is ApiResult.Failure -> HistoryUiState.Error(result.error.toFormError())
        }
    }
}
