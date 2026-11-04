package pe.edu.upc.sems.energy.domain.model

import java.time.Instant

/** Consumption of one device over a period. The cost in soles is calculated by the backend. */
data class DeviceConsumption(
    val deviceId: String,
    val totalKwh: Double,
    val costSoles: Double,
    val periodStart: Instant?,
    val periodEnd: Instant?,
)
