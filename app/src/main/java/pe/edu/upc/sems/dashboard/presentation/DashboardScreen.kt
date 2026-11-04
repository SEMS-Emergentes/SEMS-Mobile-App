package pe.edu.upc.sems.dashboard.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.gettingstarted.GettingStartedCard
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters

@Composable
fun DashboardScreen(
    onRegisterCompany: () -> Unit,
    onAddFirstSite: (organizationId: String) -> Unit,
    onOpenMonitoring: () -> Unit,
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
            is DashboardUiState.Success -> DashboardBody(value.content, viewModel::refresh, onOpenMonitoring)
        }
    }
}

@Composable
private fun DashboardBody(content: DashboardContent, onRetry: () -> Unit, onOpenMonitoring: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(SemsSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SemsSpacing.md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.xxs)) {
                Text(stringResource(R.string.dashboard_greeting, content.siteName), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.dashboard_period_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content.error?.let { error ->
            item {
                FormErrorBanner(
                    error, R.string.form_error_generic_title, R.string.form_error_generic_body,
                    onRetry, offerRetry = true,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                SummaryCard(
                    stringResource(R.string.dashboard_consumption),
                    content.aggregation?.let { Formatters.kilowattHours(it.totalKwh) }
                        ?: stringResource(R.string.value_unavailable),
                    R.drawable.ic_bolt,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    Modifier.weight(1f),
                )
                SummaryCard(
                    stringResource(R.string.dashboard_backend_cost),
                    content.backendCostSoles?.let(Formatters::soles) ?: stringResource(R.string.value_unavailable),
                    R.drawable.ic_credit_card,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                SummaryCard(
                    stringResource(R.string.dashboard_meters),
                    content.meterCount?.toString() ?: stringResource(R.string.value_unavailable),
                    R.drawable.ic_monitoring,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    Modifier.weight(1f),
                )
                SummaryCard(
                    stringResource(R.string.dashboard_demand),
                    stringResource(R.string.value_unavailable),
                    R.drawable.ic_analytics,
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Text(
                stringResource(R.string.dashboard_demand_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (content.meterCount == 0) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(Modifier.padding(SemsSpacing.lg), verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                        Text(stringResource(R.string.dashboard_no_meters_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.dashboard_no_meters_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenMonitoring) {
                            Text(stringResource(R.string.dashboard_open_monitoring))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    icon: Int,
    iconBackground: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
            Surface(shape = RoundedCornerShape(12.dp), color = iconBackground) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.padding(SemsSpacing.sm).size(22.dp),
                )
            }
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
