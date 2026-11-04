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

/** A physical energy meter. It is separate from a business device. */
data class EnergyMeter(
    val id: String,
    val serial: String,
    val model: String?,
    val userId: String?,
    val siteId: String?,
    val zoneId: String?,
    val status: MeterStatus,
    val lastSeenAt: Instant?,
)

enum class MeterStatus {
    ACTIVE, INACTIVE, MAINTENANCE, ERROR, UNKNOWN;

    companion object {
        fun from(raw: String?): MeterStatus = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/** A measured value reported by the backend. Power is converted from W to kW only for display. */
data class EnergyReading(
    val meterId: String?,
    val deviceId: String?,
    val measuredAt: Instant?,
    val energyKwh: Double?,
    val powerKw: Double?,
    val estimatedCostSoles: Double?,
)

data class MeterDraft(val serial: String, val model: String?)

/** Official site aggregation returned by the backend; it avoids adding overlapping meters in the app. */
data class SiteZoneConsumption(
    val siteId: String,
    val siteName: String?,
    val days: Int,
    val from: Instant?,
    val to: Instant?,
    val totalKwh: Double,
    val zones: List<ZoneConsumption>,
    val unassigned: ZoneConsumption?,
)

data class ZoneConsumption(
    val zoneId: String?,
    val zoneName: String,
    val meterCount: Int,
    val kwh: Double,
    val sharePercent: Double?,
    /** Calculated by the backend; clients must not derive cost from kWh. */
    val estimatedEnergyCostSoles: Double?,
)
