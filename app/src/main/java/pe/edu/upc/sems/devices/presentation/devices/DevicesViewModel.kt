package pe.edu.upc.sems.devices.presentation.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.devices.domain.model.DeviceStatus
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

/** A device with the names of its site and zone and what it consumed today. */
data class DeviceItem(
    val device: Device,
    val siteName: String?,
    val zoneName: String?,
    /** Null when the backend has no consumption for today (the card shows a dash). */
    val todayKwh: Double?,
    val todayCostSoles: Double?,
)

sealed interface DevicesUiState {
    data object Loading : DevicesUiState

    data class Error(val error: FormError) : DevicesUiState

    data class Success(
        val items: List<DeviceItem>,
        val consumptionError: FormError? = null,
    ) : DevicesUiState {
        val siteCount: Int get() = items.map { it.device.siteId }.distinct().size
    }
}

private val LIMA: ZoneId = ZoneId.of("America/Lima")

/**
 * Consumption of [deviceId] whose period ends on [today] (Lima time). The cost is the backend's
 * `costEstimateSoles`; the app never calculates soles. Null when there is none.
 */
internal fun todayConsumption(
    consumptions: List<DeviceConsumption>,
    deviceId: String,
    today: LocalDate,
): Pair<Double, Double>? {
    val matching = consumptions.filter { c ->
        c.deviceId == deviceId && c.periodEnd?.atZone(LIMA)?.toLocalDate() == today
    }
    if (matching.isEmpty()) return null
    return matching.sumOf { it.totalKwh } to matching.sumOf { it.costSoles }
}

/**
 * What [deviceId] reported today (Lima time), added up from the readings the backend sent: their `energy_kwh`
 * and the `estimated_cost` the backend put on each one. This is the web app's second step when the backend
 * has no per-device totals (energy.service.js, getDeviceConsumption). Readings without a device belong to the
 * site, not to a piece of equipment, so they never count here. Null when the device sent nothing today.
 */
internal fun todayFromReadings(
    readings: List<EnergyReading>,
    deviceId: String,
    today: LocalDate,
): Pair<Double?, Double?>? {
    val mine = readings.filter { it.deviceId == deviceId && it.measuredAt?.atZone(LIMA)?.toLocalDate() == today }
    if (mine.isEmpty()) return null
    val kwh = mine.mapNotNull { it.energyKwh }
    val cost = mine.mapNotNull { it.estimatedCostSoles }
    return kwh.takeIf { it.isNotEmpty() }?.sum() to cost.takeIf { it.isNotEmpty() }?.sum()
}

/** "Mis Dispositivos", tab Equipos (Figma M07, web: DevicesView.vue). */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val devices: DeviceRepository,
    private val organizations: OrganizationRepository,
    private val energy: EnergyRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DevicesUiState>(DevicesUiState.Loading)
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Called when the screen is shown, so it reflects changes made in the device form. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val session = sessionManager.session.first() ?: return@launch
            val userId = session.user.userId
            // Keep the current content while refreshing; only the first load shows the spinner.
            if (_uiState.value !is DevicesUiState.Success) _uiState.value = DevicesUiState.Loading

            val deviceList = when (val result = devices.listDevices(userId)) {
                is ApiResult.Success -> result.data.filter { it.status != DeviceStatus.REMOVED }
                is ApiResult.Failure -> {
                    _uiState.value = DevicesUiState.Error(result.error.toFormError())
                    return@launch
                }
            }

            // Site/zone names and today's consumption only decorate the cards: if they fail, the list still shows.
            val (names, consumptionResult) = coroutineScope {
                val namesJob = async { loadNames(userId) }
                val consumptionJob = async { energy.listDeviceConsumptions(userId) }
                namesJob.await() to consumptionJob.await()
            }
            val consumptions = (consumptionResult as? ApiResult.Success)?.data.orEmpty()
            val today = LocalDate.now(LIMA)

            // Same two steps as the web app: the backend's per-device totals when it has them and, for the
            // devices it has none for, the readings of today added up per device. Nothing in the backend
            // writes those totals yet, so in practice the readings are what shows up.
            val totals = deviceList.associate { it.id to todayConsumption(consumptions, it.id, today) }
            var consumptionError: FormError? = null
            val readings = if (totals.values.any { it == null }) {
                when (val result = energy.userReadingsInRange(userId, today.atStartOfDay(LIMA).toInstant(), Instant.now())) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure -> {
                        consumptionError = result.error.toFormError()
                        emptyList()
                    }
                }
            } else {
                emptyList()
            }
            _uiState.value = DevicesUiState.Success(
                deviceList.map { device ->
                    val figures: Pair<Double?, Double?>? = totals[device.id]?.let { it.first to it.second }
                        ?: todayFromReadings(readings, device.id, today)
                    DeviceItem(
                        device = device,
                        siteName = names.sites[device.siteId],
                        zoneName = device.zoneId?.let(names.zones::get),
                        todayKwh = figures?.first,
                        todayCostSoles = figures?.second,
                    )
                },
                consumptionError = consumptionError,
            )
        }
    }

    private class Names(val sites: Map<String, String>, val zones: Map<String, String>)

    private suspend fun loadNames(userId: String): Names {
        val access = (organizations.getMyOrganization(userId) as? ApiResult.Success)?.data
            ?: return Names(emptyMap(), emptyMap())
        val sites = (organizations.listSites(access.organization.id) as? ApiResult.Success)?.data.orEmpty()
        val zones = coroutineScope {
            sites.map { site -> async { (organizations.listZones(site.id) as? ApiResult.Success)?.data.orEmpty() } }
                .flatMap { it.await() }
        }
        return Names(sites.associate { it.id to it.name }, zones.associate { it.id to it.name })
    }
}
