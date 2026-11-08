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

    override suspend fun notificationPreferences(userId: String) =
        safeApiCall { service.listNotificationPreferences(userId) }.map { list -> list.map { it.toPreference() } }

    override suspend fun setNotificationPreference(userId: String, channel: String, enabled: Boolean, minSeverity: String?) =
        safeApiCall {
            service.saveNotificationPreference(
                pe.edu.upc.sems.alerts.data.remote.dto.SaveNotificationPreferenceRequestDto(
                    userId = userId,
                    channel = channel,
                    enabled = enabled,
                    minSeverity = minSeverity ?: DEFAULT_MIN_SEVERITY,
                ),
            )
        }.map { it.toPreference() }

    private fun pe.edu.upc.sems.alerts.data.remote.dto.NotificationPreferenceDto.toPreference() =
        pe.edu.upc.sems.alerts.domain.model.NotificationPreference(channel.orEmpty().lowercase(), enabled, minSeverity)

    private companion object {
        /** What the web app sends when the user first turns a channel on or off. */
        const val DEFAULT_MIN_SEVERITY = "medium"
    }

    override suspend fun updateStatus(alertId: String, status: AlertStatus, resolvedAt: Instant?): ApiResult<Alert> =
        safeApiCall {
            service.updateStatus(alertId, UpdateAlertStatusRequestDto(status.wire, resolvedAt?.toString()))
        }.map { it.toDomain() }
}
