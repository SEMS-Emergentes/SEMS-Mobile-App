package pe.edu.upc.sems.energy.data.repository

import javax.inject.Inject
import pe.edu.upc.sems.energy.data.mapper.toDomain
import pe.edu.upc.sems.energy.data.remote.dto.CreateMeterRequestDto
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.MeterDraft
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.data.remote.EnergyService
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.map
import pe.edu.upc.sems.shared.network.safeApiCall

class EnergyRepositoryImpl @Inject constructor(
    private val service: EnergyService,
) : EnergyRepository {

    override suspend fun listDeviceConsumptions(userId: String): ApiResult<List<DeviceConsumption>> =
        safeApiCall { service.listDeviceConsumptions(userId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun listMeters(userId: String): ApiResult<List<EnergyMeter>> =
        safeApiCall { service.listMeters(userId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun listMetersBySite(siteId: String): ApiResult<List<EnergyMeter>> =
        safeApiCall { service.listMetersBySite(siteId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun createMeter(userId: String, siteId: String, zoneId: String?, draft: MeterDraft): ApiResult<EnergyMeter> =
        safeApiCall { service.createMeter(CreateMeterRequestDto(userId, siteId, draft.serial, zoneId, draft.model)) }.map { it.toDomain() }

    override suspend fun assignMeterZone(meterId: String, zoneId: String?): ApiResult<EnergyMeter> =
        safeApiCall { service.assignZone(meterId, meterZoneBody(zoneId)) }.map { it.toDomain() }

    override suspend fun deactivateMeter(meterId: String): ApiResult<Unit> = safeApiCall { service.deactivateMeter(meterId) }
    override suspend fun deleteMeter(meterId: String): ApiResult<Unit> = safeApiCall { service.deleteMeter(meterId) }
    override suspend fun latestMeterReading(meterId: String): ApiResult<EnergyReading?> =
        safeApiCall { service.latestMeterReading(meterId) }.map { it?.toDomain() }.noReadingsIfNotFound()

    override suspend fun meterReadingHistory(
        userId: String,
        meterId: String,
        from: java.time.Instant,
        to: java.time.Instant,
    ): ApiResult<List<EnergyReading>> = safeApiCall {
        service.readingsByRange(userId, from.toString(), to.toString())
    }.map { readings -> readings.filter { it.meterId == meterId }.map { it.toDomain() } }

    override suspend fun deviceReadingsInRange(
        userId: String,
        deviceId: String,
        from: java.time.Instant,
        to: java.time.Instant,
    ): ApiResult<List<EnergyReading>> = safeApiCall {
        service.readingsByRange(userId, from.toString(), to.toString())
    }.map { readings -> readings.filter { it.deviceId == deviceId }.map { it.toDomain() } }

    override suspend fun currentDeviceReading(deviceId: String): ApiResult<EnergyReading?> =
        safeApiCall { service.currentDeviceReading(deviceId) }.map { it?.toDomain() }.noReadingsIfNotFound()

    override suspend fun deviceReadingHistory(deviceId: String, limit: Int): ApiResult<List<EnergyReading>> =
        safeApiCall { service.deviceReadingHistory(deviceId, limit) }.map { list -> list.map { it.toDomain() } }

    override suspend fun siteZoneConsumption(siteId: String, days: Int): ApiResult<SiteZoneConsumption> =
        safeApiCall { service.siteZoneConsumption(siteId, days) }.map { it.toDomain() }

    override suspend fun siteComparison(organizationId: String, days: Int): ApiResult<List<pe.edu.upc.sems.energy.domain.model.SiteConsumption>> =
        safeApiCall { service.siteComparison(organizationId, days) }.map { response -> response.sites.map { it.toDomain() } }

    override suspend fun userReadingsInRange(
        userId: String,
        from: java.time.Instant,
        to: java.time.Instant,
    ): ApiResult<List<EnergyReading>> = safeApiCall {
        service.readingsByRange(userId, from.toString(), to.toString())
    }.map { readings -> readings.map { it.toDomain() } }
}

/** An explicit JSON null is required to remove a zone, even with Json.explicitNulls=false. */
internal fun meterZoneBody(zoneId: String?): JsonObject = buildJsonObject {
    put("zone_id", zoneId?.let(::JsonPrimitive) ?: JsonNull)
}

/**
 * The backend answers 404 ("No readings found...") when a meter or a device has not reported yet. That is
 * not a failure: it just has nothing to show, so the screens say "sin lecturas" instead of an error.
 */
internal fun ApiResult<EnergyReading?>.noReadingsIfNotFound(): ApiResult<EnergyReading?> =
    if (this is ApiResult.Failure && error is ApiError.Http && error.code == 404) ApiResult.Success(null) else this
