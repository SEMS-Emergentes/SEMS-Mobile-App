package pe.edu.upc.sems.energy.data.remote

import pe.edu.upc.sems.energy.data.remote.dto.ConsumptionDto
import pe.edu.upc.sems.energy.data.remote.dto.CreateMeterRequestDto
import pe.edu.upc.sems.energy.data.remote.dto.EnergyMeterDto
import pe.edu.upc.sems.energy.data.remote.dto.MeterReadingDto
import pe.edu.upc.sems.energy.data.remote.dto.SiteComparisonDto
import pe.edu.upc.sems.energy.data.remote.dto.ZoneBreakdownDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

/** Energy bounded context (base /api/v1). Only the endpoints the app already uses. */
interface EnergyService {

    @GET("device-consumptions/user/{userId}")
    suspend fun listDeviceConsumptions(@Path("userId") userId: String): List<ConsumptionDto>

    @GET("energy-meters/user/{userId}")
    suspend fun listMeters(@Path("userId") userId: String): List<EnergyMeterDto>

    @GET("energy-meters/site/{siteId}")
    suspend fun listMetersBySite(@Path("siteId") siteId: String): List<EnergyMeterDto>

    @POST("energy-meters")
    suspend fun createMeter(@Body body: CreateMeterRequestDto): EnergyMeterDto

    @PATCH("energy-meters/{meterId}/zone")
    suspend fun assignZone(@Path("meterId") meterId: String, @Body body: JsonObject): EnergyMeterDto

    @DELETE("energy-meters/{meterId}")
    suspend fun deleteMeter(@Path("meterId") meterId: String)

    @GET("energy-readings/meter/{meterId}/latest")
    suspend fun latestMeterReading(@Path("meterId") meterId: String): MeterReadingDto?

    @GET("energy-readings/range")
    suspend fun readingsByRange(
        @Query("userId") userId: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): List<MeterReadingDto>

    @GET("energy/devices/{deviceId}/consumption/current")
    suspend fun currentDeviceReading(@Path("deviceId") deviceId: String): MeterReadingDto?

    @GET("energy/devices/{deviceId}/consumption/history")
    suspend fun deviceReadingHistory(@Path("deviceId") deviceId: String, @Query("limit") limit: Int): List<MeterReadingDto>

    @GET("sites/{siteId}/zone-consumption")
    suspend fun siteZoneConsumption(@Path("siteId") siteId: String, @Query("days") days: Int): ZoneBreakdownDto

    @GET("organizations/{organizationId}/site-consumption")
    suspend fun siteComparison(@Path("organizationId") organizationId: String, @Query("days") days: Int): SiteComparisonDto
}
