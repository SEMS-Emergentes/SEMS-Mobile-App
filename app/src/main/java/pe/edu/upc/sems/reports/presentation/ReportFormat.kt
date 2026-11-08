package pe.edu.upc.sems.reports.presentation

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import pe.edu.upc.sems.reports.domain.MONTH_NAMES
import pe.edu.upc.sems.reports.domain.ReportRange

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "Septiembre 2026" for a whole month, "01/10/2026 – 15/10/2026" for any other range. */
fun reportPeriodLabel(range: ReportRange): String =
    if (range.isWholeMonth()) monthYear(range.from) else "${range.from.format(DATE)} – ${range.to.format(DATE)}"

/** "septiembre 2026" (lower case, to follow "Mes pasado · "). */
fun monthYearLower(date: LocalDate): String = monthYear(date).lowercase()

private fun monthYear(date: LocalDate): String =
    MONTH_NAMES[date.monthValue - 1].replaceFirstChar { it.uppercase() } + " " + date.year

/** "14 min", "1 h 5 min", "2 h". */
fun formatDuration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
