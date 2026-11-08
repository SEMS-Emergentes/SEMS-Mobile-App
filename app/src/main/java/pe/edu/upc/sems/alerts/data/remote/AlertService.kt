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

    @GET("users/{userId}/notification-preferences")
    suspend fun listNotificationPreferences(@Path("userId") userId: String): List<pe.edu.upc.sems.alerts.data.remote.dto.NotificationPreferenceDto>

    /** Creates the preference or updates the one the user already has for that channel (answers 201 either way). */
    @retrofit2.http.POST("notification-preferences")
    suspend fun saveNotificationPreference(
        @Body body: pe.edu.upc.sems.alerts.data.remote.dto.SaveNotificationPreferenceRequestDto,
    ): pe.edu.upc.sems.alerts.data.remote.dto.NotificationPreferenceDto

    @PATCH("alerts/{alertId}/status")
    suspend fun updateStatus(
        @Path("alertId") alertId: String,
        @Body body: UpdateAlertStatusRequestDto,
    ): AlertDto
}
