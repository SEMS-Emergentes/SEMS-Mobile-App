package pe.edu.upc.sems.analytics.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
import pe.edu.upc.sems.R
import pe.edu.upc.sems.analytics.domain.model.Recommendation
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.designsystem.components.PlanLock
import pe.edu.upc.sems.shared.util.Formatters

private const val NBSP = ' '

/**
 * "Analítica": Recomendaciones and Top consumidores (Figma M17, M18, M19). Behind the plan lock: the web shows
 * the same upgrade block when the plan is below Business.
 */
@Composable
fun AnalyticsScreen(
    onUpgrade: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val snackbar = remember { SnackbarHostState() }
    val appliedText = stringResource(R.string.analytics_applied_message)
    val failedText = stringResource(R.string.analytics_action_failed)
    LaunchedEffect(state.message) {
        val text = when (state.message ?: return@LaunchedEffect) {
            AnalyticsMessage.APPLIED -> appliedText
            AnalyticsMessage.ACTION_FAILED -> failedText
        }
        // Clearing the message first would change this effect's key and cancel the snackbar before it shows.
        snackbar.showSnackbar(text)
        viewModel.onMessageShown()
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
            state.locked -> PlanLock(R.string.analytics_locked_title, R.string.analytics_locked_body, onUpgrade)
            else -> AnalyticsContent(state, viewModel)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(SemsSpacing.md))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsContent(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    Column(Modifier.fillMaxSize()) {
        val tabs = AnalyticsTab.entries
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = SemsSpacing.md, vertical = SemsSpacing.sm),
        ) {
            tabs.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = tab == state.tab,
                    onClick = { viewModel.onTabChange(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                ) {
                    Text(stringResource(if (tab == AnalyticsTab.RECOMMENDATIONS) R.string.analytics_tab_recommendations else R.string.analytics_tab_top))
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = SemsSpacing.md, end = SemsSpacing.md, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm),
        ) {
            when (state.tab) {
                AnalyticsTab.RECOMMENDATIONS -> recommendationsTab(state, viewModel)
                AnalyticsTab.TOP_CONSUMERS -> topConsumersTab(state, viewModel)
            }
        }
    }
}

// region Recomendaciones

private fun androidx.compose.foundation.lazy.LazyListScope.recommendationsTab(
    state: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
) {
    item { ForecastCard(state) }
    val recommendations = state.recommendations
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = SemsSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.analytics_recs_title), style = MaterialTheme.typography.titleMedium)
            val pending = recommendations?.count { !it.applied } ?: 0
            if (recommendations != null && pending > 0) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        pluralStringResource(R.plurals.analytics_recs_pending, pending, pending),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xxs),
                    )
                }
            }
        }
    }
    when {
        recommendations == null -> item { SectionNote(stringResource(R.string.analytics_section_error)) }
        recommendations.isEmpty() -> item {
            SectionNote(stringResource(R.string.analytics_recs_empty), stringResource(R.string.analytics_recs_empty_hint))
        }
        else -> items(recommendations, key = { it.id }) { recommendation ->
            RecommendationCard(
                recommendation = recommendation,
                busy = recommendation.id in state.busyIds,
                onApply = { viewModel.onApply(recommendation.id) },
            )
        }
    }
}

@Composable
private fun ForecastCard(state: AnalyticsUiState) {
    val forecast = state.forecast
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
            Text(
                text = if (forecast != null) {
                    stringResource(R.string.analytics_forecast_title, monthName(forecast.month))
                } else {
                    stringResource(R.string.analytics_forecast_title_none)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = forecast?.let { Formatters.soles(it.amountSoles) } ?: "—",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(
                    if (forecast != null) R.string.analytics_forecast_caption else R.string.analytics_forecast_empty,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            val saving = state.recommendations?.let(::potentialSaving)
            if (saving != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_eco),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(SemsSpacing.xs))
                    Text(
                        stringResource(R.string.analytics_saving_up_to, Formatters.soles(saving)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: Recommendation, busy: Boolean, onApply: () -> Unit) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape,
                    color = if (recommendation.applied) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Icon(
                        painterResource(if (recommendation.applied) R.drawable.ic_check_circle_filled else R.drawable.ic_lightbulb),
                        contentDescription = null,
                        modifier = Modifier.padding(SemsSpacing.xs).size(24.dp),
                    )
                }
                Spacer(Modifier.width(SemsSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(recommendation.title, style = MaterialTheme.typography.titleMedium)
                    if (recommendation.detail.isNotBlank()) {
                        Text(
                            recommendation.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = SemsSpacing.xxs),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = SemsSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_eco),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(SemsSpacing.xxs))
                    Text(
                        stringResource(R.string.analytics_saving_per_month, Formatters.soles(recommendation.estimatedSavingSoles)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (recommendation.applied) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            stringResource(R.string.analytics_applied),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xxs),
                        )
                    }
                } else {
                    FilledTonalButton(onClick = onApply, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(SemsSpacing.xs))
                        }
                        Text(stringResource(R.string.analytics_apply))
                    }
                }
            }
        }
    }
}

// endregion

// region Top consumidores

private fun androidx.compose.foundation.lazy.LazyListScope.topConsumersTab(
    state: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
) {
    item { PeriodHeader(state, viewModel) }
    item { ComparisonCard(state) }
    item {
        Text(
            stringResource(R.string.analytics_ranking_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = SemsSpacing.xs),
        )
    }
    val ranking = state.ranking
    when {
        ranking == null -> item { SectionNote(stringResource(R.string.analytics_section_error)) }
        ranking.isEmpty() -> item {
            SectionNote(stringResource(R.string.analytics_ranking_empty), stringResource(R.string.analytics_ranking_empty_hint))
        }
        else -> item { RankingCard(ranking) }
    }
}

@Composable
private fun PeriodHeader(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val sites = state.siteRows?.size
        Text(
            text = if (sites != null) {
                stringResource(
                    R.string.analytics_period_summary,
                    stringResource(state.period.label()),
                    pluralStringResource(R.plurals.analytics_sites_count, sites, sites),
                )
            } else {
                stringResource(state.period.label())
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { menuOpen = true }) { Text(stringResource(R.string.analytics_filter)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ComparisonPeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(stringResource(period.label())) },
                        onClick = {
                            menuOpen = false
                            viewModel.onPeriodChange(period)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(state: AnalyticsUiState) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
            Text(stringResource(R.string.analytics_compare_title), style = MaterialTheme.typography.titleMedium)
            val rows = state.siteRows
            when {
                state.comparisonLoading && rows == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                rows == null -> Text(
                    stringResource(R.string.analytics_section_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // As in the web: comparing needs at least two sites.
                rows.size < 2 -> {
                    Text(stringResource(R.string.analytics_compare_need_two), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.analytics_compare_need_two_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    rows.forEach { row -> SiteRowItem(row) }
                    Text(
                        stringResource(R.string.analytics_compare_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteRowItem(row: SiteRow) {
    Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.xxs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name ?: stringResource(R.string.analytics_ranking_unnamed),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SemsSpacing.xs))
            Text(
                text = row.energyCostSoles?.let(Formatters::soles) ?: Formatters.kilowattHours(row.kwh),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        LinearProgressIndicator(
            progress = { row.barFraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                Formatters.kilowattHours(row.kwh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.changePercent?.let { change ->
                val (container, content) = when {
                    change.roundToInt() > 0 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                    change.roundToInt() < 0 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(shape = CircleShape, color = container, contentColor = content) {
                    Text(
                        stringResource(R.string.analytics_change_vs_previous, signedPercent(change)),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xxs),
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingCard(ranking: List<RankingRow>) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column {
            ranking.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = SemsSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(SemsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (row.rank == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            row.rank.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xs),
                        )
                    }
                    Spacer(Modifier.width(SemsSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.name.ifBlank { stringResource(R.string.analytics_ranking_unnamed) },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        row.siteName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Formatters.soles(row.amountSoles), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (row.percentOfTotal > 0.0) {
                                "${Formatters.quantity(row.percentOfTotal, 0)}$NBSP%"
                            } else {
                                Formatters.kilowattHours(row.kwh)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// endregion

@Composable
private fun SectionNote(message: String, hint: String? = null) {
    Column(Modifier.padding(vertical = SemsSpacing.xs)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ComparisonPeriod.label(): Int = when (this) {
    ComparisonPeriod.WEEK -> R.string.analytics_period_week
    ComparisonPeriod.MONTH -> R.string.analytics_period_month
    ComparisonPeriod.QUARTER -> R.string.analytics_period_quarter
}

/** "Octubre" / "October" for month 10, in the language the app is in. */
private fun monthName(month: Int): String =
    pe.edu.upc.sems.reports.domain.monthNames()[month.coerceIn(1, 12) - 1].replaceFirstChar { it.uppercase() }

/** `+3 %`, `−5 %`, `0 %`: whole percent with its sign. */
internal fun signedPercent(value: Double): String {
    val rounded = value.roundToInt()
    val sign = when {
        rounded > 0 -> "+"
        rounded < 0 -> "−"
        else -> ""
    }
    return "$sign${abs(rounded)}$NBSP%"
}
