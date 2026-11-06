package pe.edu.upc.sems.subscriptions.domain.repository

import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier

interface SubscriptionRepository {
    /** The level of the plan the user has. Without a subscription it is [PlanTier.STARTER], as in the web. */
    suspend fun currentTier(userId: String): ApiResult<PlanTier>
}
