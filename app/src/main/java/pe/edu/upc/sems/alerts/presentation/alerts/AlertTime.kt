package pe.edu.upc.sems.alerts.presentation.alerts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pe.edu.upc.sems.R

private val LIMA: ZoneId = ZoneId.of("America/Lima")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")

/** "hace 10 min", "hace 2 h", "hace 3 d", and the date after a week. Times in Peru (America/Lima). */
@Composable
fun agoText(instant: Instant, now: Instant): String {
    val elapsed = Duration.between(instant, now)
    return when {
        elapsed.isNegative || elapsed.toMinutes() < 1 -> stringResource(R.string.alert_time_now)
        elapsed.toMinutes() < 60 -> stringResource(R.string.alert_time_minutes, elapsed.toMinutes().toInt())
        elapsed.toHours() < 24 -> stringResource(R.string.alert_time_hours, elapsed.toHours().toInt())
        elapsed.toDays() < 7 -> stringResource(R.string.alert_time_days, elapsed.toDays().toInt())
        else -> DAY_MONTH.format(instant.atZone(LIMA))
    }
}

/** "hoy 18:40", "ayer 19:05", or the date. */
@Composable
fun dayTimeText(instant: Instant, now: Instant): String {
    val date = instant.atZone(LIMA).toLocalDate()
    val today = now.atZone(LIMA).toLocalDate()
    return when (date) {
        today -> stringResource(R.string.alert_time_today, TIME.format(instant.atZone(LIMA)))
        today.minusDays(1) -> stringResource(R.string.alert_time_yesterday, TIME.format(instant.atZone(LIMA)))
        else -> DAY_MONTH.format(instant.atZone(LIMA))
    }
}
