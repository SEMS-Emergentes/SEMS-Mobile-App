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
