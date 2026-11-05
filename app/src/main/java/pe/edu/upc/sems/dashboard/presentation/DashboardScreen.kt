package pe.edu.upc.sems.dashboard.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import pe.edu.upc.sems.R
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertSeverity
import pe.edu.upc.sems.alerts.presentation.alerts.agoText
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.gettingstarted.GettingStartedCard
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.BarChart
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters

private val LIMA: ZoneId = ZoneId.of("America/Lima")

/** "Inicio" (Figma M05): the site's figures, the daily chart and the alerts waiting for attention. */
@Composable
fun DashboardScreen(
    onRegisterCompany: () -> Unit,
    onAddFirstSite: (organizationId: String) -> Unit,
    onOpenMonitoring: () -> Unit,
    onOpenAlerts: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize()) {
        GettingStartedCard(
            onRegisterCompany = onRegisterCompany,
            onAddFirstSite = onAddFirstSite,
            modifier = Modifier.fillMaxWidth().padding(horizontal = SemsSpacing.md, vertical = SemsSpacing.sm),
        )
        when (val value = state) {
            DashboardUiState.Loading -> LoadingScreen(Modifier.weight(1f))
            DashboardUiState.NoSite -> Text(
                stringResource(R.string.dashboard_select_site),
                modifier = Modifier.padding(SemsSpacing.md),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is DashboardUiState.Success ->
                DashboardBody(value.content, viewModel::refresh, onOpenMonitoring, onOpenAlerts)
        }
    }
}

@Composable
private fun DashboardBody(
    content: DashboardContent,
    onRetry: () -> Unit,
    onOpenMonitoring: () -> Unit,
    onOpenAlerts: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(SemsSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SemsSpacing.md),
    ) {
        item {
            Text(
                stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content.error?.let { error ->
            item {
                FormErrorBanner(
                    error, R.string.form_error_generic_title, R.string.form_error_generic_body,
                    onRetry, offerRetry = true,
                )
            }
        }
        if (content.meterCount == 0) {
            item { NoMetersCard(onOpenMonitoring) }
        }
        item { FigureCards(content) }
        item { DailyChartCard(content) }
        item { PendingAlerts(content, onOpenAlerts) }
    }
}

@Composable
private fun FigureCards(content: DashboardContent) {
    val colors = MaterialTheme.colorScheme
    val unavailable = stringResource(R.string.value_unavailable)
    val consumption = content.consumption

    val change = content.kwhChangePercent
    val demandShare = consumption?.let { demandPercent(it.peakDemandKw, it.contractedPowerKw) }
    val saving = content.saving

    Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
            FigureCard(
                label = stringResource(R.string.dashboard_card_cost),
                value = consumption?.energyCostSoles?.let(Formatters::soles) ?: unavailable,
                chip = stringResource(R.string.dashboard_cost_chip),
                modifier = Modifier.weight(1f),
            )
            FigureCard(
                label = stringResource(R.string.dashboard_consumption),
                value = consumption?.let { Formatters.kilowattHours(it.kwh) } ?: unavailable,
                chip = change?.let { stringResource(R.string.dashboard_energy_change, signedPercent(it)) }
                    ?: stringResource(R.string.dashboard_energy_period),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
            FigureCard(
                label = stringResource(R.string.dashboard_card_demand),
                value = consumption?.let { Formatters.kilowatts(it.peakDemandKw) } ?: unavailable,
                chip = if (demandShare != null && consumption.contractedPowerKw != null) {
                    stringResource(
                        R.string.dashboard_demand_share,
                        Formatters.quantity(demandShare, 0),
                        Formatters.kilowatts(consumption.contractedPowerKw),
                    )
                } else {
                    stringResource(R.string.dashboard_energy_period)
                },
                // Error colors are only for exceeding the contracted power.
                chipContainer = if (demandShare != null && demandShare > 100.0) colors.errorContainer else null,
                chipContent = if (demandShare != null && demandShare > 100.0) colors.onErrorContainer else null,
                modifier = Modifier.weight(1f),
            )
            FigureCard(
                label = stringResource(R.string.dashboard_card_saving),
                value = saving?.let { signedSoles(it.amountSoles) } ?: "—",
                chip = saving?.let { stringResource(R.string.dashboard_saving_vs, signedPercent(it.percent)) }
                    ?: stringResource(R.string.dashboard_saving_none),
                chipContainer = if (saving != null && saving.amountSoles > 0.0) colors.tertiaryContainer else null,
                chipContent = if (saving != null && saving.amountSoles > 0.0) colors.onTertiaryContainer else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FigureCard(
    label: String,
    value: String,
    chip: String,
    modifier: Modifier = Modifier,
    chipContainer: Color? = null,
    chipContent: Color? = null,
) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Surface(
                shape = CircleShape,
                color = chipContainer ?: MaterialTheme.colorScheme.surfaceVariant,
                contentColor = chipContent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    chip,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun DailyChartCard(content: DashboardContent) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dashboard_chart_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.dashboard_chart_badge),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(SemsSpacing.md))
            val bars = content.dailyKwh
            if (bars == null || !content.hasReadings) {
                Text(
                    stringResource(if (bars == null) R.string.value_unavailable else R.string.dashboard_chart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val first = Instant.now().atZone(LIMA).toLocalDate().minusDays((CHART_DAYS - 1).toLong())
                val title = stringResource(R.string.dashboard_chart_title)
                BarChart(
                    values = bars,
                    xLabels = listOf(DateTimeFormatter.ofPattern("dd/MM").format(first), stringResource(R.string.consumption_today)),
                    description = stringResource(R.string.consumption_chart_description, title, stringResource(R.string.unit_kwh)),
                )
            }
        }
    }
}

@Composable
private fun PendingAlerts(content: DashboardContent, onOpenAlerts: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_alerts_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenAlerts) { Text(stringResource(R.string.dashboard_alerts_all)) }
        }
        when {
            content.alertsUnavailable -> Text(
                stringResource(R.string.dashboard_alerts_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content.pendingAlerts.isEmpty() -> Text(
                stringResource(R.string.dashboard_alerts_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                val now = Instant.now()
                content.pendingAlerts.forEach { alert -> PendingAlertCard(alert, now, onOpenAlerts) }
            }
        }
    }
}

@Composable
private fun PendingAlertCard(alert: Alert, now: Instant, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (alert.severity) {
        AlertSeverity.HIGH -> colors.errorContainer to colors.onErrorContainer
        AlertSeverity.MEDIUM -> colors.secondaryContainer to colors.onSecondaryContainer
        AlertSeverity.LOW -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(SemsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(SemsSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    alert.title.ifBlank { alert.message }.ifBlank { stringResource(R.string.alert_untitled) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                alert.triggeredAt?.let {
                    Text(agoText(it, now), style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@Composable
private fun NoMetersCard(onOpenMonitoring: () -> Unit) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
            Text(stringResource(R.string.dashboard_no_meters_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.dashboard_no_meters_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenMonitoring) { Text(stringResource(R.string.dashboard_open_monitoring)) }
        }
    }
}

/** `+6,2 %` or `−3,1 %`, with the sign always written. */
private fun signedPercent(value: Double): String =
    (if (value < 0) "−" else "+") + Formatters.quantity(abs(value), 1) + " %"

/** `S/ 180,00` or `−S/ 12,00`. */
private fun signedSoles(value: Double): String =
    if (value < 0) "−" + Formatters.soles(abs(value)) else Formatters.soles(value)
