package pe.edu.upc.sems.reports.presentation

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.edu.upc.sems.reports.domain.ReportRange
import pe.edu.upc.sems.reports.domain.monthNames

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "Septiembre 2026" / "September 2026" for a whole month, "01/10/2026 – 15/10/2026" for any other range. */
fun reportPeriodLabel(range: ReportRange, locale: Locale = Locale.getDefault()): String =
    if (range.isWholeMonth()) monthYear(range.from, locale) else "${range.from.format(DATE)} – ${range.to.format(DATE)}"

/** "septiembre 2026" (lower case, to follow "Mes pasado · "). */
fun monthYearLower(date: LocalDate, locale: Locale = Locale.getDefault()): String = monthYear(date, locale).lowercase()

private fun monthYear(date: LocalDate, locale: Locale): String =
    monthNames(locale)[date.monthValue - 1].replaceFirstChar { it.uppercase() } + " " + date.year

/** "14 min", "1 h 5 min", "2 h". */
fun formatDuration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
