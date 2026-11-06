package pe.edu.upc.sems.subscriptions.domain.repository

import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.subscriptions.domain.model.PaymentCard
import pe.edu.upc.sems.subscriptions.domain.model.Payment
import pe.edu.upc.sems.subscriptions.domain.model.PendingPlanChange
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.model.Subscription

interface SubscriptionRepository {
    /** The level of the plan the user has. Without a subscription it is [PlanTier.STARTER], as in the web. */
    suspend fun currentTier(userId: String): ApiResult<PlanTier>

    /** The active plans of the catalog. */
    suspend fun plans(): ApiResult<List<Plan>>

    /** The user's subscription (an active one if there is, else the first), or null when there is none. */
    suspend fun subscription(userId: String): ApiResult<Subscription?>

    suspend fun paymentCards(userId: String): ApiResult<List<PaymentCard>>

    suspend fun payments(userId: String): ApiResult<List<Payment>>

    /** Moves the subscription to [planId], or creates it when the user has none ([subscriptionId] null). */
    suspend fun changePlan(userId: String, subscriptionId: String?, planId: String): ApiResult<Subscription>

    /** Opens a Stripe Checkout session and returns the URL to pay at. The card is typed on Stripe's page only. */
    suspend fun checkoutUrl(userId: String, subscriptionId: String?, plan: Plan, successUrl: String, cancelUrl: String): ApiResult<String>
}

/** Remembers which paid plan the user went to pay for, to apply it when they come back. */
interface PendingPlanStore {
    fun get(): PendingPlanChange?
    fun set(change: PendingPlanChange)
    fun clear()
}
