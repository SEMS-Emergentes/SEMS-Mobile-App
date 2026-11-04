package pe.edu.upc.sems.energy.data.remote

import pe.edu.upc.sems.energy.data.remote.dto.ConsumptionDto
import retrofit2.http.GET
import retrofit2.http.Path

/** Energy bounded context (base /api/v1). Only the endpoints the app already uses. */
interface EnergyService {

    @GET("device-consumptions/user/{userId}")
    suspend fun listDeviceConsumptions(@Path("userId") userId: String): List<ConsumptionDto>
}
