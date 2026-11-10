package pe.edu.upc.sems.subscriptions.domain.repository

import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.subscriptions.domain.model.CardInput
import pe.edu.upc.sems.subscriptions.domain.model.Plan
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.model.PurchaseOutcome
import pe.edu.upc.sems.subscriptions.domain.model.Subscription

interface SubscriptionRepository {
    /** The level of the plan the user has. Without a subscription it is [PlanTier.STARTER], as in the web. */
    suspend fun currentTier(userId: String): ApiResult<PlanTier>

    /** The active plans of the catalog. */
    suspend fun plans(): ApiResult<List<Plan>>

    /** The user's subscription (an active one if there is, else the first), or null when there is none. */
    suspend fun subscription(userId: String): ApiResult<Subscription?>

    /**
     * Contrata [planId]. Un plan de pago necesita [card]; el backend la cobra con su procesador simulado y solo
     * cambia el plan si el cobro se aprueba. Un rechazo no es un fallo de red: llega como [PurchaseOutcome.Declined].
     */
    suspend fun purchase(userId: String, planId: String, card: CardInput?): ApiResult<PurchaseOutcome>
}
