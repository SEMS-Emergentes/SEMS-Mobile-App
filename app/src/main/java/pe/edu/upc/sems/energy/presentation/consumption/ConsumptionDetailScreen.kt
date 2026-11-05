package pe.edu.upc.sems.energy.presentation.consumption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pe.edu.upc.sems.R
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.BarChart
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters

private val LIMA: ZoneId = ZoneId.of("America/Lima")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm")
private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")

/** "Detalle de consumo" of one device (Figma M13): period, consumption, cost, peak, chart and recent readings. */
@Composable
fun ConsumptionDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    viewModel: ConsumptionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(deviceId) { viewModel.start(deviceId) }

    FormScaffold(title = state.deviceName ?: stringResource(R.string.monitoring_detail_title), onBack = onBack) {
        when {
            state.isLoading -> LoadingScreen()
            state.error != null -> FormErrorBanner(
                error = state.error!!,
                conflictTitle = R.string.form_error_generic_title,
                conflictBody = R.string.form_error_generic_body,
                onRetry = viewModel::retry,
                offerRetry = true,
            )
            else -> Detail(state, viewModel)
        }
    }
}

@Composable
private fun Detail(state: ConsumptionDetailUiState, viewModel: ConsumptionDetailViewModel) {
    state.place?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(SemsSpacing.sm))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
        ConsumptionPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == state.period,
                onClick = { viewModel.onPeriodChange(period) },
                label = { Text(stringResource(period.label())) },
            )
        }
    }
    Spacer(Modifier.height(SemsSpacing.md))

    state.periodError?.let { error ->
        FormErrorBanner(
            error = error,
            conflictTitle = R.string.form_error_generic_title,
            conflictBody = R.string.form_error_generic_body,
            onRetry = { viewModel.onPeriodChange(state.period) },
            offerRetry = true,
        )
        return
    }
    if (state.isLoadingPeriod || state.summary == null) {
        Row(Modifier.fillMaxWidth().padding(vertical = SemsSpacing.lg), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.size(32.dp))
        }
        return
    }

    val summary = state.summary
    val dash = stringResource(R.string.devices_no_reading)
    Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.sm), modifier = Modifier.fillMaxWidth()) {
        StatCard(
            label = stringResource(R.string.consumption_card_energy),
            value = summary.totalKwh?.let { Formatters.quantity(it) } ?: dash,
            caption = stringResource(R.string.unit_kwh),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.consumption_card_cost),
            value = summary.costSoles?.let(Formatters::soles) ?: dash,
            caption = stringResource(state.period.caption()),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.consumption_card_peak),
            value = summary.peakKw?.let { Formatters.quantity(it) } ?: dash,
            caption = stringResource(R.string.unit_kw),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(SemsSpacing.md))

    if (summary.recent.isEmpty()) {
        Text(
            text = stringResource(R.string.consumption_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    ChartCard(summary, state.period)
    Spacer(Modifier.height(SemsSpacing.md))
    RecentReadings(summary.recent, state.period)
}

@Composable
private fun StatCard(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(SemsSpacing.sm)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(summary: ConsumptionSummary, period: ConsumptionPeriod) {
    val title = stringResource(if (summary.barsArePower) R.string.consumption_chart_power else R.string.consumption_chart_energy)
    val unit = stringResource(if (summary.barsArePower) R.string.unit_kw else R.string.unit_kwh)
    val labels = if (summary.barsArePower) {
        listOf("0 h", "6 h", "12 h", "18 h", "23 h")
    } else {
        val first = Instant.now().atZone(LIMA).toLocalDate().minusDays((period.days - 1).toLong())
        listOf(DAY_MONTH.format(first), stringResource(R.string.consumption_today))
    }
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(SemsSpacing.md))
            BarChart(
                values = summary.bars,
                xLabels = labels,
                description = stringResource(R.string.consumption_chart_description, title, unit),
            )
        }
    }
}

@Composable
private fun RecentReadings(readings: List<EnergyReading>, period: ConsumptionPeriod) {
    val dash = stringResource(R.string.devices_no_reading)
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Text(stringResource(R.string.consumption_recent), style = MaterialTheme.typography.titleMedium)
            readings.forEach { reading ->
                HorizontalDivider(Modifier.padding(vertical = SemsSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reading.measuredAt?.let { (if (period == ConsumptionPeriod.TODAY) TIME else DAY_TIME).format(it.atZone(LIMA)) }
                            ?: dash,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = reading.powerKw?.let(Formatters::kilowatts) ?: dash,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = reading.estimatedCostSoles?.let(Formatters::soles) ?: dash,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun ConsumptionPeriod.label(): Int = when (this) {
    ConsumptionPeriod.TODAY -> R.string.consumption_period_today
    ConsumptionPeriod.WEEK -> R.string.consumption_period_week
    ConsumptionPeriod.MONTH -> R.string.consumption_period_month
}

private fun ConsumptionPeriod.caption(): Int = when (this) {
    ConsumptionPeriod.TODAY -> R.string.consumption_caption_today
    ConsumptionPeriod.WEEK -> R.string.consumption_caption_week
    ConsumptionPeriod.MONTH -> R.string.consumption_caption_month
}
