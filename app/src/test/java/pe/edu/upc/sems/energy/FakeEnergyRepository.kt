package pe.edu.upc.sems.energy

import java.time.Instant
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.model.MeterDraft
import pe.edu.upc.sems.energy.domain.model.SiteConsumption
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.shared.network.ApiResult

/** Fake for the device readings of a period; every other call fails, so a test cannot use one by mistake. */
class FakeEnergyRepository : EnergyRepository {

    /** (user id, device id, from, to) of every request. */
    val rangeCalls = mutableListOf<List<Any>>()
    var rangeResult: ApiResult<List<EnergyReading>> = ApiResult.Success(emptyList())

    override suspend fun deviceReadingsInRange(
        userId: String,
        deviceId: String,
        from: Instant,
        to: Instant,
    ): ApiResult<List<EnergyReading>> {
        rangeCalls += listOf(userId, deviceId, from, to)
        return rangeResult
    }

    override suspend fun listDeviceConsumptions(userId: String): ApiResult<List<DeviceConsumption>> = error("unused")
    override suspend fun listMeters(userId: String): ApiResult<List<EnergyMeter>> = error("unused")
    var metersBySiteResult: ApiResult<List<EnergyMeter>> = ApiResult.Success(emptyList())

    override suspend fun listMetersBySite(siteId: String): ApiResult<List<EnergyMeter>> = metersBySiteResult
    override suspend fun createMeter(userId: String, siteId: String, zoneId: String?, draft: MeterDraft): ApiResult<EnergyMeter> = error("unused")
    override suspend fun assignMeterZone(meterId: String, zoneId: String?): ApiResult<EnergyMeter> = error("unused")
    override suspend fun deactivateMeter(meterId: String): ApiResult<Unit> = error("unused")
    override suspend fun deleteMeter(meterId: String): ApiResult<Unit> = error("unused")
    override suspend fun latestMeterReading(meterId: String): ApiResult<EnergyReading?> = error("unused")
    override suspend fun meterReadingHistory(userId: String, meterId: String, from: Instant, to: Instant): ApiResult<List<EnergyReading>> = error("unused")
    override suspend fun currentDeviceReading(deviceId: String): ApiResult<EnergyReading?> = error("unused")
    override suspend fun deviceReadingHistory(deviceId: String, limit: Int): ApiResult<List<EnergyReading>> = error("unused")
    override suspend fun siteZoneConsumption(siteId: String, days: Int): ApiResult<SiteZoneConsumption> = error("unused")

    /** (organization id, days) of every request. */
    val comparisonCalls = mutableListOf<Pair<String, Int>>()
    var comparisonResults: Map<Int, ApiResult<List<SiteConsumption>>> = emptyMap()

    override suspend fun siteComparison(organizationId: String, days: Int): ApiResult<List<SiteConsumption>> {
        comparisonCalls += organizationId to days
        return comparisonResults[days] ?: ApiResult.Success(emptyList())
    }

    /** (user id, from, to) of every request. */
    val userRangeCalls = mutableListOf<List<Any>>()
    var userRangeResult: ApiResult<List<EnergyReading>> = ApiResult.Success(emptyList())

    override suspend fun userReadingsInRange(userId: String, from: Instant, to: Instant): ApiResult<List<EnergyReading>> {
        userRangeCalls += listOf(userId, from, to)
        return userRangeResult
    }

    companion object {
        fun reading(
            at: String,
            kw: Double? = null,
            kwh: Double? = null,
            cost: Double? = null,
            deviceId: String? = "dev-1",
        ) = EnergyReading(
            meterId = "meter-1",
            deviceId = deviceId,
            measuredAt = Instant.parse(at),
            energyKwh = kwh,
            powerKw = kw,
            estimatedCostSoles = cost,
        )
    }
}
