package pe.edu.upc.sems.analytics.data.mapper

import java.time.Instant
import pe.edu.upc.sems.analytics.data.remote.BillPredictionDto
import pe.edu.upc.sems.analytics.data.remote.ConsumptionRankingDto
import pe.edu.upc.sems.analytics.data.remote.RankingItemDto
import pe.edu.upc.sems.analytics.data.remote.RecommendationDto
import pe.edu.upc.sems.analytics.domain.model.BillPrediction
import pe.edu.upc.sems.analytics.domain.model.ConsumptionRanking
import pe.edu.upc.sems.analytics.domain.model.RankingEntry
import pe.edu.upc.sems.analytics.domain.model.Recommendation

fun RecommendationDto.toDomain() = Recommendation(
    id = id,
    title = title.orEmpty(),
    detail = description.orEmpty(),
    estimatedSavingSoles = estimatedSavingAmount ?: 0.0,
    // The backend sends `status` ("pending" / "applied") and applied_at, not a boolean.
    applied = status.equals("applied", ignoreCase = true),
    generatedAt = generatedAt.toInstantOrNull(),
)

fun BillPredictionDto.toDomain() = BillPrediction(
    siteId = siteId?.takeIf { it.isNotBlank() },
    year = predictionYear,
    month = predictionMonth,
    estimatedAmountSoles = estimatedAmount ?: 0.0,
    estimatedKwh = estimatedKwh ?: 0.0,
    createdAt = createdAt.toInstantOrNull(),
)

fun RankingItemDto.toDomain() = RankingEntry(
    rank = rank,
    deviceId = deviceId?.takeIf { it.isNotBlank() },
    deviceName = deviceName?.takeIf { it.isNotBlank() },
    kwh = totalKwh ?: 0.0,
    amountSoles = estimatedAmount ?: 0.0,
    percentOfTotal = percentageOfTotal ?: 0.0,
)

fun ConsumptionRankingDto.toDomain() = ConsumptionRanking(
    entries = rankings.map { it.toDomain() },
    generatedAt = generatedAt.toInstantOrNull(),
)

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
