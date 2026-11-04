package pe.edu.upc.sems.energy.presentation.meters

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.presentation.common.DropdownField
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton

@Composable
fun MeterLinkScreen(serial: String, model: String?, onBack: () -> Unit, onSaved: () -> Unit, viewModel: MeterLinkViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(serial, model) { viewModel.load(if (serial.isBlank()) null else QrMeterPayload(serial, model)) }
    LaunchedEffect(state.done) { if (state.done) onSaved() }
    FormScaffold(stringResource(R.string.meter_link_title), onBack) {
        if (state.loading) { LoadingScreen(); return@FormScaffold }
        state.loadError?.let { FormErrorBanner(it, R.string.form_error_generic_title, R.string.form_error_generic_body, { viewModel.load() }, offerRetry = true); return@FormScaffold }
        Text(stringResource(R.string.meter_link_intro))
        Spacer(Modifier.height(SemsSpacing.md))
        OutlinedTextField(state.serial, viewModel::onSerial, label = { Text(stringResource(R.string.meter_serial_label)) }, singleLine = true, isError = state.serialInvalid, supportingText = if (state.serialInvalid) {{ Text(stringResource(R.string.meter_invalid_serial)) }} else null, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(SemsSpacing.sm))
        OutlinedTextField(state.model, viewModel::onModel, label = { Text(stringResource(R.string.meter_model_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (state.sites.isNotEmpty()) {
            Spacer(Modifier.height(SemsSpacing.sm))
            val site = state.sites.firstOrNull { it.id == state.selectedSiteId } ?: state.sites.first()
            DropdownField(site, state.sites, R.string.meter_site_label, { it.name }, { viewModel.selectSite(it.id) })
        }
        if (state.sites.isEmpty()) Text(stringResource(R.string.meter_no_sites))
        Spacer(Modifier.height(SemsSpacing.sm))
        val zone = state.zones.firstOrNull { it.id == state.selectedZoneId }
        DropdownField(zone, listOf(null) + state.zones, R.string.meter_zone_label, { it?.name ?: stringResource(R.string.meter_no_zone_option) }, { viewModel.selectZone(it?.id) }, enabled = !state.loadingZones)
        state.zoneError?.let { FormErrorBanner(it, R.string.form_error_generic_title, R.string.form_error_generic_body, viewModel::retryZones, offerRetry = true) }
        state.submitError?.let {
            Spacer(Modifier.height(SemsSpacing.sm))
            FormErrorBanner(it, R.string.meter_link_error_title, R.string.meter_link_error_body, viewModel::submit, offerRetry = true)
        }
        Spacer(Modifier.height(SemsSpacing.lg))
        SubmitButton(R.string.meter_link_submit, state.saving, false, viewModel::submit)
    }
}
