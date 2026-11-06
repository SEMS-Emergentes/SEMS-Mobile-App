package pe.edu.upc.sems.analytics.domain.model

import java.time.Instant

/** A saving the backend proposes after looking at the measured consumption (rules of its InsightEngine). */
data class Recommendation(
    val id: String,
    val title: String,
    val detail: String,
    /** Monthly saving in soles, as the backend estimated it. */
    val estimatedSavingSoles: Double,
    val applied: Boolean,
    val generatedAt: Instant?,
)

/** The backend's forecast of one site's bill for a month (`/analytics/bill-predictions`). */
data class BillPrediction(
    val siteId: String?,
    val year: Int,
    val month: Int,
    val estimatedAmountSoles: Double,
    val estimatedKwh: Double,
    val createdAt: Instant?,
)

data class RankingEntry(
    val rank: Int,
    val deviceId: String?,
    val deviceName: String?,
    val kwh: Double,
    val amountSoles: Double,
    val percentOfTotal: Double,
)

/** One saved ranking of equipment by consumption (`/analytics/consumption-rankings`). */
data class ConsumptionRanking(
    val entries: List<RankingEntry>,
    val generatedAt: Instant?,
)
