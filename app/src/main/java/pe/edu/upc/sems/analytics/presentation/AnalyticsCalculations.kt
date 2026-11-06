package pe.edu.upc.sems.analytics.presentation

import pe.edu.upc.sems.analytics.domain.model.BillPrediction
import pe.edu.upc.sems.analytics.domain.model.ConsumptionRanking
import pe.edu.upc.sems.analytics.domain.model.Recommendation
import pe.edu.upc.sems.dashboard.presentation.kwhChangePercent
import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.energy.domain.model.SiteConsumption

/** The bill forecast of one month, added up over the sites. Every number is the backend's. */
data class MonthForecast(
    val year: Int,
    val month: Int,
    val amountSoles: Double,
    val kwh: Double,
)

/**
 * The forecast to show: the most recent month the backend has, with the latest forecast of each site added
 * together. The backend keeps one forecast per site per day, so older ones of the same site must not count.
 * Null when there is none (the card shows a dash).
 */
internal fun monthForecast(predictions: List<BillPrediction>): MonthForecast? {
    val latestMonth = predictions.maxOfOrNull { it.year * 12 + it.month } ?: return null
    val ofMonth = predictions.filter { it.year * 12 + it.month == latestMonth }
    val newestPerSite = ofMonth
        .groupBy { it.siteId.orEmpty() }
        .values
        .mapNotNull { forecasts -> forecasts.maxByOrNull { it.createdAt ?: java.time.Instant.MIN } }
    return MonthForecast(
        year = ofMonth.first().year,
        month = ofMonth.first().month,
        amountSoles = newestPerSite.sumOf { it.estimatedAmountSoles },
        kwh = newestPerSite.sumOf { it.estimatedKwh },
    )
}

/** "Puedes ahorrar hasta": what the pending recommendations add up to, per month. Null when there is nothing. */
internal fun potentialSaving(recommendations: List<Recommendation>): Double? =
    recommendations.filterNot { it.applied }.sumOf { it.estimatedSavingSoles }.takeIf { it > 0.0 }

/** One site of "Comparación entre locales". */
data class SiteRow(
    val siteId: String,
    val name: String?,
    val kwh: Double,
    /** Energy only (no power charge, fixed charge or IGV); null when the site has no tariff. */
    val energyCostSoles: Double?,
    /** 0..1, against the site that consumed most. */
    val barFraction: Float,
    /** Change in kWh against the window before; null when there is nothing before to compare with. */
    val changePercent: Double?,
)

/**
 * The comparison between sites, biggest consumer first. [doubledWindow] is the same query over twice the days:
 * minus [recent] it gives the window before, which is what the change is measured against.
 */
internal fun siteRows(
    recent: List<SiteConsumption>,
    doubledWindow: List<SiteConsumption>?,
    names: Map<String, String>,
): List<SiteRow> {
    val biggest = recent.maxOfOrNull { it.kwh }?.takeIf { it > 0.0 } ?: 1.0
    return recent.sortedByDescending { it.kwh }.map { site ->
        SiteRow(
            siteId = site.siteId,
            name = names[site.siteId],
            kwh = site.kwh,
            energyCostSoles = site.energyCostSoles,
            barFraction = (site.kwh / biggest).toFloat().coerceIn(0f, 1f),
            changePercent = doubledWindow?.firstOrNull { it.siteId == site.siteId }
                ?.let { kwhChangePercent(site.kwh, it.kwh) },
        )
    }
}

/** One line of "Top consumidores". */
data class RankingRow(
    val rank: Int,
    val name: String,
    val siteName: String?,
    val kwh: Double,
    val amountSoles: Double,
    val percentOfTotal: Double,
)

/**
 * The saved ranking with the names of today's equipment. As in the web, equipment that no longer exists
 * (removed) is left out when the user still has equipment; the name comes from the device and, failing that,
 * from the ranking itself.
 */
internal fun rankingRows(
    ranking: ConsumptionRanking?,
    devices: List<Device>,
    siteNames: Map<String, String>,
): List<RankingRow> {
    val byId = devices.associateBy { it.id }
    return ranking?.entries.orEmpty()
        .sortedBy { it.rank }
        .filter { it.deviceId == null || devices.isEmpty() || it.deviceId in byId }
        .mapIndexed { index, entry ->
            val device = entry.deviceId?.let(byId::get)
            RankingRow(
                rank = index + 1,
                name = device?.name ?: entry.deviceName ?: "",
                siteName = device?.siteId?.let(siteNames::get),
                kwh = entry.kwh,
                amountSoles = entry.amountSoles,
                percentOfTotal = entry.percentOfTotal,
            )
        }
}
