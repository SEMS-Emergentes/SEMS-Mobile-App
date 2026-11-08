package pe.edu.upc.sems.alerts.domain.repository

import java.time.Instant
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.shared.network.ApiResult

interface AlertRepository {
    suspend fun listAlerts(userId: String): ApiResult<List<Alert>>

    suspend fun notificationPreferences(userId: String): ApiResult<List<pe.edu.upc.sems.alerts.domain.model.NotificationPreference>>

    /** Saves whether the user wants alerts on [channel]. [minSeverity] keeps the one they had (the web sends "medium" first). */
    suspend fun setNotificationPreference(
        userId: String,
        channel: String,
        enabled: Boolean,
        minSeverity: String?,
    ): ApiResult<pe.edu.upc.sems.alerts.domain.model.NotificationPreference>

    /** [resolvedAt] is sent only when resolving; the backend clears it for the other statuses. */
    suspend fun updateStatus(alertId: String, status: AlertStatus, resolvedAt: Instant? = null): ApiResult<Alert>
}
