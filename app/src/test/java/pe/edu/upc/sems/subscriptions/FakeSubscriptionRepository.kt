package pe.edu.upc.sems.subscriptions

import java.time.LocalDate
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.subscriptions.domain.model.CardInput
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.model.PurchaseOutcome
import pe.edu.upc.sems.subscriptions.domain.model.SiteLimit
import pe.edu.upc.sems.subscriptions.domain.model.Subscription
import pe.edu.upc.sems.subscriptions.domain.model.SubscriptionStatus
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository

/** Configurable fake: every call is recorded. */
class FakeSubscriptionRepository(var result: ApiResult<PlanTier> = ApiResult.Success(PlanTier.BUSINESS)) : SubscriptionRepository {

    var plansResult: ApiResult<List<Plan>> = ApiResult.Success(listOf(STARTER, BUSINESS, ENTERPRISE))
    var subscriptionResult: ApiResult<Subscription?> = ApiResult.Success(SUBSCRIPTION)
    var purchaseResult: ApiResult<PurchaseOutcome> = ApiResult.Success(PurchaseOutcome.Purchased)

    /** (plan id, card) of every purchase; the card is null for a free plan. */
    val purchaseCalls = mutableListOf<Pair<String, CardInput?>>()

    override suspend fun currentTier(userId: String) = result
    override suspend fun plans() = plansResult
    override suspend fun subscription(userId: String) = subscriptionResult

    override suspend fun purchase(userId: String, planId: String, card: CardInput?): ApiResult<PurchaseOutcome> {
        purchaseCalls += planId to card
        return purchaseResult
    }

    companion object {
        val STARTER = Plan("plan-starter", "Starter", 0.0, false, SiteLimit.Limited(1), emptyList())
        val BUSINESS = Plan("plan-business", "Business", 149.0, false, SiteLimit.Limited(5), emptyList())
        val ENTERPRISE = Plan("plan-enterprise", "Enterprise", 399.0, false, SiteLimit.Unlimited, emptyList())

        val SUBSCRIPTION = Subscription("sub-1", BUSINESS.id, SubscriptionStatus.ACTIVE, LocalDate.of(2026, 11, 15))
    }
}
