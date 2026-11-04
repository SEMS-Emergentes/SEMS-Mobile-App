package pe.edu.upc.sems.energy.data.mapper

import java.time.Instant
import pe.edu.upc.sems.energy.data.remote.dto.ConsumptionDto
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption

fun ConsumptionDto.toDomain() = DeviceConsumption(
    deviceId = deviceId,
    totalKwh = totalKwh,
    costSoles = costEstimateSoles,
    periodStart = periodStart.toInstantOrNull(),
    periodEnd = periodEnd.toInstantOrNull(),
)

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
