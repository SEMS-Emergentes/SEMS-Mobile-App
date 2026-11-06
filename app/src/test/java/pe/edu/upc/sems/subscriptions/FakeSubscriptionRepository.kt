package pe.edu.upc.sems.subscriptions

import java.time.Instant
import java.time.LocalDate
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.subscriptions.domain.model.Payment
import pe.edu.upc.sems.subscriptions.domain.model.PaymentCard
import pe.edu.upc.sems.subscriptions.domain.model.PendingPlanChange
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.model.SiteLimit
import pe.edu.upc.sems.subscriptions.domain.model.Subscription
import pe.edu.upc.sems.subscriptions.domain.model.SubscriptionStatus
import pe.edu.upc.sems.subscriptions.domain.repository.PendingPlanStore
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository

/** Configurable fake: every call is recorded. */
class FakeSubscriptionRepository(var result: ApiResult<PlanTier> = ApiResult.Success(PlanTier.BUSINESS)) : SubscriptionRepository {

    var plansResult: ApiResult<List<Plan>> = ApiResult.Success(listOf(STARTER, BUSINESS, ENTERPRISE))
    var subscriptionResult: ApiResult<Subscription?> = ApiResult.Success(SUBSCRIPTION)
    var cardsResult: ApiResult<List<PaymentCard>> = ApiResult.Success(emptyList())
    var paymentsResult: ApiResult<List<Payment>> = ApiResult.Success(emptyList())
    var checkoutResult: ApiResult<String> = ApiResult.Success("https://checkout.stripe.test/session")
    var changeFailure: ApiResult.Failure? = null

    /** (subscription id, plan id) of every change; the id is null when the subscription was created. */
    val changeCalls = mutableListOf<Pair<String?, String>>()
    val checkoutCalls = mutableListOf<Plan>()

    override suspend fun currentTier(userId: String) = result
    override suspend fun plans() = plansResult
    override suspend fun subscription(userId: String) = subscriptionResult
    override suspend fun paymentCards(userId: String) = cardsResult
    override suspend fun payments(userId: String) = paymentsResult

    override suspend fun changePlan(userId: String, subscriptionId: String?, planId: String): ApiResult<Subscription> {
        changeCalls += subscriptionId to planId
        changeFailure?.let { return it }
        return ApiResult.Success(SUBSCRIPTION.copy(planId = planId))
    }

    override suspend fun checkoutUrl(userId: String, subscriptionId: String?, plan: Plan, successUrl: String, cancelUrl: String): ApiResult<String> {
        checkoutCalls += plan
        return checkoutResult
    }

    companion object {
        val STARTER = Plan("plan-starter", "Starter", 0.0, false, SiteLimit.Limited(1), emptyList())
        val BUSINESS = Plan("plan-business", "Business", 149.0, false, SiteLimit.Limited(5), emptyList())
        val ENTERPRISE = Plan("plan-enterprise", "Enterprise", 399.0, false, SiteLimit.Unlimited, emptyList())

        val SUBSCRIPTION = Subscription("sub-1", BUSINESS.id, SubscriptionStatus.ACTIVE, LocalDate.of(2026, 11, 15))

        fun payment(processed: Boolean, at: String) = Payment(Instant.parse(at), Instant.parse(at), processed)
    }
}

class FakePendingPlanStore(var pending: PendingPlanChange? = null) : PendingPlanStore {
    override fun get() = pending
    override fun set(change: PendingPlanChange) {
        pending = change
    }

    override fun clear() {
        pending = null
    }
}
