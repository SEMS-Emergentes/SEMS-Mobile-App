package pe.edu.upc.sems.subscriptions.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Subscriptions and payments bounded contexts (base /api/v1). */
interface SubscriptionService {

    @GET("subscription-plans")
    suspend fun listPlans(): List<PlanDto>

    @GET("subscriptions/users/{userId}")
    suspend fun listSubscriptions(@Path("userId") userId: String): List<SubscriptionDto>

    // Unica via para contratar un plan: el backend cobra la tarjeta (simulada) y decide el importe.
    @POST("subscriptions/purchase")
    suspend fun purchase(@Body body: PurchaseRequestDto): PurchaseResponseDto
}

// OJO: unlike the rest of the backend, the responses of the subscriptions module are PascalCase (@JsonProperty),
// the same quirk the web app documents in subscriptions.service.js. Its requests and the payments module are snake_case.

@Serializable
data class PlanDto(
    @SerialName("PlanID") val planId: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Price") val price: Double? = null,
    @SerialName("BillingPeriod") val billingPeriod: String? = null,
    @SerialName("PlanFeatures") val features: List<PlanFeatureDto> = emptyList(),
)

@Serializable
data class PlanFeatureDto(
    @SerialName("FeatureCode") val code: String? = null,
    @SerialName("FeatureName") val name: String? = null,
    @SerialName("FeatureValue") val value: String? = null,
)

@Serializable
data class SubscriptionDto(
    @SerialName("SubscriptionID") val subscriptionId: String? = null,
    @SerialName("PlanID") val planId: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
)

// La tarjeta va solo cuando el plan cuesta; el importe no viaja, lo fija el plan guardado.
@Serializable
data class PurchaseRequestDto(
    @SerialName("user_id") val userId: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("card_number") val cardNumber: String? = null,
    @SerialName("exp_month") val expMonth: Int? = null,
    @SerialName("exp_year") val expYear: Int? = null,
    val cvc: String? = null,
) {
    // Un data class imprime todos sus campos: sin esto, un log de la peticion escribiria el numero y el CVC.
    override fun toString() = "PurchaseRequestDto(userId=$userId, planId=$planId, card=****)"
}

@Serializable
data class PurchaseResponseDto(
    @SerialName("Subscription") val subscription: SubscriptionDto? = null,
)
