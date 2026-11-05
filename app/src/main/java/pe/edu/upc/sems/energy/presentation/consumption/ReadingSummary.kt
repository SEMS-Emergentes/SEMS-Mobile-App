package pe.edu.upc.sems.energy.presentation.consumption

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import pe.edu.upc.sems.energy.domain.model.EnergyReading

private val LIMA: ZoneId = ZoneId.of("America/Lima")

/** The period chips of the consumption detail (Figma M13). */
enum class ConsumptionPeriod(val days: Int) {
    TODAY(1),
    WEEK(7),
    MONTH(30),
}

/** Start of [period]: midnight in Lima for today, otherwise that many days back from [now]. */
internal fun periodStart(period: ConsumptionPeriod, now: Instant): Instant = when (period) {
    ConsumptionPeriod.TODAY -> now.atZone(LIMA).toLocalDate().atStartOfDay(LIMA).toInstant()
    else -> now.minus(Duration.ofDays(period.days.toLong()))
}

/**
 * What the detail screen shows for one device and period.
 *
 * Every figure comes from the readings the backend sent: it prices each reading with the site's tariff
 * (`estimated_cost`) and adds up `energy_kwh` and the peak the same way. The app only adds them up; it never
 * multiplies kWh by a price of its own.
 */
data class ConsumptionSummary(
    /** Null when no reading carries the value, so the card shows a dash instead of an invented 0. */
    val totalKwh: Double?,
    val costSoles: Double?,
    val peakKw: Double?,
    /** Today: average kW of each hour of the day (24 values). Other periods: kWh of each day, oldest first. */
    val bars: List<Double>,
    val barsArePower: Boolean,
    /** Newest first. */
    val recent: List<EnergyReading>,
)

internal const val RECENT_READINGS = 5

internal fun summarize(readings: List<EnergyReading>, period: ConsumptionPeriod, now: Instant): ConsumptionSummary {
    val dated = readings.filter { it.measuredAt != null }
    val energy = dated.mapNotNull { it.energyKwh }
    val costs = dated.mapNotNull { it.estimatedCostSoles }
    val bars = if (period == ConsumptionPeriod.TODAY) hourlyPower(dated) else dailyEnergy(dated, period.days, now)
    return ConsumptionSummary(
        totalKwh = energy.takeIf { it.isNotEmpty() }?.sum(),
        costSoles = costs.takeIf { it.isNotEmpty() }?.sum(),
        peakKw = dated.mapNotNull { it.powerKw }.maxOrNull(),
        bars = bars,
        barsArePower = period == ConsumptionPeriod.TODAY,
        recent = dated.sortedByDescending { it.measuredAt }.take(RECENT_READINGS),
    )
}

/** Average power of the readings in each hour (Lima time); an hour without readings is 0. */
private fun hourlyPower(readings: List<EnergyReading>): List<Double> {
    val byHour = readings.mapNotNull { r ->
        val kw = r.powerKw ?: return@mapNotNull null
        r.measuredAt!!.atZone(LIMA).hour to kw
    }.groupBy({ it.first }, { it.second })
    return (0..23).map { hour -> byHour[hour]?.average() ?: 0.0 }
}

/** kWh of each of the last [days] days (Lima time), oldest first, today last. */
internal fun dailyEnergy(readings: List<EnergyReading>, days: Int, now: Instant): List<Double> {
    val today = now.atZone(LIMA).toLocalDate()
    val byDay = readings.mapNotNull { r ->
        val kwh = r.energyKwh ?: return@mapNotNull null
        (r.measuredAt ?: return@mapNotNull null).atZone(LIMA).toLocalDate() to kwh
    }.groupBy({ it.first }, { it.second })
    return (days - 1 downTo 0).map { back -> byDay[today.minusDays(back.toLong())]?.sum() ?: 0.0 }
}
