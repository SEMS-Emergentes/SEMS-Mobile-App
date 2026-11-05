package pe.edu.upc.sems.alerts.data.remote

import pe.edu.upc.sems.alerts.data.remote.dto.AlertDto
import pe.edu.upc.sems.alerts.data.remote.dto.UpdateAlertStatusRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/** Alerts bounded context (backend AlertController, base /api/v1). */
interface AlertService {

    @GET("users/{userId}/alerts")
    suspend fun listAlerts(@Path("userId") userId: String): List<AlertDto>

    @PATCH("alerts/{alertId}/status")
    suspend fun updateStatus(
        @Path("alertId") alertId: String,
        @Body body: UpdateAlertStatusRequestDto,
    ): AlertDto
}
