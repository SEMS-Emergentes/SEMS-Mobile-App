package pe.edu.upc.sems.energy.domain.repository

import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.MeterDraft
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.model.SiteConsumption
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.shared.network.ApiResult
import java.time.Instant

interface EnergyRepository {
    suspend fun listDeviceConsumptions(userId: String): ApiResult<List<DeviceConsumption>>
    suspend fun listMeters(userId: String): ApiResult<List<EnergyMeter>>
    suspend fun listMetersBySite(siteId: String): ApiResult<List<EnergyMeter>>
    suspend fun createMeter(userId: String, siteId: String, zoneId: String?, draft: MeterDraft): ApiResult<EnergyMeter>
    suspend fun assignMeterZone(meterId: String, zoneId: String?): ApiResult<EnergyMeter>
    suspend fun deleteMeter(meterId: String): ApiResult<Unit>
    suspend fun latestMeterReading(meterId: String): ApiResult<EnergyReading?>
    suspend fun meterReadingHistory(userId: String, meterId: String, from: Instant, to: Instant): ApiResult<List<EnergyReading>>
    /** Readings of one device in a period (the backend filters by user and range; the device is picked here). */
    suspend fun deviceReadingsInRange(userId: String, deviceId: String, from: Instant, to: Instant): ApiResult<List<EnergyReading>>
    suspend fun currentDeviceReading(deviceId: String): ApiResult<EnergyReading?>
    suspend fun deviceReadingHistory(deviceId: String, limit: Int = 50): ApiResult<List<EnergyReading>>
    suspend fun siteZoneConsumption(siteId: String, days: Int): ApiResult<SiteZoneConsumption>

    /** Every active site of the organization over the last [days] days, as calculated by the backend. */
    suspend fun siteComparison(organizationId: String, days: Int): ApiResult<List<SiteConsumption>>

    /** All the user's readings in a period (every site and meter). The caller keeps the ones it needs. */
    suspend fun userReadingsInRange(userId: String, from: Instant, to: Instant): ApiResult<List<EnergyReading>>
}
