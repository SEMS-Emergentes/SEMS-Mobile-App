package pe.edu.upc.sems.subscriptions.data.repository

import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.map
import pe.edu.upc.sems.shared.network.safeApiCall
import pe.edu.upc.sems.subscriptions.data.mapper.toDomain
import pe.edu.upc.sems.subscriptions.data.remote.ChangePlanRequestDto
import pe.edu.upc.sems.subscriptions.data.remote.CheckoutRequestDto
import pe.edu.upc.sems.subscriptions.data.remote.CreateSubscriptionRequestDto
import pe.edu.upc.sems.subscriptions.data.remote.SubscriptionDto
import pe.edu.upc.sems.subscriptions.data.remote.SubscriptionService
import pe.edu.upc.sems.subscriptions.domain.model.Payment
import pe.edu.upc.sems.subscriptions.domain.model.PaymentCard
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.model.Subscription
import pe.edu.upc.sems.subscriptions.domain.model.SubscriptionStatus
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository

class SubscriptionRepositoryImpl @Inject constructor(
    private val service: SubscriptionService,
) : SubscriptionRepository {

    override suspend fun currentTier(userId: String): ApiResult<PlanTier> = coroutineScope {
        val subscriptions = async { subscriptionDtos(userId) }
        val plans = async { plans() }
        val subs = when (val result = subscriptions.await()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return@coroutineScope result
        }
        if (subs.isEmpty()) return@coroutineScope ApiResult.Success(PlanTier.STARTER)

        // The plan's name tells the level. If the catalog cannot be read we must not guess "Starter" and lock
        // a customer who paid, so the failure is reported instead.
        val catalog = when (val result = plans.await()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return@coroutineScope result
        }
        val current = subs.firstOrNull { it.isActive() } ?: subs.first()
        ApiResult.Success(catalog.firstOrNull { it.id == current.planId }?.tier ?: PlanTier.STARTER)
    }

    override suspend fun plans(): ApiResult<List<Plan>> =
        safeApiCall { service.listPlans() }.map { list -> list.map { it.toDomain() } }

    override suspend fun subscription(userId: String): ApiResult<Subscription?> =
        subscriptionDtos(userId).map { subs -> (subs.firstOrNull { it.isActive() } ?: subs.firstOrNull())?.toDomain() }

    override suspend fun paymentCards(userId: String): ApiResult<List<PaymentCard>> =
        safeApiCall { service.listPaymentMethods(userId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun payments(userId: String): ApiResult<List<Payment>> =
        safeApiCall { service.listPayments(userId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun changePlan(userId: String, subscriptionId: String?, planId: String): ApiResult<Subscription> =
        safeApiCall {
            if (subscriptionId != null) {
                service.changePlan(subscriptionId, ChangePlanRequestDto(planId))
            } else {
                service.createSubscription(CreateSubscriptionRequestDto(planId, userId))
            }
        }.map { it.toDomain() }

    override suspend fun checkoutUrl(
        userId: String,
        subscriptionId: String?,
        plan: Plan,
        successUrl: String,
        cancelUrl: String,
    ): ApiResult<String> {
        val result = safeApiCall {
            service.createCheckoutSession(
                CheckoutRequestDto(
                    userId = userId,
                    subscriptionId = subscriptionId,
                    planName = "SEMS ${plan.name}",
                    amount = plan.priceSoles,
                    currency = "pen",
                    successUrl = successUrl,
                    cancelUrl = cancelUrl,
                ),
            )
        }
        return when (result) {
            is ApiResult.Success -> result.data.url?.takeIf { it.isNotBlank() }
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(ApiError.Unknown(IllegalStateException("Checkout without url")))
            is ApiResult.Failure -> result
        }
    }

    private suspend fun subscriptionDtos(userId: String): ApiResult<List<SubscriptionDto>> =
        safeApiCall { service.listSubscriptions(userId) }

    /** The web prefers an active subscription; any status it does not know counts as active. */
    private fun SubscriptionDto.isActive(): Boolean = SubscriptionStatus.from(status) == SubscriptionStatus.ACTIVE
}
