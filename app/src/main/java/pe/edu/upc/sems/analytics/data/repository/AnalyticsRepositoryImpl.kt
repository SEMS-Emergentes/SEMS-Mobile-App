package pe.edu.upc.sems.analytics.data.repository

import java.time.Instant
import javax.inject.Inject
import pe.edu.upc.sems.analytics.data.mapper.toDomain
import pe.edu.upc.sems.analytics.data.remote.AnalyticsService
import pe.edu.upc.sems.analytics.domain.model.BillPrediction
import pe.edu.upc.sems.analytics.domain.model.ConsumptionRanking
import pe.edu.upc.sems.analytics.domain.model.Recommendation
import pe.edu.upc.sems.analytics.domain.repository.AnalyticsRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.map
import pe.edu.upc.sems.shared.network.safeApiCall

class AnalyticsRepositoryImpl @Inject constructor(
    private val service: AnalyticsService,
) : AnalyticsRepository {

    override suspend fun recommendations(userId: String): ApiResult<List<Recommendation>> =
        safeApiCall { service.recommendations(userId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun applyRecommendation(recommendationId: String): ApiResult<Recommendation> =
        safeApiCall { service.applyRecommendation(recommendationId) }.map { it.toDomain() }

    override suspend fun billPredictions(userId: String): ApiResult<List<BillPrediction>> =
        safeApiCall { service.billPredictions(userId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun latestRanking(userId: String): ApiResult<ConsumptionRanking?> =
        safeApiCall { service.consumptionRankings(userId) }.map { list ->
            list.map { it.toDomain() }.maxByOrNull { it.generatedAt ?: Instant.MIN }
        }
}
