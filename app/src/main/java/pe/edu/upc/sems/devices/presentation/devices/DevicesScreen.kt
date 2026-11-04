package pe.edu.upc.sems.devices.presentation.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.devices.domain.model.DeviceStatus
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters

/** Equipment inventory. Energy meters belong to Monitoring and are not business equipment. */
@Composable
fun DevicesScreen(
    onNewDevice: () -> Unit,
    onDeviceClick: (deviceId: String) -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    DevicesTab(viewModel, onNewDevice, onDeviceClick)
}

@Composable
private fun DevicesTab(
    viewModel: DevicesViewModel,
    onNewDevice: () -> Unit,
    onDeviceClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    when (val current = state) {
        DevicesUiState.Loading -> LoadingScreen()
        is DevicesUiState.Error -> Column(Modifier.padding(SemsSpacing.md)) {
            FormErrorBanner(
                error = current.error,
                conflictTitle = R.string.form_error_generic_title,
                conflictBody = R.string.form_error_generic_body,
                onRetry = viewModel::refresh,
                offerRetry = true,
            )
        }
        is DevicesUiState.Success -> Box(Modifier.fillMaxSize()) {
            DevicesList(current, onDeviceClick)
            // FAB: radius 16 dp, Primary Container (design spec).
            ExtendedFloatingActionButton(
                onClick = onNewDevice,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.devices_add)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(SemsSpacing.md),
            )
        }
    }
}

@Composable
private fun DevicesList(state: DevicesUiState.Success, onDeviceClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = SemsSpacing.md, end = SemsSpacing.md, top = SemsSpacing.md, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm),
    ) {
        state.consumptionError?.let { error ->
            item {
                FormErrorBanner(
                    error = error,
                    conflictTitle = R.string.form_error_generic_title,
                    conflictBody = R.string.form_error_generic_body,
                    onRetry = {},
                    offerRetry = false,
                )
            }
        }
        if (state.items.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.devices_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(
                        R.string.devices_summary,
                        pluralStringResource(R.plurals.devices_count, state.items.size, state.items.size),
                        pluralStringResource(R.plurals.sites_count, state.siteCount, state.siteCount),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { it.device.id }) { item -> DeviceCard(item, onClick = { onDeviceClick(item.device.id) }) }
    }
}

@Composable
private fun DeviceCard(item: DeviceItem, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(SemsSpacing.sm))
                StatusBadge(item.device.status)
            }
            val place = listOfNotNull(item.siteName, item.zoneName).joinToString(" · ")
            if (place.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = SemsSpacing.xs),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_storefront),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(SemsSpacing.sm))
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = SemsSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_bolt),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(SemsSpacing.sm))
                Text(
                    text = item.todayKwh?.let { stringResource(R.string.devices_kwh_today, Formatters.kilowattHours(it)) }
                        ?: stringResource(R.string.devices_no_reading),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.todayCostSoles?.let(Formatters::soles) ?: stringResource(R.string.devices_no_reading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Encendido / Sin señal / Apagado (Figma M07), from the backend status. */
@Composable
private fun StatusBadge(status: DeviceStatus) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (status) {
        DeviceStatus.ACTIVE -> Triple(R.string.device_status_on, colors.tertiaryContainer, colors.onTertiaryContainer)
        DeviceStatus.DISCONNECTED -> Triple(R.string.device_status_no_signal, colors.errorContainer, colors.onErrorContainer)
        else -> Triple(R.string.device_status_off, colors.surfaceVariant, colors.onSurfaceVariant)
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xs),
        )
    }
}
