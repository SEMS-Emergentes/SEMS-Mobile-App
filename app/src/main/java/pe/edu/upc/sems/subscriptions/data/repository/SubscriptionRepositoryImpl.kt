package pe.edu.upc.sems.subscriptions.data.repository

import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.safeApiCall
import pe.edu.upc.sems.subscriptions.data.remote.SubscriptionDto
import pe.edu.upc.sems.subscriptions.data.remote.SubscriptionService
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository

class SubscriptionRepositoryImpl @Inject constructor(
    private val service: SubscriptionService,
) : SubscriptionRepository {

    override suspend fun currentTier(userId: String): ApiResult<PlanTier> = coroutineScope {
        val subscriptions = async { safeApiCall { service.listSubscriptions(userId) } }
        val plans = async { safeApiCall { service.listPlans() } }
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
        val planName = catalog.firstOrNull { it.planId == current.planId }?.name
        ApiResult.Success(PlanTier.fromPlanName(planName))
    }

    /** The web prefers an active subscription; any status it does not know counts as active. */
    private fun SubscriptionDto.isActive(): Boolean = when (status.orEmpty().uppercase()) {
        "CANCELLED", "CANCELED", "TRIAL", "TRIALING", "PAST_DUE", "UNPAID" -> false
        else -> true
    }
}
