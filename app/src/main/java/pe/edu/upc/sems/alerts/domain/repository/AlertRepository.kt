package pe.edu.upc.sems.alerts.domain.repository

import java.time.Instant
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.shared.network.ApiResult

interface AlertRepository {
    suspend fun listAlerts(userId: String): ApiResult<List<Alert>>

    /** [resolvedAt] is sent only when resolving; the backend clears it for the other statuses. */
    suspend fun updateStatus(alertId: String, status: AlertStatus, resolvedAt: Instant? = null): ApiResult<Alert>
}
