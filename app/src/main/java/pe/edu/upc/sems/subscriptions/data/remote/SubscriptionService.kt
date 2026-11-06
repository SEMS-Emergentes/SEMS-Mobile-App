package pe.edu.upc.sems.subscriptions.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/** Subscriptions bounded context (base /api/v1). Only the two reads the app already needs. */
interface SubscriptionService {

    @GET("subscription-plans")
    suspend fun listPlans(): List<PlanDto>

    @GET("subscriptions/users/{userId}")
    suspend fun listSubscriptions(@Path("userId") userId: String): List<SubscriptionDto>
}

// OJO: unlike the rest of the backend, the responses of this module are PascalCase (@JsonProperty), the same
// quirk the web app documents in subscriptions.service.js.

@Serializable
data class PlanDto(
    @SerialName("PlanID") val planId: String,
    @SerialName("Name") val name: String? = null,
)

@Serializable
data class SubscriptionDto(
    @SerialName("SubscriptionID") val subscriptionId: String? = null,
    @SerialName("PlanID") val planId: String? = null,
    @SerialName("Status") val status: String? = null,
)
