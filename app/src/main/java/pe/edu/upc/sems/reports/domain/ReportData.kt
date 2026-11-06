package pe.edu.upc.sems.reports.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.analytics.domain.model.Recommendation
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.organizations.domain.model.OrgStatus
import pe.edu.upc.sems.organizations.domain.model.Site

val LIMA: ZoneId = ZoneId.of("America/Lima")

/** The choices of the "Periodo" menu (Figma M20, M21). */
enum class ReportPreset { THIS_MONTH, LAST_MONTH, LAST_THREE_MONTHS, CUSTOM }

/** First and last day (both included) of a report. */
data class ReportRange(val from: LocalDate, val to: LocalDate) {
    /** True when it is exactly one calendar month, from its first day to its last. */
    fun isWholeMonth(): Boolean =
        from.dayOfMonth == 1 && from.year == to.year && from.month == to.month && to == from.withDayOfMonth(from.lengthOfMonth())
}

/** The days a preset covers, as of [today]. [custom] is what the user picked for [ReportPreset.CUSTOM]. */
fun reportRange(preset: ReportPreset, today: LocalDate, custom: ReportRange?): ReportRange? = when (preset) {
    ReportPreset.THIS_MONTH -> ReportRange(today.withDayOfMonth(1), today)
    ReportPreset.LAST_MONTH -> {
        val first = today.minusMonths(1).withDayOfMonth(1)
        ReportRange(first, first.plusMonths(1).minusDays(1))
    }
    // The current month and the two before it.
    ReportPreset.LAST_THREE_MONTHS -> ReportRange(today.minusMonths(2).withDayOfMonth(1), today)
    ReportPreset.CUSTOM -> custom
}

/** One site of the report: what it consumed and spent, and how high its demand went against what it contracts. */
data class ReportSiteRow(
    val siteName: String,
    val kwh: Double,
    /** Sum of the cost the backend put on each reading (energy only); null when no reading carried one. */
    val costSoles: Double?,
    /** Highest power any reading of the period reached; null without readings. */
    val peakKw: Double?,
    val contractedKw: Double,
)

/**
 * Everything the PDF says. Each figure is added up from what the backend sent (readings, alerts,
 * recommendations); the app does not price energy or invent anything.
 */
data class ReportData(
    val organizationName: String,
    val range: ReportRange,
    val sites: List<ReportSiteRow>,
    val totalKwh: Double,
    val totalCostSoles: Double?,
    val peakKw: Double?,
    /** Alerts of the period that are no longer waiting: acknowledged or resolved. */
    val alertsAttended: Int,
    /** Mean minutes from trigger to resolution, over the resolved alerts; null when none was resolved. */
    val avgResponseMinutes: Long?,
    /** Monthly saving the backend estimated for the recommendations applied in the period; null when none. */
    val appliedSavingSoles: Double?,
    val generatedOn: LocalDate,
) {
    val hasReadings: Boolean get() = sites.any { it.peakKw != null }
}

private fun ReportRange.window(): Pair<Instant, Instant> =
    from.atStartOfDay(LIMA).toInstant() to to.plusDays(1).atStartOfDay(LIMA).toInstant()

private fun Instant?.within(window: Pair<Instant, Instant>): Boolean =
    this != null && !this.isBefore(window.first) && this.isBefore(window.second)

/**
 * Builds the report. A site's readings are the ones of its meters (a reading carries its meter, and the meter
 * its site), the same way the Inicio screen does it.
 */
fun buildReport(
    organizationName: String,
    sites: List<Site>,
    meters: List<EnergyMeter>,
    readings: List<EnergyReading>,
    alerts: List<Alert>,
    recommendations: List<Recommendation>,
    range: ReportRange,
    today: LocalDate,
): ReportData {
    val window = range.window()
    val siteOfMeter = meters.mapNotNull { m -> m.siteId?.let { m.id to it } }.toMap()
    val inRange = readings.filter { it.measuredAt.within(window) }

    val rows = sites.filter { it.status == OrgStatus.ACTIVE }.map { site ->
        val mine = inRange.filter { r -> r.meterId?.let(siteOfMeter::get) == site.id }
        ReportSiteRow(
            siteName = site.name,
            kwh = mine.mapNotNull { it.energyKwh }.sum(),
            costSoles = mine.mapNotNull { it.estimatedCostSoles }.takeIf { it.isNotEmpty() }?.sum(),
            peakKw = mine.mapNotNull { it.powerKw }.maxOrNull(),
            contractedKw = site.contractedPowerKw,
        )
    }

    val ofPeriod = alerts.filter { it.triggeredAt.within(window) }
    val attended = ofPeriod.filter { it.status != AlertStatus.ACTIVE }
    val responseMinutes = ofPeriod.mapNotNull { alert ->
        val triggered = alert.triggeredAt
        val resolved = alert.resolvedAt
        if (alert.status == AlertStatus.RESOLVED && triggered != null && resolved != null) {
            java.time.Duration.between(triggered, resolved).toMinutes().coerceAtLeast(0)
        } else {
            null
        }
    }

    val saving = recommendations.filter { it.applied && it.appliedAt.within(window) }
        .sumOf { it.estimatedSavingSoles }

    return ReportData(
        organizationName = organizationName,
        range = range,
        sites = rows,
        totalKwh = rows.sumOf { it.kwh },
        totalCostSoles = rows.mapNotNull { it.costSoles }.takeIf { it.isNotEmpty() }?.sum(),
        peakKw = rows.mapNotNull { it.peakKw }.maxOrNull(),
        alertsAttended = attended.size,
        avgResponseMinutes = responseMinutes.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        appliedSavingSoles = saving.takeIf { it > 0.0 },
        generatedOn = today,
    )
}

// Spelled out here because the phone's locale data varies (some write "setiembre").
private val SHORT_MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
val MONTH_NAMES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** `Reporte_SEMS_sep-2026.pdf` for a whole month, `Reporte_SEMS_2026-09-05_2026-09-20.pdf` otherwise. */
fun reportFileName(range: ReportRange): String {
    return if (range.isWholeMonth()) {
        "Reporte_SEMS_${SHORT_MONTHS[range.from.monthValue - 1]}-${range.from.year}.pdf"
    } else {
        "Reporte_SEMS_${range.from}_${range.to}.pdf"
    }
}
