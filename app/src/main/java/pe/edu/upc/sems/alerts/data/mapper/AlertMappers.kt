package pe.edu.upc.sems.alerts.data.mapper

import java.time.Instant
import pe.edu.upc.sems.alerts.data.remote.dto.AlertDto
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertSeverity
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.domain.model.AlertType

fun AlertDto.toDomain() = Alert(
    id = alertId,
    deviceId = deviceId?.takeIf { it.isNotBlank() },
    type = AlertType.from(alertType),
    title = title.orEmpty(),
    message = message.orEmpty(),
    severity = AlertSeverity.from(severity),
    status = AlertStatus.from(status),
    triggeredAt = triggeredAt.toInstantOrNull(),
    resolvedAt = resolvedAt.toInstantOrNull(),
)

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
