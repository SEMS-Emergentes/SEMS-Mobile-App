package pe.edu.upc.sems.alerts.presentation.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
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
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.domain.repository.AlertRepository
import pe.edu.upc.sems.alerts.presentation.badge.ActiveAlertCount
import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.toFormError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

/** The three tabs of the screen (M14, M15, M16). */
enum class AlertFilter(val status: AlertStatus) {
    ACTIVE(AlertStatus.ACTIVE),
    ACKNOWLEDGED(AlertStatus.ACKNOWLEDGED),
    RESOLVED(AlertStatus.RESOLVED),
}

/** An alert with the names that the backend only sends as ids. */
data class AlertItem(
    val alert: Alert,
    val deviceName: String?,
    val siteId: String?,
    val siteName: String?,
)

/** One-shot messages shown in the snackbar. */
sealed interface AlertMessage {
    /** The alert was resolved; [previous] is the status "Deshacer" goes back to. */
    data class Resolved(val alertId: String, val previous: AlertStatus) : AlertMessage
    data object ActionFailed : AlertMessage
}

data class AlertsUiState(
    val isLoading: Boolean = true,
    val error: FormError? = null,
    val items: List<AlertItem> = emptyList(),
    val filter: AlertFilter = AlertFilter.ACTIVE,
    /** Alerts with a status change in flight: their buttons are disabled. */
    val busyIds: Set<String> = emptySet(),
    val message: AlertMessage? = null,
)

/** Resolved alerts older than this are left out, as in the design ("Historial de los últimos 7 días"). */
val RESOLVED_HISTORY: Duration = Duration.ofDays(7)

/**
 * What a tab shows. Active ones come most severe first, then newest; the other tabs are newest first.
 * Resolved alerts keep only the last [RESOLVED_HISTORY], counted from when they were resolved.
 */
internal fun visibleAlerts(items: List<AlertItem>, filter: AlertFilter, now: Instant): List<AlertItem> {
    val ofStatus = items.filter { it.alert.status == filter.status }
    return when (filter) {
        AlertFilter.ACTIVE -> ofStatus.sortedWith(
            compareByDescending<AlertItem> { it.alert.severity.rank }
                .thenByDescending { it.alert.triggeredAt ?: Instant.MIN },
        )
        AlertFilter.ACKNOWLEDGED -> ofStatus.sortedByDescending { it.alert.triggeredAt ?: Instant.MIN }
        AlertFilter.RESOLVED -> ofStatus
            .filter { item ->
                val since = item.alert.resolvedAt ?: item.alert.triggeredAt
                since == null || since.isAfter(now.minus(RESOLVED_HISTORY))
            }
            .sortedByDescending { it.alert.resolvedAt ?: it.alert.triggeredAt ?: Instant.MIN }
    }
}

/** "Alertas" (Figma M14, M15, M16; web: AlertsView.vue). */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alerts: AlertRepository,
    private val devices: DeviceRepository,
    private val organizations: OrganizationRepository,
    private val sessionManager: SessionManager,
    private val activeAlertCount: ActiveAlertCount,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Called when the screen is shown, so it reflects what happened since. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            // Keep the current content while refreshing; only the first load shows the spinner.
            if (_uiState.value.items.isEmpty()) _uiState.update { it.copy(isLoading = true, error = null) }

            val list = when (val result = alerts.listAlerts(userId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> {
                    _uiState.update {
                        // With content already shown, a failed refresh must not wipe it.
                        if (it.items.isEmpty()) it.copy(isLoading = false, error = result.error.toFormError())
                        else it.copy(isLoading = false)
                    }
                    return@launch
                }
            }
            val names = loadNames(userId)
            val items = list.map { alert ->
                val device = alert.deviceId?.let(names.devices::get)
                AlertItem(
                    alert = alert,
                    deviceName = device?.name,
                    siteId = device?.siteId,
                    siteName = device?.siteId?.let(names.sites::get),
                )
            }
            _uiState.update { it.copy(isLoading = false, error = null, items = items) }
            publishCount()
        }
    }

    fun onFilterChange(filter: AlertFilter) = _uiState.update { it.copy(filter = filter) }

    /** "Reconocer": the alert goes from active to acknowledged. */
    fun onAcknowledge(alertId: String) = changeStatus(alertId, AlertStatus.ACKNOWLEDGED, offerUndo = false)

    /** "Marcar como resuelta": offers "Deshacer" in the snackbar. */
    fun onResolve(alertId: String) = changeStatus(alertId, AlertStatus.RESOLVED, offerUndo = true)

    /** "Deshacer": puts the alert back in the status it had before it was resolved. */
    fun onUndo(alertId: String, previous: AlertStatus) = changeStatus(alertId, previous, offerUndo = false)

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun changeStatus(alertId: String, status: AlertStatus, offerUndo: Boolean) {
        val current = _uiState.value
        val item = current.items.firstOrNull { it.alert.id == alertId } ?: return
        if (alertId in current.busyIds) return
        val previous = item.alert.status
        _uiState.update { it.copy(busyIds = it.busyIds + alertId) }
        viewModelScope.launch {
            val resolvedAt = if (status == AlertStatus.RESOLVED) Instant.now() else null
            when (val result = alerts.updateStatus(alertId, status, resolvedAt)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            busyIds = state.busyIds - alertId,
                            items = state.items.map { if (it.alert.id == alertId) it.copy(alert = result.data) else it },
                            message = if (offerUndo) AlertMessage.Resolved(alertId, previous) else state.message,
                        )
                    }
                    publishCount()
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(busyIds = it.busyIds - alertId, message = AlertMessage.ActionFailed) }
            }
        }
    }

    private fun publishCount() {
        activeAlertCount.set(_uiState.value.items.count { it.alert.status == AlertStatus.ACTIVE })
    }

    private class Names(
        val devices: Map<String, Device>,
        val sites: Map<String, String>,
    )

    /** Names only decorate the cards: if they cannot be loaded, the alerts still show without them. */
    private suspend fun loadNames(userId: String): Names = coroutineScope {
        val devicesJob = async { (devices.listDevices(userId) as? ApiResult.Success)?.data.orEmpty() }
        val sitesJob = async {
            val access = (organizations.getMyOrganization(userId) as? ApiResult.Success)?.data
            access?.let { (organizations.listSites(it.organization.id) as? ApiResult.Success)?.data }.orEmpty()
        }
        Names(devicesJob.await().associateBy { it.id }, sitesJob.await().associate { it.id to it.name })
    }
}
