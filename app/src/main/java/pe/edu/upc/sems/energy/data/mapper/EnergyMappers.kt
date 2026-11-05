package pe.edu.upc.sems.energy.data.mapper

import java.time.Instant
import pe.edu.upc.sems.energy.data.remote.dto.ConsumptionDto
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.model.MeterStatus
import pe.edu.upc.sems.energy.data.remote.dto.EnergyMeterDto
import pe.edu.upc.sems.energy.data.remote.dto.MeterReadingDto
import pe.edu.upc.sems.energy.data.remote.dto.ZoneBreakdownDto
import pe.edu.upc.sems.energy.data.remote.dto.ZoneShareDto
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.energy.domain.model.ZoneConsumption

fun ConsumptionDto.toDomain() = DeviceConsumption(
    deviceId = deviceId,
    totalKwh = totalKwh,
    costSoles = costEstimateSoles,
    periodStart = periodStart.toInstantOrNull(),
    periodEnd = periodEnd.toInstantOrNull(),
)

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun EnergyMeterDto.toDomain() = EnergyMeter(
    id = id,
    serial = meterSerial,
    model = model,
    userId = userId,
    siteId = siteId,
    zoneId = zoneId,
    status = MeterStatus.from(status),
    lastSeenAt = lastSeenAt.toInstantOrNull(),
)

fun MeterReadingDto.toDomain() = EnergyReading(
    meterId = meterId,
    deviceId = deviceId,
    measuredAt = timestamp.toInstantOrNull(),
    energyKwh = energyKwh,
    powerKw = powerWatts?.div(1000.0),
    estimatedCostSoles = estimatedCost,
)

fun ZoneBreakdownDto.toDomain() = SiteZoneConsumption(
    siteId = siteId,
    siteName = siteName,
    days = days,
    from = from.toInstantOrNull(),
    to = to.toInstantOrNull(),
    totalKwh = totalKwh,
    zones = zones.map { it.toDomain() },
    unassigned = unassigned?.toDomain(fallbackName = "Sin zona"),
)

private fun ZoneShareDto.toDomain(fallbackName: String = "Zona") = ZoneConsumption(
    zoneId = zoneId,
    zoneName = zoneName?.takeIf { it.isNotBlank() } ?: fallbackName,
    meterCount = meterCount,
    kwh = kwh,
    sharePercent = sharePercent,
    estimatedEnergyCostSoles = estimatedEnergyCost,
)

fun pe.edu.upc.sems.energy.data.remote.dto.SiteShareDto.toDomain() = pe.edu.upc.sems.energy.domain.model.SiteConsumption(
    siteId = siteId,
    meterCount = meterCount,
    kwh = kwh,
    energyCostSoles = estimatedEnergyCost,
    peakDemandKw = peakDemandKw,
    contractedPowerKw = contractedPowerKw,
)
