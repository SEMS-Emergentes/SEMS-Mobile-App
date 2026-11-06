package pe.edu.upc.sems.analytics.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/** Analytics bounded context (backend AnalyticsController, base /api/v1/analytics). snake_case JSON. */
interface AnalyticsService {

    @GET("analytics/recommendations/user/{userId}")
    suspend fun recommendations(@Path("userId") userId: String): List<RecommendationDto>

    @PATCH("analytics/recommendations/{recommendationId}/apply")
    suspend fun applyRecommendation(@Path("recommendationId") recommendationId: String): RecommendationDto

    @GET("analytics/bill-predictions/user/{userId}")
    suspend fun billPredictions(@Path("userId") userId: String): List<BillPredictionDto>

    @GET("analytics/consumption-rankings/user/{userId}")
    suspend fun consumptionRankings(@Path("userId") userId: String): List<ConsumptionRankingDto>
}

@Serializable
data class RecommendationDto(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    @SerialName("estimated_saving_amount") val estimatedSavingAmount: Double? = null,
    /** `pending` or `applied`. */
    val status: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
)

@Serializable
data class BillPredictionDto(
    @SerialName("site_id") val siteId: String? = null,
    @SerialName("prediction_year") val predictionYear: Int = 0,
    @SerialName("prediction_month") val predictionMonth: Int = 0,
    @SerialName("estimated_kwh") val estimatedKwh: Double? = null,
    @SerialName("estimated_amount") val estimatedAmount: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ConsumptionRankingDto(
    val rankings: List<RankingItemDto> = emptyList(),
    @SerialName("generated_at") val generatedAt: String? = null,
)

@Serializable
data class RankingItemDto(
    val rank: Int = 0,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("total_kwh") val totalKwh: Double? = null,
    @SerialName("estimated_amount") val estimatedAmount: Double? = null,
    @SerialName("percentage_of_total") val percentageOfTotal: Double? = null,
)
