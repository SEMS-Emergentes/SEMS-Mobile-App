package pe.edu.upc.sems.alerts

import java.time.Instant
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertSeverity
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.domain.model.AlertType
import pe.edu.upc.sems.alerts.domain.repository.AlertRepository
import pe.edu.upc.sems.shared.network.ApiResult

/** Configurable fake: every call is recorded. A successful update answers with the alert in its new status. */
class FakeAlertRepository : AlertRepository {

    val listCalls = mutableListOf<String>()
    var listResult: ApiResult<List<Alert>> = ApiResult.Success(emptyList())

    /** (alert id, status, resolvedAt) of every update. */
    val updateCalls = mutableListOf<Triple<String, AlertStatus, Instant?>>()
    var updateFailure: ApiResult.Failure? = null

    override suspend fun listAlerts(userId: String): ApiResult<List<Alert>> {
        listCalls += userId
        return listResult
    }

    var preferencesResult: ApiResult<List<pe.edu.upc.sems.alerts.domain.model.NotificationPreference>> = ApiResult.Success(emptyList())

    /** (channel, enabled, minSeverity) of every preference saved. */
    val preferenceCalls = mutableListOf<Triple<String, Boolean, String?>>()
    var preferenceFailure: ApiResult.Failure? = null

    override suspend fun notificationPreferences(userId: String) = preferencesResult

    override suspend fun setNotificationPreference(
        userId: String,
        channel: String,
        enabled: Boolean,
        minSeverity: String?,
    ): ApiResult<pe.edu.upc.sems.alerts.domain.model.NotificationPreference> {
        preferenceCalls += Triple(channel, enabled, minSeverity)
        preferenceFailure?.let { return it }
        return ApiResult.Success(pe.edu.upc.sems.alerts.domain.model.NotificationPreference(channel, enabled, minSeverity ?: "medium"))
    }

    override suspend fun updateStatus(alertId: String, status: AlertStatus, resolvedAt: Instant?): ApiResult<Alert> {
        updateCalls += Triple(alertId, status, resolvedAt)
        updateFailure?.let { return it }
        val current = (listResult as? ApiResult.Success)?.data?.firstOrNull { it.id == alertId } ?: alert(alertId)
        return ApiResult.Success(current.copy(status = status, resolvedAt = resolvedAt))
    }

    companion object {
        fun alert(
            id: String,
            status: AlertStatus = AlertStatus.ACTIVE,
            severity: AlertSeverity = AlertSeverity.MEDIUM,
            deviceId: String? = null,
            triggeredAt: Instant? = Instant.parse("2026-11-05T15:00:00Z"),
            resolvedAt: Instant? = null,
        ) = Alert(
            id = id,
            deviceId = deviceId,
            type = AlertType.THRESHOLD,
            title = "Alerta $id",
            message = "Mensaje $id",
            severity = severity,
            status = status,
            triggeredAt = triggeredAt,
            resolvedAt = resolvedAt,
        )
    }
}
