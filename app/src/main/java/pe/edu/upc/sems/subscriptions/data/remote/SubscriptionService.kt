package pe.edu.upc.sems.subscriptions.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Subscriptions and payments bounded contexts (base /api/v1). */
interface SubscriptionService {

    @GET("subscription-plans")
    suspend fun listPlans(): List<PlanDto>

    @GET("subscriptions/users/{userId}")
    suspend fun listSubscriptions(@Path("userId") userId: String): List<SubscriptionDto>

    @POST("subscriptions")
    suspend fun createSubscription(@Body body: CreateSubscriptionRequestDto): SubscriptionDto

    @PATCH("subscriptions/{subscriptionId}/change-plan")
    suspend fun changePlan(@Path("subscriptionId") subscriptionId: String, @Body body: ChangePlanRequestDto): SubscriptionDto

    @GET("payment-methods/user/{userId}")
    suspend fun listPaymentMethods(@Path("userId") userId: String): List<PaymentMethodDto>

    @GET("payments/user/{userId}")
    suspend fun listPayments(@Path("userId") userId: String): List<PaymentDto>

    @POST("payments/checkout-session")
    suspend fun createCheckoutSession(@Body body: CheckoutRequestDto): CheckoutSessionDto
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

@Serializable
data class CreateSubscriptionRequestDto(
    @SerialName("plan_id") val planId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
data class ChangePlanRequestDto(
    @SerialName("new_plan_id") val newPlanId: String,
)

@Serializable
data class PaymentMethodDto(
    val brand: String? = null,
    val last4: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable
data class PaymentDto(
    /** `pending`, `processing`, `processed`, `failed` or `cancelled`. */
    val status: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CheckoutRequestDto(
    @SerialName("user_id") val userId: String,
    @SerialName("subscription_id") val subscriptionId: String?,
    @SerialName("plan_name") val planName: String,
    val amount: Double,
    val currency: String,
    @SerialName("success_url") val successUrl: String,
    @SerialName("cancel_url") val cancelUrl: String,
)

@Serializable
data class CheckoutSessionDto(
    val url: String? = null,
)
