package pe.edu.upc.sems.energy.domain.repository

import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.MeterDraft
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.shared.network.ApiResult
import java.time.Instant

interface EnergyRepository {
    suspend fun listDeviceConsumptions(userId: String): ApiResult<List<DeviceConsumption>>
    suspend fun listMeters(userId: String): ApiResult<List<EnergyMeter>>
    suspend fun listMetersBySite(siteId: String): ApiResult<List<EnergyMeter>>
    suspend fun createMeter(userId: String, siteId: String, zoneId: String?, draft: MeterDraft): ApiResult<EnergyMeter>
    suspend fun assignMeterZone(meterId: String, zoneId: String?): ApiResult<EnergyMeter>
    suspend fun deactivateMeter(meterId: String): ApiResult<Unit>
    suspend fun deleteMeter(meterId: String): ApiResult<Unit>
    suspend fun latestMeterReading(meterId: String): ApiResult<EnergyReading?>
    suspend fun meterReadingHistory(userId: String, meterId: String, from: Instant, to: Instant): ApiResult<List<EnergyReading>>
    suspend fun currentDeviceReading(deviceId: String): ApiResult<EnergyReading?>
    suspend fun deviceReadingHistory(deviceId: String, limit: Int = 50): ApiResult<List<EnergyReading>>
    suspend fun siteZoneConsumption(siteId: String, days: Int): ApiResult<SiteZoneConsumption>
}
