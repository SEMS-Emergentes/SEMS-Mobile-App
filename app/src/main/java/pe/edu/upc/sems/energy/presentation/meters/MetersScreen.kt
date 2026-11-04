package pe.edu.upc.sems.energy.presentation.meters

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.energy.domain.model.MeterStatus
import pe.edu.upc.sems.organizations.domain.model.Zone
import pe.edu.upc.sems.organizations.presentation.common.DropdownField
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters

@Composable
fun MetersSection(
    onLinkMeter: () -> Unit,
    onMeterHistory: (String) -> Unit,
    viewModel: MetersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.monitoring_meters_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.monitoring_meters_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (val current = state) {
            MetersUiState.Loading -> LoadingScreen(Modifier.height(160.dp))
            is MetersUiState.Error -> {
                FormErrorBanner(
                    current.error,
                    R.string.form_error_generic_title,
                    R.string.form_error_generic_body,
                    viewModel::refresh,
                    offerRetry = true,
                )
                if (current.canManage) {
                    Button(onClick = onLinkMeter, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.meter_link_submit))
                    }
                }
            }
            is MetersUiState.Success -> {
                current.content.zoneError?.let { error ->
                    FormErrorBanner(
                        error,
                        R.string.form_error_generic_title,
                        R.string.form_error_generic_body,
                        viewModel::refresh,
                        offerRetry = true,
                    )
                }
                MeterFilters(current.content, viewModel)
                current.content.actionError?.let {
                    FormErrorBanner(
                        it,
                        R.string.form_error_generic_title,
                        R.string.form_error_generic_body,
                        viewModel::clearActionError,
                    )
                }
                if (current.content.visibleItems.isEmpty()) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(SemsSpacing.lg)) {
                            Text(
                                if (current.content.items.isEmpty()) stringResource(R.string.meters_empty)
                                else stringResource(R.string.meters_filters_empty),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (current.content.items.isEmpty()) stringResource(R.string.meters_empty_detail)
                                else stringResource(R.string.meters_filters_empty_detail),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    current.content.visibleItems.forEach { item ->
                        MeterCard(
                            item = item,
                            zones = current.content.zones,
                            canManage = current.content.canManage && !current.content.actionInProgress,
                            canChangeZone = current.content.zoneError == null,
                            onReassign = { zoneId -> viewModel.reassignZone(item.meter.id, zoneId) },
                            onUnlink = { viewModel.unlink(item.meter.id) },
                            onHistory = { onMeterHistory(item.meter.id) },
                        )
                    }
                }
                if (current.content.canManage) {
                    Button(
                        onClick = onLinkMeter,
                        enabled = !current.content.actionInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.meter_link_submit)) }
                }
            }
        }
    }
}

@Composable
private fun MeterFilters(content: MetersContent, viewModel: MetersViewModel) {
    if (content.items.isEmpty()) return
    if (content.zoneError == null) {
        DropdownField(
            value = content.selectedZoneId,
            options = listOf<String?>(null, UNASSIGNED_ZONE_FILTER) + content.zones.map { it.id },
            label = R.string.meters_filter_zone,
            optionLabel = { id ->
                when (id) {
                    null -> stringResource(R.string.meters_filter_all_zones)
                    UNASSIGNED_ZONE_FILTER -> stringResource(R.string.meter_no_zone_short)
                    else -> content.zones.firstOrNull { it.id == id }?.name ?: id
                }
            },
            onSelect = viewModel::selectZoneFilter,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SemsSpacing.xs),
    ) {
        FilterChip(
            selected = content.selectedStatus == null,
            onClick = { viewModel.selectStatusFilter(null) },
            label = { Text(stringResource(R.string.meters_filter_all_statuses)) },
        )
        MeterStatus.entries.filter { it != MeterStatus.UNKNOWN }.forEach { status ->
            FilterChip(
                selected = content.selectedStatus == status,
                onClick = { viewModel.selectStatusFilter(status) },
                label = { Text(stringResource(status.label())) },
            )
        }
    }
}

@Composable
private fun MeterCard(
    item: MeterItem,
    zones: List<Zone>,
    canManage: Boolean,
    canChangeZone: Boolean,
    onReassign: (String?) -> Unit,
    onUnlink: () -> Unit,
    onHistory: () -> Unit,
) {
    var confirmUnlink by remember { mutableStateOf(false) }
    var editZone by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.meter.serial, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(item.meter.status.label()), style = MaterialTheme.typography.labelMedium)
            }
            item.meter.model?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                listOfNotNull(
                    item.siteName,
                    when {
                        item.meter.zoneId == null -> stringResource(R.string.meter_no_zone_short)
                        item.zoneName != null -> item.zoneName
                        else -> stringResource(R.string.meter_zone_unavailable)
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                item.readingError -> Text(
                    stringResource(R.string.meter_reading_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                item.latest == null -> Text(stringResource(R.string.meter_no_reading), style = MaterialTheme.typography.bodySmall)
                else -> {
                    item.latest.powerKw?.let {
                        Text(stringResource(R.string.meter_last_power, Formatters.kilowatts(it)), style = MaterialTheme.typography.bodySmall)
                    }
                    item.latest.energyKwh?.let {
                        Text(stringResource(R.string.meter_last_energy, Formatters.kilowattHours(it)), style = MaterialTheme.typography.bodySmall)
                    }
                    item.latest.measuredAt?.let {
                        Text(
                            stringResource(R.string.meter_last_update, formatInstant(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(onClick = onHistory) { Text(stringResource(R.string.meter_history_open)) }
            if (canManage) {
                Row {
                    if (canChangeZone) {
                        TextButton(onClick = { editZone = true }) { Text(stringResource(R.string.meter_change_zone)) }
                    }
                    TextButton(onClick = { confirmUnlink = true }) { Text(stringResource(R.string.meter_unlink)) }
                }
            }
        }
    }
    if (editZone) {
        var selected by remember(item.meter.id) { mutableStateOf(item.meter.zoneId) }
        AlertDialog(
            onDismissRequest = { editZone = false },
            title = { Text(stringResource(R.string.meter_change_zone)) },
            text = {
                DropdownField(
                    value = selected,
                    options = listOf<String?>(null) + zones.map { it.id },
                    label = R.string.meter_zone_label,
                    optionLabel = { id ->
                        id?.let { value -> zones.firstOrNull { it.id == value }?.name }
                            ?: stringResource(R.string.meter_no_zone_option)
                    },
                    onSelect = { selected = it },
                )
            },
            confirmButton = {
                TextButton(onClick = { onReassign(selected); editZone = false }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editZone = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text(stringResource(R.string.meter_unlink)) },
            text = { Text(stringResource(R.string.meter_unlink_message)) },
            confirmButton = {
                TextButton(onClick = { onUnlink(); confirmUnlink = false }) {
                    Text(stringResource(R.string.meter_unlink_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnlink = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun MeterStatus.label(): Int = when (this) {
    MeterStatus.ACTIVE -> R.string.meter_status_active
    MeterStatus.INACTIVE -> R.string.meter_status_inactive
    MeterStatus.MAINTENANCE -> R.string.meter_status_maintenance
    MeterStatus.ERROR -> R.string.meter_status_error
    MeterStatus.UNKNOWN -> R.string.meter_status_unknown
}

private fun formatInstant(value: java.time.Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(value)
