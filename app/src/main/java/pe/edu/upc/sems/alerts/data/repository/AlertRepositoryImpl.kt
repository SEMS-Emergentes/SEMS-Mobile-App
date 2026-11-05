package pe.edu.upc.sems.alerts.data.repository

import java.time.Instant
import javax.inject.Inject
import pe.edu.upc.sems.alerts.data.mapper.toDomain
import pe.edu.upc.sems.alerts.data.remote.AlertService
import pe.edu.upc.sems.alerts.data.remote.dto.UpdateAlertStatusRequestDto
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.domain.repository.AlertRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.map
import pe.edu.upc.sems.shared.network.safeApiCall

class AlertRepositoryImpl @Inject constructor(
    private val service: AlertService,
) : AlertRepository {

    override suspend fun listAlerts(userId: String): ApiResult<List<Alert>> =
        safeApiCall { service.listAlerts(userId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun updateStatus(alertId: String, status: AlertStatus, resolvedAt: Instant?): ApiResult<Alert> =
        safeApiCall {
            service.updateStatus(alertId, UpdateAlertStatusRequestDto(status.wire, resolvedAt?.toString()))
        }.map { it.toDomain() }
}
