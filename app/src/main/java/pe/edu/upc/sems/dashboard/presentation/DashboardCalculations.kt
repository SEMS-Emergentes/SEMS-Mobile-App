package pe.edu.upc.sems.dashboard.presentation

import java.time.Duration
import java.time.Instant
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.energy.domain.model.EnergyReading

/** Days of the main figures (consumption, energy cost, peak demand). */
internal const val SUMMARY_DAYS = 30

/** Days of the daily chart, and the length of each side of the savings comparison. */
internal const val CHART_DAYS = 14

/** How many pending alerts the Inicio lists before "Ver todo". */
internal const val PENDING_ALERTS_SHOWN = 3

/** How much less (positive) or more (negative) was spent in the last fortnight than in the one before it. */
data class Saving(val amountSoles: Double, val percent: Double)

/** The readings that belong to the site: the backend sends all of the user's readings, whatever the site. */
internal fun readingsOfSite(readings: List<EnergyReading>, meterIds: Set<String>): List<EnergyReading> =
    readings.filter { it.meterId != null && it.meterId in meterIds }

/**
 * Change of the last [SUMMARY_DAYS] days against the same length before them, in kWh.
 *
 * The backend only gives the total of the last N days, so the previous window is the 2×N total minus the last
 * N. Null when there is nothing before to compare with.
 */
internal fun kwhChangePercent(currentKwh: Double, doubleWindowKwh: Double): Double? {
    val previous = doubleWindowKwh - currentKwh
    if (previous <= 0.0) return null
    return (currentKwh - previous) / previous * 100.0
}

/** Peak demand as a share of the contracted power; null when the site has no contracted power. */
internal fun demandPercent(peakKw: Double, contractedKw: Double?): Double? =
    contractedKw?.takeIf { it > 0.0 }?.let { peakKw / it * 100.0 }

/**
 * Same comparison as the web app (dashboard.service.js): the cost the backend put on each reading, added up
 * for the last [CHART_DAYS] days and for the [CHART_DAYS] before them. Null when either side has no priced
 * reading, so there is never a made-up "0 saved".
 */
internal fun fortnightSaving(readings: List<EnergyReading>, now: Instant): Saving? {
    val cutoff = now.minus(Duration.ofDays(CHART_DAYS.toLong()))
    val start = now.minus(Duration.ofDays(2L * CHART_DAYS))
    val priced = readings.filter { it.measuredAt != null && it.estimatedCostSoles != null }
    val current = priced.filter { !it.measuredAt!!.isBefore(cutoff) }.map { it.estimatedCostSoles!! }
    val previous = priced.filter { !it.measuredAt!!.isBefore(start) && it.measuredAt!!.isBefore(cutoff) }
        .map { it.estimatedCostSoles!! }
    if (current.isEmpty() || previous.isEmpty()) return null
    val before = previous.sum()
    if (before <= 0.0) return null
    val saved = before - current.sum()
    return Saving(amountSoles = saved, percent = saved / before * 100.0)
}

/** The most severe active alerts first, newest first among equals. */
internal fun pendingAlerts(alerts: List<Alert>, limit: Int = PENDING_ALERTS_SHOWN): List<Alert> =
    alerts.filter { it.status == AlertStatus.ACTIVE }
        .sortedWith(
            compareByDescending<Alert> { it.severity.rank }.thenByDescending { it.triggeredAt ?: Instant.MIN },
        )
        .take(limit)
