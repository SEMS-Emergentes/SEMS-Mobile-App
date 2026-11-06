package pe.edu.upc.sems.analytics

import java.time.Instant
import pe.edu.upc.sems.analytics.domain.model.BillPrediction
import pe.edu.upc.sems.analytics.domain.model.ConsumptionRanking
import pe.edu.upc.sems.analytics.domain.model.RankingEntry
import pe.edu.upc.sems.analytics.domain.model.Recommendation
import pe.edu.upc.sems.analytics.domain.repository.AnalyticsRepository
import pe.edu.upc.sems.shared.network.ApiResult

/** Configurable fake: every call is recorded. A successful apply answers with the recommendation applied. */
class FakeAnalyticsRepository : AnalyticsRepository {

    var recommendationsResult: ApiResult<List<Recommendation>> = ApiResult.Success(emptyList())
    var predictionsResult: ApiResult<List<BillPrediction>> = ApiResult.Success(emptyList())
    var rankingResult: ApiResult<ConsumptionRanking?> = ApiResult.Success(null)

    val applyCalls = mutableListOf<String>()
    var applyFailure: ApiResult.Failure? = null

    override suspend fun recommendations(userId: String) = recommendationsResult

    override suspend fun applyRecommendation(recommendationId: String): ApiResult<Recommendation> {
        applyCalls += recommendationId
        applyFailure?.let { return it }
        val current = (recommendationsResult as? ApiResult.Success)?.data?.firstOrNull { it.id == recommendationId }
            ?: recommendation(recommendationId)
        return ApiResult.Success(current.copy(applied = true))
    }

    override suspend fun billPredictions(userId: String) = predictionsResult

    override suspend fun latestRanking(userId: String) = rankingResult

    companion object {
        fun recommendation(
            id: String,
            saving: Double = 10.0,
            applied: Boolean = false,
            generatedAt: Instant? = Instant.parse("2026-11-05T12:00:00Z"),
        ) = Recommendation(id, "Título $id", "Detalle $id", saving, applied, generatedAt)

        fun prediction(
            siteId: String?,
            year: Int,
            month: Int,
            amount: Double,
            kwh: Double = 100.0,
            createdAt: String = "2026-11-05T12:00:00Z",
        ) = BillPrediction(siteId, year, month, amount, kwh, Instant.parse(createdAt))

        fun rankingEntry(rank: Int, deviceId: String?, name: String? = null, amount: Double = 10.0, percent: Double = 50.0) =
            RankingEntry(rank, deviceId, name, kwh = 5.0, amountSoles = amount, percentOfTotal = percent)
    }
}

