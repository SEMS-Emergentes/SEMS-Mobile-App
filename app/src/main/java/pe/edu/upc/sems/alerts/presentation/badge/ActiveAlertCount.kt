package pe.edu.upc.sems.alerts.presentation.badge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.domain.repository.AlertRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager

/** Number of active alerts, shared by the Alerts screen and the badge of the bottom bar. */
@Singleton
class ActiveAlertCount @Inject constructor() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun set(value: Int) {
        _count.value = value
    }
}

/**
 * Keeps the bottom bar badge up to date. Like the web app's counter, a failure is not an error:
 * the badge just keeps the last known number.
 */
@HiltViewModel
class AlertBadgeViewModel @Inject constructor(
    private val alerts: AlertRepository,
    private val sessionManager: SessionManager,
    private val activeAlertCount: ActiveAlertCount,
) : ViewModel() {

    val count: StateFlow<Int> = activeAlertCount.count

    fun refresh() {
        viewModelScope.launch {
            val userId = sessionManager.session.first()?.user?.userId ?: return@launch
            val result = alerts.listAlerts(userId)
            if (result is ApiResult.Success) {
                activeAlertCount.set(result.data.count { it.status == AlertStatus.ACTIVE })
            }
        }
    }
}
