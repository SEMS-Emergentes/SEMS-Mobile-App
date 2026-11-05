package pe.edu.upc.sems.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.domain.repository.AlertRepository
import pe.edu.upc.sems.alerts.presentation.badge.ActiveAlertCount
import pe.edu.upc.sems.energy.domain.model.SiteConsumption
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.energy.presentation.consumption.dailyEnergy
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

data class DashboardContent(
    val siteName: String,
    /** Backend figures of the last [SUMMARY_DAYS] days for the site; null if they could not be loaded. */
    val consumption: SiteConsumption?,
    val kwhChangePercent: Double?,
    val saving: Saving?,
    /** kWh of each of the last [CHART_DAYS] days, oldest first; null if the readings could not be loaded. */
    val dailyKwh: List<Double>?,
    val hasReadings: Boolean,
    val meterCount: Int?,
    val pendingAlerts: List<Alert>,
    val alertsUnavailable: Boolean,
    val error: FormError?,
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object NoSite : DashboardUiState
    data class Success(val content: DashboardContent) : DashboardUiState
}

/** "Inicio" (Figma M05). Every figure is what the backend sent; the app only compares and adds up. */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val energy: EnergyRepository,
    private val alerts: AlertRepository,
    private val sessionManager: SessionManager,
    private val activeAlertCount: ActiveAlertCount,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val session = sessionManager.session.first()
            val site = session?.selectedSite
            if (session == null || site == null) {
                _uiState.value = DashboardUiState.NoSite
                return@launch
            }
            // Keep the figures on screen while refreshing; only the first load shows the spinner.
            if (_uiState.value !is DashboardUiState.Success) _uiState.value = DashboardUiState.Loading

            val now = Instant.now()
            val userId = session.user.userId
            val loaded = coroutineScope {
                val recent = async { energy.siteComparison(site.organizationId, SUMMARY_DAYS) }
                val doubled = async { energy.siteComparison(site.organizationId, 2 * SUMMARY_DAYS) }
                val meters = async { energy.listMetersBySite(site.siteId) }
                val readings = async {
                    energy.userReadingsInRange(userId, now.minus(Duration.ofDays(2L * CHART_DAYS)), now)
                }
                val alertList = async { alerts.listAlerts(userId) }
                Loaded(recent.await(), doubled.await(), meters.await(), readings.await(), alertList.await())
            }

            val consumption = (loaded.recent as? ApiResult.Success)?.data?.firstOrNull { it.siteId == site.siteId }
            val doubledKwh = (loaded.doubled as? ApiResult.Success)?.data?.firstOrNull { it.siteId == site.siteId }?.kwh
            val meterIds = (loaded.meters as? ApiResult.Success)?.data?.map { it.id }?.toSet()
            val siteReadings = (loaded.readings as? ApiResult.Success)?.data
                ?.let { all -> meterIds?.let { readingsOfSite(all, it) } }
            val alertList = (loaded.alerts as? ApiResult.Success)?.data

            alertList?.let { list -> activeAlertCount.set(list.count { it.status == AlertStatus.ACTIVE }) }

            _uiState.value = DashboardUiState.Success(
                DashboardContent(
                    siteName = site.siteName,
                    consumption = consumption,
                    kwhChangePercent = if (consumption != null && doubledKwh != null) {
                        kwhChangePercent(consumption.kwh, doubledKwh)
                    } else {
                        null
                    },
                    saving = siteReadings?.let { fortnightSaving(it, now) },
                    dailyKwh = siteReadings?.let { dailyEnergy(it, CHART_DAYS, now) },
                    hasReadings = siteReadings?.isNotEmpty() == true,
                    meterCount = meterIds?.size,
                    pendingAlerts = alertList?.let(::pendingAlerts).orEmpty(),
                    alertsUnavailable = alertList == null,
                    // The figures are the point of the screen: tell the user when they could not be loaded.
                    error = listOf(loaded.recent, loaded.meters, loaded.readings)
                        .filterIsInstance<ApiResult.Failure>()
                        .firstOrNull()?.error?.toFormError(),
                ),
            )
        }
    }

    private class Loaded(
        val recent: ApiResult<List<SiteConsumption>>,
        val doubled: ApiResult<List<SiteConsumption>>,
        val meters: ApiResult<List<pe.edu.upc.sems.energy.domain.model.EnergyMeter>>,
        val readings: ApiResult<List<pe.edu.upc.sems.energy.domain.model.EnergyReading>>,
        val alerts: ApiResult<List<Alert>>,
    )
}
