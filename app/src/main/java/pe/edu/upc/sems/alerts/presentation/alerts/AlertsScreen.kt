package pe.edu.upc.sems.alerts.presentation.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import pe.edu.upc.sems.R
import pe.edu.upc.sems.alerts.domain.model.Alert
import pe.edu.upc.sems.alerts.domain.model.AlertSeverity
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen

/** "Alertas": three tabs, Activas, Reconocidas and Resueltas (Figma M14, M15, M16). */
@Composable
fun AlertsScreen(
    onViewSite: (siteId: String) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val snackbar = remember { SnackbarHostState() }
    val resolvedText = stringResource(R.string.alert_resolved_message)
    val undoText = stringResource(R.string.alert_undo)
    val failedText = stringResource(R.string.alert_action_failed)
    LaunchedEffect(state.message) {
        when (val message = state.message ?: return@LaunchedEffect) {
            is AlertMessage.Resolved -> {
                val result = snackbar.showSnackbar(resolvedText, undoText, duration = SnackbarDuration.Long)
                viewModel.onMessageShown()
                if (result == SnackbarResult.ActionPerformed) viewModel.onUndo(message.alertId, message.previous)
            }
            AlertMessage.ActionFailed -> {
                snackbar.showSnackbar(failedText)
                viewModel.onMessageShown()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingScreen()
            state.error != null -> Column(Modifier.padding(SemsSpacing.md)) {
                FormErrorBanner(
                    error = state.error!!,
                    conflictTitle = R.string.form_error_generic_title,
                    conflictBody = R.string.form_error_generic_body,
                    onRetry = viewModel::refresh,
                    offerRetry = true,
                )
            }
            else -> AlertsContent(state, viewModel, onViewSite)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(SemsSpacing.md))
    }
}

@Composable
private fun AlertsContent(state: AlertsUiState, viewModel: AlertsViewModel, onViewSite: (String) -> Unit) {
    val now = Instant.now()
    val visible = visibleAlerts(state.items, state.filter, now)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = SemsSpacing.md, vertical = SemsSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(SemsSpacing.xs),
        ) {
            AlertFilter.entries.forEach { filter ->
                val selected = filter == state.filter
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onFilterChange(filter) },
                    label = { Text(stringResource(filter.label())) },
                    leadingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = SemsSpacing.md, end = SemsSpacing.md, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm),
        ) {
            item {
                Text(
                    text = when (state.filter) {
                        AlertFilter.ACTIVE -> pluralStringResource(R.plurals.alerts_active_summary, visible.size, visible.size)
                        AlertFilter.ACKNOWLEDGED -> stringResource(R.string.alerts_acknowledged_hint)
                        AlertFilter.RESOLVED -> stringResource(R.string.alerts_resolved_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        text = stringResource(state.filter.emptyText()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = SemsSpacing.sm),
                    )
                }
            }
            items(visible, key = { it.alert.id }) { item ->
                AlertCard(
                    item = item,
                    now = now,
                    busy = item.alert.id in state.busyIds,
                    onViewSite = { item.siteId?.let(onViewSite) },
                    onAcknowledge = { viewModel.onAcknowledge(item.alert.id) },
                    onResolve = { viewModel.onResolve(item.alert.id) },
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    item: AlertItem,
    now: Instant,
    busy: Boolean,
    onViewSite: () -> Unit,
    onAcknowledge: () -> Unit,
    onResolve: () -> Unit,
) {
    val alert = item.alert
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                AlertIcon(alert)
                Spacer(Modifier.width(SemsSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = alert.title.ifBlank { alert.message }.ifBlank { stringResource(R.string.alert_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val place = listOfNotNull(item.deviceName, item.siteName).joinToString(" · ")
                    if (place.isNotEmpty()) {
                        Text(
                            text = place,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(SemsSpacing.xs))
                AlertBadge(alert)
            }
            // The title already says it when the backend sends no separate message.
            if (alert.message.isNotBlank() && alert.message != alert.title) {
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = SemsSpacing.sm),
                )
            }
            alertTimeText(alert, now)?.let { time ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = SemsSpacing.sm),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_schedule),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(SemsSpacing.xs))
                    Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (alert.status != AlertStatus.RESOLVED) {
                HorizontalDivider(Modifier.padding(vertical = SemsSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.siteId != null) {
                        TextButton(onClick = onViewSite) { Text(stringResource(R.string.alert_view_site)) }
                        Spacer(Modifier.width(SemsSpacing.xs))
                    }
                    Button(
                        onClick = if (alert.status == AlertStatus.ACTIVE) onAcknowledge else onResolve,
                        enabled = !busy,
                    ) {
                        Text(
                            stringResource(
                                if (alert.status == AlertStatus.ACTIVE) R.string.alert_acknowledge else R.string.alert_mark_resolved,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Active alerts say when they started; acknowledged ones too; resolved ones say when they were resolved. */
@Composable
private fun alertTimeText(alert: Alert, now: Instant): String? = when (alert.status) {
    AlertStatus.RESOLVED -> (alert.resolvedAt ?: alert.triggeredAt)
        ?.let { stringResource(R.string.alert_resolved_at, dayTimeText(it, now)) }
    else -> alert.triggeredAt?.let { agoText(it, now) }
}

@Composable
private fun AlertIcon(alert: Alert) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        alert.status == AlertStatus.RESOLVED -> colors.tertiaryContainer to colors.onTertiaryContainer
        alert.severity == AlertSeverity.HIGH -> colors.errorContainer to colors.onErrorContainer
        alert.severity == AlertSeverity.MEDIUM -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(40.dp).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (alert.status == AlertStatus.RESOLVED) R.drawable.ic_check_circle_filled else R.drawable.ic_error),
            contentDescription = null,
            tint = content,
        )
    }
}

/** Severity while the alert is open; the status once it is acknowledged or resolved (as in the design). */
@Composable
private fun AlertBadge(alert: Alert) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (alert.status) {
        AlertStatus.RESOLVED -> Triple(R.string.alert_status_resolved, colors.tertiaryContainer, colors.onTertiaryContainer)
        AlertStatus.ACKNOWLEDGED -> Triple(R.string.alert_status_acknowledged, colors.primaryContainer, colors.onPrimaryContainer)
        AlertStatus.ACTIVE -> when (alert.severity) {
            AlertSeverity.HIGH -> Triple(R.string.alert_severity_high, colors.errorContainer, colors.onErrorContainer)
            AlertSeverity.MEDIUM -> Triple(R.string.alert_severity_medium, colors.secondaryContainer, colors.onSecondaryContainer)
            AlertSeverity.LOW -> Triple(R.string.alert_severity_low, colors.surfaceVariant, colors.onSurfaceVariant)
        }
    }
    Badge(stringResource(label), container, content)
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xs),
        )
    }
}

private fun AlertFilter.label(): Int = when (this) {
    AlertFilter.ACTIVE -> R.string.alerts_tab_active
    AlertFilter.ACKNOWLEDGED -> R.string.alerts_tab_acknowledged
    AlertFilter.RESOLVED -> R.string.alerts_tab_resolved
}

private fun AlertFilter.emptyText(): Int = when (this) {
    AlertFilter.ACTIVE -> R.string.alerts_empty_active
    AlertFilter.ACKNOWLEDGED -> R.string.alerts_empty_acknowledged
    AlertFilter.RESOLVED -> R.string.alerts_empty_resolved
}
