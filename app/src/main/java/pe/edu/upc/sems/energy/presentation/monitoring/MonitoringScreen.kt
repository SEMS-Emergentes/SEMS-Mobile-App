package pe.edu.upc.sems.energy.presentation.monitoring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.energy.presentation.meters.MetersSection
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters

@Composable
fun MonitoringScreen(
    onLinkMeter: () -> Unit,
    onMeterHistory: (String) -> Unit,
    onDeviceClick: (String) -> Unit,
    viewModel: MonitoringViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    when (val value = state) {
        MonitoringUiState.Loading -> LoadingScreen()
        is MonitoringUiState.Success -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(SemsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SemsSpacing.lg),
        ) {
            if (value.content.siteName == null) {
                item { EmptyCard(R.string.monitoring_select_site) }
                return@LazyColumn
            }
            item { MetersSection(onLinkMeter = onLinkMeter, onMeterHistory = onMeterHistory) }
            item {
                ZoneConsumptionSection(
                    content = value.content.zoneConsumption,
                    error = value.content.zoneError,
                    selectedDays = value.content.selectedDays,
                    onDays = viewModel::selectDays,
                    onRetry = viewModel::refresh,
                )
            }
            item {
                Text(stringResource(R.string.monitoring_equipment_with_readings), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.monitoring_equipment_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            value.content.equipmentError?.let { error ->
                item {
                    FormErrorBanner(
                        error,
                        R.string.form_error_generic_title,
                        R.string.form_error_generic_body,
                        viewModel::refresh,
                        offerRetry = true,
                    )
                }
            }
            if (value.content.equipment.isEmpty() && value.content.equipmentError == null) {
                item { EmptyCard(R.string.monitoring_equipment_empty) }
            } else {
                items(value.content.equipment, key = { it.device.id }) { item ->
                    OutlinedCard(
                        Modifier.fillMaxWidth().clickable { onDeviceClick(item.device.id) },
                    ) {
                        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
                            Text(item.device.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.current?.powerKw?.let(Formatters::kilowatts)
                                    ?: stringResource(if (item.readingError) R.string.monitoring_reading_error else R.string.devices_no_reading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.readingError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            item.consumption?.totalKwh?.let {
                                Text(Formatters.kilowattHours(it), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneConsumptionSection(
    content: SiteZoneConsumption?,
    error: pe.edu.upc.sems.organizations.presentation.common.FormError?,
    selectedDays: Int,
    onDays: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
        Text(stringResource(R.string.monitoring_by_zone), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
            listOf(7, 14, 30).forEach { days ->
                FilterChip(
                    selected = selectedDays == days,
                    onClick = { onDays(days) },
                    label = { Text(stringResource(R.string.monitoring_days, days)) },
                )
            }
        }
        if (error != null) {
            FormErrorBanner(
                error,
                R.string.form_error_generic_title,
                R.string.form_error_generic_body,
                onRetry,
                offerRetry = true,
            )
            return
        }
        if (content == null ||
            (content.zones.none { it.meterCount > 0 } && (content.unassigned?.meterCount ?: 0) == 0)
        ) {
            EmptyCard(R.string.monitoring_zone_empty)
            return
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                Text(
                    stringResource(R.string.monitoring_period_total, Formatters.kilowattHours(content.totalKwh)),
                    style = MaterialTheme.typography.titleMedium,
                )
                (content.zones + listOfNotNull(content.unassigned)).forEach { zone ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(zone.zoneName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.monitoring_meter_count, zone.meterCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            Text(Formatters.kilowattHours(zone.kwh), style = MaterialTheme.typography.labelLarge)
                            zone.estimatedEnergyCostSoles?.let {
                                Text(Formatters.soles(it), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsumptionDetailScreen(
    deviceId: String,
    onBack: () -> Unit,
    viewModel: MonitoringViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    LifecycleResumeEffect(deviceId) {
        viewModel.refresh()
        viewModel.loadHistory(deviceId)
        onPauseOrDispose { }
    }
    FormScaffold(stringResource(R.string.monitoring_detail_title), onBack) {
        when (val value = state) {
            MonitoringUiState.Loading -> LoadingScreen()
            is MonitoringUiState.Success -> {
                val item = value.content.equipment.firstOrNull { it.device.id == deviceId }
                Text(item?.device?.name ?: stringResource(R.string.monitoring_device_unknown), style = MaterialTheme.typography.titleLarge)
                Text(
                    item?.current?.powerKw?.let(Formatters::kilowatts) ?: stringResource(R.string.monitoring_no_readings),
                    modifier = Modifier.padding(top = SemsSpacing.md),
                    style = MaterialTheme.typography.headlineSmall,
                )
                item?.consumption?.totalKwh?.let {
                    Text(Formatters.kilowattHours(it), modifier = Modifier.padding(top = SemsSpacing.xs))
                }
                TextButton(onClick = { viewModel.loadHistory(deviceId) }) {
                    Text(stringResource(R.string.action_retry))
                }
                when (val readings = history) {
                    HistoryUiState.Idle, HistoryUiState.Loading -> LoadingScreen(Modifier.height(120.dp))
                    is HistoryUiState.Error -> FormErrorBanner(
                        readings.error,
                        R.string.form_error_generic_title,
                        R.string.form_error_generic_body,
                        { viewModel.loadHistory(deviceId) },
                        offerRetry = true,
                    )
                    is HistoryUiState.Success -> {
                        if (readings.readings.isEmpty()) {
                            Text(stringResource(R.string.monitoring_no_readings), modifier = Modifier.padding(top = SemsSpacing.md))
                        } else {
                            ReadingChart(readings.readings)
                            readings.readings.take(10).forEach { reading ->
                                Text(
                                    listOfNotNull(
                                        reading.measuredAt?.toString(),
                                        reading.powerKw?.let(Formatters::kilowatts),
                                        reading.energyKwh?.let(Formatters::kilowattHours),
                                    ).joinToString(" · "),
                                    modifier = Modifier.padding(top = SemsSpacing.sm),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingChart(readings: List<EnergyReading>) {
    val values = readings.asReversed().mapNotNull { it.powerKw }.takeLast(30)
    if (values.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = SemsSpacing.md)) {
        val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val step = if (values.size <= 1) 0f else size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val point = Offset(index * step, size.height - (value / max * size.height).toFloat())
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun EmptyCard(message: Int) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(message),
            modifier = Modifier.padding(SemsSpacing.lg),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
