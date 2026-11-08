package pe.edu.upc.sems.reports.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.reports.domain.ReportData
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters

/** "Vista previa" (Figma M22): the first page of the PDF, already saved in Downloads, with share and download. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportPreviewScreen(
    from: LocalDate,
    to: LocalDate,
    onBack: () -> Unit,
    viewModel: ReportPreviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(from, to) { viewModel.load(from, to) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val ready = state as? ReportPreviewUiState.Ready
    val share: () -> Unit = {
        ready?.fileUri?.let { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null))
        }
    }

    val savedText = stringResource(R.string.report_saved)
    val failedText = stringResource(R.string.report_save_failed)
    val shareText = stringResource(R.string.report_share)
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        // The notice is cleared after it was shown: clearing it first would change this effect's key and
        // cancel the snackbar before it appears. A newer event cancels this one before it gets to clear it.
        when (current.kind) {
            ReportMessage.SAVED -> {
                val result = snackbar.showSnackbar(savedText, shareText, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) share()
            }
            ReportMessage.SAVE_FAILED -> snackbar.showSnackbar(failedText)
        }
        viewModel.onMessageShown()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.report_preview_title), style = MaterialTheme.typography.titleLarge)
                    ready?.let {
                        Text(it.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                }
            },
            actions = {
                if (ready != null) {
                    IconButton(onClick = share, enabled = ready.fileUri != null) {
                        Icon(painterResource(R.drawable.ic_share), stringResource(R.string.report_share))
                    }
                    IconButton(onClick = viewModel::onDownloadAgain) {
                        Icon(painterResource(R.drawable.ic_download), stringResource(R.string.report_download_again))
                    }
                }
            },
            // The main Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val current = state) {
                ReportPreviewUiState.Loading -> LoadingScreen()
                is ReportPreviewUiState.Error -> Column(Modifier.padding(SemsSpacing.md)) {
                    FormErrorBanner(
                        error = current.error,
                        conflictTitle = R.string.form_error_generic_title,
                        conflictBody = R.string.form_error_generic_body,
                        onRetry = { viewModel.load(from, to, force = true) },
                        offerRetry = true,
                    )
                }
                is ReportPreviewUiState.Ready -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SemsSpacing.md),
                ) {
                    ReportPage(current.data)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(SemsSpacing.md))
        }
    }
}

/** First page of the PDF, drawn the way the PDF draws it. */
@Composable
private fun ReportPage(data: ReportData) {
    val pages = if (data.sites.isNotEmpty()) 2 else 1
    OutlinedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md), verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.report_pdf_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.report_page, 1, pages), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.report_subtitle, reportPeriodLabel(data.range), data.organizationName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SemsSpacing.sm)) {
                Figure(stringResource(R.string.report_kpi_consumption), Formatters.kilowattHours(data.totalKwh), Modifier.weight(1f))
                Figure(stringResource(R.string.report_kpi_cost), data.totalCostSoles?.let(Formatters::soles) ?: "—", Modifier.weight(1f))
                Figure(stringResource(R.string.report_kpi_demand), data.peakKw?.let(Formatters::kilowatts) ?: "—", Modifier.weight(1f))
            }
            HorizontalDivider()
            TableRow(
                stringResource(R.string.report_col_site),
                stringResource(R.string.report_col_kwh),
                stringResource(R.string.report_col_soles),
                header = true,
            )
            HorizontalDivider()
            data.sites.forEach { site ->
                TableRow(site.siteName, Formatters.quantity(site.kwh, 0), site.costSoles?.let(Formatters::soles) ?: "—")
            }
            HorizontalDivider()
            TableRow(
                stringResource(R.string.report_total),
                Formatters.quantity(data.totalKwh, 0),
                data.totalCostSoles?.let(Formatters::soles) ?: "—",
                bold = true,
            )
            Text(
                stringResource(R.string.report_note_cost),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!data.hasReadings) {
                Text(
                    stringResource(R.string.report_no_readings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(SemsSpacing.sm), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xxs)) {
                    val attended = pluralStringResource(R.plurals.report_alerts_attended, data.alertsAttended, data.alertsAttended)
                    val response = data.avgResponseMinutes?.let { stringResource(R.string.report_alerts_response, formatDuration(it)) }
                    Text(if (response != null) "$attended · $response" else attended, style = MaterialTheme.typography.bodySmall)
                    data.appliedSavingSoles?.let {
                        Text(
                            stringResource(R.string.report_saving, Formatters.soles(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.report_footer, data.generatedOn.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun TableRow(first: String, second: String, third: String, header: Boolean = false, bold: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val weight = if (bold) FontWeight.Bold else null
    Row(Modifier.fillMaxWidth().padding(vertical = SemsSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Text(first, style = style, color = color, fontWeight = weight, modifier = Modifier.weight(1.4f))
        Text(second, style = style, color = color, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(third, style = style, color = color, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}
