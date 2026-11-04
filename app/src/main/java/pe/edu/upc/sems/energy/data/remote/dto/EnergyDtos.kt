package pe.edu.upc.sems.energy.data.remote.dto

import kotlinx.serialization.Serializable

/** Item of GET /device-consumptions/user/{userId} (backend DeviceConsumptionController, camelCase). */
@Serializable
data class ConsumptionDto(
    val deviceId: String = "",
    val totalKwh: Double = 0.0,
    val costEstimateSoles: Double = 0.0,
    val periodStart: String? = null,
    val periodEnd: String? = null,
)
