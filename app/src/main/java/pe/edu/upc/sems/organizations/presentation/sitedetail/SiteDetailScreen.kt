package pe.edu.upc.sems.organizations.presentation.sitedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.domain.model.Zone
import pe.edu.upc.sems.organizations.domain.model.ZoneType
import pe.edu.upc.sems.organizations.presentation.common.DropdownField
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.label
import pe.edu.upc.sems.organizations.presentation.common.message
import pe.edu.upc.sems.organizations.presentation.sites.InfoChip
import pe.edu.upc.sems.organizations.presentation.sites.siteLocation
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton
import pe.edu.upc.sems.shared.util.Formatters

@Composable
fun SiteDetailScreen(
    siteId: String,
    onBack: () -> Unit,
    onEdit: (siteId: String) -> Unit,
    viewModel: SiteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(siteId) { viewModel.start(siteId) }
    // Reload after "Editar local".
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.archived) {
        if (state.archived) {
            viewModel.onArchivedHandled()
            onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(site = state.site, canEdit = state.canEdit, onBack = onBack, onEdit = { onEdit(siteId) })
        val site = state.site
        when {
            site != null -> SiteDetailContent(state, site, viewModel)
            state.loadError != null -> Column(Modifier.padding(SemsSpacing.md)) {
                FormErrorBanner(
                    error = state.loadError!!,
                    conflictTitle = R.string.form_error_generic_title,
                    conflictBody = R.string.form_error_generic_body,
                    onRetry = viewModel::refresh,
                    offerRetry = true,
                )
            }
            else -> LoadingScreen()
        }
    }

    state.zoneEditor?.let { editor ->
        ZoneEditorSheet(
            editor = editor,
            onNameChange = viewModel::onZoneNameChange,
            onTypeChange = viewModel::onZoneTypeChange,
            onOffHoursChange = viewModel::onZoneOffHoursChange,
            onSave = viewModel::onSaveZone,
            onArchive = viewModel::onArchiveZone,
            onDismiss = viewModel::onZoneEditorDismiss,
        )
    }
    if (state.confirmingArchive) {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveSiteDismiss,
            title = { Text(stringResource(R.string.site_archive_title)) },
            text = { Text(stringResource(R.string.site_archive_message, state.site?.name.orEmpty())) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::onArchiveSiteConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.site_archive_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onArchiveSiteDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(site: Site?, canEdit: Boolean, onBack: () -> Unit, onEdit: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(site?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                site?.let(::siteLocation)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
            }
        },
        actions = {
            if (canEdit && site != null) {
                IconButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.site_edit_title))
                }
            }
        },
        // The main Scaffold already pads for the status bar.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SiteDetailContent(state: SiteDetailUiState, site: Site, viewModel: SiteDetailViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SemsSpacing.md),
    ) {
        // Supply data (M28 "Suministro").
        OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(SemsSpacing.md)) {
                Text(stringResource(R.string.site_supply_title), style = MaterialTheme.typography.titleMedium)
                SupplyRow(R.string.site_contracted_power_row, Formatters.kilowatts(site.contractedPowerKw))
                SupplyRow(R.string.site_tariff_row, site.tariffCategory.name)
                // Comes from the backend (charges_for_demand): never computed in the app.
                SupplyRow(
                    R.string.site_power_charge_row,
                    stringResource(if (site.chargesForDemand) R.string.answer_yes else R.string.answer_no),
                )
                SupplyRow(
                    R.string.site_floor_area_row,
                    site.floorAreaM2?.let(Formatters::squareMeters) ?: stringResource(R.string.value_not_set),
                )
                SupplyRow(
                    R.string.site_sundays_row,
                    stringResource(if (site.excludesSundaysFromPeak) R.string.site_sundays_excluded else R.string.site_sundays_included),
                )
                SupplyRow(R.string.site_code_label, site.siteCode, showDivider = false)
            }
        }

        // Zones.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SemsSpacing.lg, bottom = SemsSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.site_zones_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.canEdit) {
                TextButton(onClick = viewModel::onAddZone) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(SemsSpacing.xs))
                    Text(stringResource(R.string.site_add_zone))
                }
            }
        }
        OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            if (state.zones.isEmpty()) {
                Text(
                    text = stringResource(R.string.site_zones_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(SemsSpacing.md),
                )
            }
            state.zones.forEachIndexed { index, zone ->
                ZoneRow(zone = zone, clickable = state.canEdit, onClick = { viewModel.onZoneClick(zone) })
                if (index < state.zones.lastIndex) HorizontalDivider(Modifier.padding(horizontal = SemsSpacing.md))
            }
        }

        // Archive the site.
        if (state.canEdit) {
            Spacer(Modifier.height(SemsSpacing.lg))
            state.archiveError?.let {
                FormErrorBanner(
                    error = it,
                    conflictTitle = R.string.form_error_generic_title,
                    conflictBody = R.string.form_error_generic_body,
                    onRetry = viewModel::onArchiveSiteConfirm,
                )
                Spacer(Modifier.height(SemsSpacing.sm))
            }
            OutlinedButton(
                onClick = viewModel::onArchiveSiteRequest,
                enabled = !state.isArchiving,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.site_archive_title)) }
        }
        Spacer(Modifier.height(SemsSpacing.lg))
    }
}

@Composable
private fun SupplyRow(label: Int, value: String, showDivider: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SemsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
    if (showDivider) HorizontalDivider()
}

@Composable
private fun ZoneRow(zone: Zone, clickable: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(SemsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(zone.name, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(zone.zoneType.label()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (zone.operatesOffHours) InfoChip(stringResource(R.string.zone_off_hours_badge), highlighted = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneEditorSheet(
    editor: ZoneEditor,
    onNameChange: (String) -> Unit,
    onTypeChange: (ZoneType) -> Unit,
    onOffHoursChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = SemsSpacing.lg)
                .padding(bottom = SemsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm),
        ) {
            Text(
                stringResource(if (editor.zoneId == null) R.string.zone_new_title else R.string.zone_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            editor.error?.let {
                FormErrorBanner(
                    error = it,
                    conflictTitle = R.string.zone_error_name_taken_title,
                    conflictBody = R.string.zone_error_name_taken_body,
                    onRetry = onSave,
                )
            }
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.zone_name_label)) },
                placeholder = { Text(stringResource(R.string.zone_name_placeholder)) },
                isError = editor.nameError != null,
                supportingText = editor.nameError?.let { { Text(stringResource(it.message())) } },
                singleLine = true,
                enabled = !editor.isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                value = editor.zoneType,
                options = ZoneType.entries,
                label = R.string.zone_type_label,
                optionLabel = { stringResource(it.label()) },
                onSelect = onTypeChange,
                enabled = !editor.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = editor.operatesOffHours,
                        enabled = !editor.isSaving,
                        role = Role.Switch,
                        onValueChange = onOffHoursChange,
                    )
                    .padding(vertical = SemsSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.zone_off_hours_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = editor.operatesOffHours, onCheckedChange = null, enabled = !editor.isSaving)
            }
            SubmitButton(
                label = if (editor.zoneId == null) R.string.site_add_zone else R.string.action_save,
                isLoading = editor.isSaving,
                isSlow = false,
                onClick = onSave,
            )
            if (editor.zoneId != null) {
                TextButton(
                    onClick = onArchive,
                    enabled = !editor.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(stringResource(R.string.zone_archive)) }
            }
        }
    }
}
