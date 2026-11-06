package pe.edu.upc.sems.reports.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.reports.domain.ReportPreset
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.designsystem.components.PlanLock

/** "Reportes PDF": pick the period and download the monthly report (Figma M20, M21). Behind the plan lock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onUpgrade: () -> Unit,
    onDownload: (from: LocalDate, to: LocalDate) -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

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
        state.locked -> PlanLock(R.string.reports_locked_title, R.string.reports_locked_body, onUpgrade)
        else -> ReportsContent(state, viewModel, onDownload)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsContent(
    state: ReportsUiState,
    viewModel: ReportsViewModel,
    onDownload: (LocalDate, LocalDate) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pickingDates by remember { mutableStateOf(false) }
    val range = state.range

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SemsSpacing.md)
                .padding(top = SemsSpacing.md, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SemsSpacing.md),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    painterResource(R.drawable.ic_description),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(SemsSpacing.md).size(32.dp),
                )
            }
            Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.reports_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    value = presetLabel(state.preset, state),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.reports_period_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ReportPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(presetLabel(preset, state)) },
                            trailingIcon = if (preset == state.preset) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(20.dp)) }
                            } else {
                                null
                            },
                            onClick = {
                                menuOpen = false
                                if (preset == ReportPreset.CUSTOM) pickingDates = true else viewModel.onPresetChange(preset)
                            },
                        )
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                    Text(stringResource(R.string.reports_includes_title), style = MaterialTheme.typography.titleSmall)
                    listOf(
                        R.string.reports_includes_consumption,
                        R.string.reports_includes_demand,
                        R.string.reports_includes_alerts,
                        R.string.reports_includes_savings,
                    ).forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(SemsSpacing.sm))
                            Text(stringResource(item), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Button(
            onClick = { range?.let { onDownload(it.from, it.to) } },
            enabled = range != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(SemsSpacing.md),
        ) {
            Icon(painterResource(R.drawable.ic_download), contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(SemsSpacing.xs))
            Text(stringResource(R.string.reports_download))
        }
    }

    if (pickingDates) {
        val today = state.today
        val pickerState = rememberDateRangePickerState(
            selectableDates = object : SelectableDates {
                // The backend has nothing to report about days that have not happened.
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toLocalDate() <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDates = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) viewModel.onCustomRange(start.toLocalDate(), end.toLocalDate())
                        pickingDates = false
                    },
                ) { Text(stringResource(R.string.reports_dates_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pickingDates = false }) { Text(stringResource(R.string.reports_dates_cancel)) } },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text(stringResource(R.string.reports_dates_title), Modifier.padding(SemsSpacing.md)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The date pickers speak in milliseconds at UTC midnight. */
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun presetLabel(preset: ReportPreset, state: ReportsUiState): String = when (preset) {
    ReportPreset.THIS_MONTH -> stringResource(R.string.reports_period_this_month, monthYearLower(state.today))
    ReportPreset.LAST_MONTH -> stringResource(R.string.reports_period_last_month, monthYearLower(state.today.minusMonths(1)))
    ReportPreset.LAST_THREE_MONTHS -> stringResource(R.string.reports_period_last_three)
    ReportPreset.CUSTOM ->
        state.custom?.let(::reportPeriodLabel) ?: stringResource(R.string.reports_period_custom)
}
