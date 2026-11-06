package pe.edu.upc.sems.analytics.domain.repository

import pe.edu.upc.sems.analytics.domain.model.BillPrediction
import pe.edu.upc.sems.analytics.domain.model.ConsumptionRanking
import pe.edu.upc.sems.analytics.domain.model.Recommendation
import pe.edu.upc.sems.shared.network.ApiResult

interface AnalyticsRepository {
    suspend fun recommendations(userId: String): ApiResult<List<Recommendation>>

    /** Marks the recommendation as applied in the backend (it is idempotent; there is no way back). */
    suspend fun applyRecommendation(recommendationId: String): ApiResult<Recommendation>

    suspend fun billPredictions(userId: String): ApiResult<List<BillPrediction>>

    /** The most recent saved ranking, or null when the backend has none. */
    suspend fun latestRanking(userId: String): ApiResult<ConsumptionRanking?>
}
