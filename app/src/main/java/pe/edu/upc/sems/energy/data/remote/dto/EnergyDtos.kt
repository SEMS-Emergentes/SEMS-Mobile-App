package pe.edu.upc.sems.energy.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Item of GET /device-consumptions/user/{userId} (backend DeviceConsumptionController, camelCase). */
@Serializable
data class ConsumptionDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("total_kwh") val totalKwh: Double,
    @SerialName("cost_estimate_soles") val costEstimateSoles: Double,
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
)

/** Exact property names from the deployed OpenAPI MeterResponse schema. */
@Serializable
data class EnergyMeterDto(
    val id: String,
    @SerialName("meter_serial") val meterSerial: String,
    val model: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("site_id") val siteId: String? = null,
    @SerialName("zone_id") val zoneId: String? = null,
    val status: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
data class CreateMeterRequestDto(
    @SerialName("user_id") val userId: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("meter_serial") val meterSerial: String,
    @SerialName("zone_id") val zoneId: String? = null,
    val model: String? = null,
)

@Serializable
data class MeterReadingDto(
    @SerialName("meter_id") val meterId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    val timestamp: String? = null,
    @SerialName("energy_kwh") val energyKwh: Double? = null,
    @SerialName("power_watts") val powerWatts: Double? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
)

@Serializable
data class ZoneBreakdownDto(
    @SerialName("site_id") val siteId: String,
    @SerialName("site_name") val siteName: String? = null,
    val days: Int,
    val from: String? = null,
    val to: String? = null,
    @SerialName("total_kwh") val totalKwh: Double,
    val zones: List<ZoneShareDto> = emptyList(),
    val unassigned: ZoneShareDto? = null,
)

@Serializable
data class ZoneShareDto(
    @SerialName("zone_id") val zoneId: String? = null,
    @SerialName("zone_name") val zoneName: String? = null,
    @SerialName("meter_count") val meterCount: Int = 0,
    val kwh: Double,
    @SerialName("share_percent") val sharePercent: Double? = null,
    @SerialName("estimated_energy_cost") val estimatedEnergyCost: Double? = null,
)

/** `GET /organizations/{id}/site-consumption`: one entry per active site (OpenAPI SiteComparisonResponse). */
@Serializable
data class SiteComparisonDto(
    val sites: List<SiteShareDto> = emptyList(),
)

@Serializable
data class SiteShareDto(
    @SerialName("site_id") val siteId: String,
    @SerialName("meter_count") val meterCount: Int = 0,
    val kwh: Double,
    @SerialName("estimated_energy_cost") val estimatedEnergyCost: Double? = null,
    @SerialName("peak_demand_kw") val peakDemandKw: Double,
    @SerialName("contracted_power_kw") val contractedPowerKw: Double? = null,
)
