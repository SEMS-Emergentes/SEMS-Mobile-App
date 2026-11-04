package pe.edu.upc.sems.devices.presentation.deviceform

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.devices.domain.model.ConnectionProtocol
import pe.edu.upc.sems.organizations.presentation.common.DropdownField
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.common.message
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton

/** "Agregar equipo" / "Editar equipo" (web: DeviceModal.vue). Same look as the site form. */
@Composable
fun DeviceFormScreen(
    mode: DeviceFormMode,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: DeviceFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(mode) { viewModel.start(mode) }
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.onDoneHandled()
            onSaved()
        }
    }
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.onSubmit()
    }

    val title = stringResource(if (state.isEdit) R.string.device_edit_title else R.string.device_new_title)
    if (state.isLoadingForm) {
        FormScaffold(title = title, onBack = onBack) { LoadingScreen() }
        return
    }
    state.loadError?.let { error ->
        FormScaffold(title = title, onBack = onBack) {
            FormErrorBanner(
                error = error,
                conflictTitle = R.string.form_error_generic_title,
                conflictBody = R.string.form_error_generic_body,
                onRetry = viewModel::retryLoad,
                offerRetry = true,
            )
        }
        return
    }
    if (state.hasNoSites) {
        FormScaffold(title = title, onBack = onBack) {
            Text(
                text = stringResource(R.string.device_no_sites),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    FormScaffold(title = title, onBack = onBack) {
        Text(
            text = stringResource(R.string.device_form_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SemsSpacing.lg))
        state.error?.let { error ->
            FormErrorBanner(
                error = error,
                conflictTitle = R.string.device_error_code_taken_title,
                conflictBody = R.string.device_error_code_taken_body,
                onRetry = submit,
            )
            Spacer(Modifier.height(SemsSpacing.md))
        }
        FormTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = R.string.device_name_label,
            placeholder = R.string.device_name_placeholder,
            error = state.nameError,
            enabled = !state.isLoading,
            capitalization = KeyboardCapitalization.Sentences,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        DropdownField(
            value = state.siteId.orEmpty(),
            options = state.sites.map { it.id },
            label = R.string.device_site_label,
            optionLabel = { id -> state.sites.firstOrNull { it.id == id }?.name.orEmpty() },
            onSelect = viewModel::onSiteChange,
            // A device never changes site: the backend does not allow it.
            enabled = !state.isLoading && !state.isEdit,
            supportingText = if (state.isEdit) {
                { Text(stringResource(R.string.device_site_locked)) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SemsSpacing.xs),
        )
        DropdownField(
            value = state.zoneId,
            options = listOf<String?>(null) + state.zones.map { it.id },
            label = R.string.device_zone_label,
            optionLabel = { id ->
                if (id == null) {
                    stringResource(if (state.zones.isEmpty()) R.string.device_zone_none_available else R.string.device_zone_unassigned)
                } else {
                    state.zones.firstOrNull { it.id == id }?.name.orEmpty()
                }
            },
            onSelect = viewModel::onZoneChange,
            enabled = !state.isLoading && state.zones.isNotEmpty(),
            supportingText = { Text(stringResource(R.string.form_optional)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SemsSpacing.xs),
        )
        DropdownField(
            value = state.type,
            options = state.typeOptions,
            label = R.string.device_type_label,
            optionLabel = { deviceTypeLabel(it) },
            onSelect = viewModel::onTypeChange,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SemsSpacing.xs),
        )
        DropdownField(
            value = state.protocol,
            options = ConnectionProtocol.entries,
            label = R.string.device_connection_label,
            optionLabel = { stringResource(it.label()) },
            onSelect = viewModel::onProtocolChange,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SemsSpacing.xs),
        )
        FormTextField(
            value = state.brand,
            onValueChange = viewModel::onBrandChange,
            label = R.string.device_brand_label,
            placeholder = R.string.device_brand_placeholder,
            hint = R.string.form_optional,
            enabled = !state.isLoading,
            capitalization = KeyboardCapitalization.Words,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        FormTextField(
            value = state.model,
            onValueChange = viewModel::onModelChange,
            label = R.string.device_model_label,
            placeholder = R.string.device_model_placeholder,
            hint = R.string.form_optional,
            enabled = !state.isLoading,
            capitalization = KeyboardCapitalization.Characters,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        FormTextField(
            value = state.externalCode,
            onValueChange = viewModel::onExternalCodeChange,
            label = R.string.device_code_label,
            placeholder = R.string.device_code_placeholder,
            error = state.externalCodeError,
            hint = if (state.isEdit) R.string.device_code_locked else R.string.device_code_hint,
            // The backend does not allow changing it.
            enabled = !state.isLoading && !state.isEdit,
            capitalization = KeyboardCapitalization.Characters,
            onNext = { focusManager.clearFocus() },
        )
        Spacer(Modifier.height(SemsSpacing.lg))
        SubmitButton(
            label = if (state.isEdit) R.string.action_save else R.string.device_create_submit,
            isLoading = state.isLoading,
            isSlow = state.isSlow,
            onClick = submit,
        )
        if (state.isEdit) {
            Spacer(Modifier.height(SemsSpacing.sm))
            OutlinedButton(
                onClick = viewModel::onDeleteRequest,
                enabled = !state.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { Text(stringResource(R.string.device_delete)) }
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismiss,
            title = { Text(stringResource(R.string.device_delete)) },
            text = { Text(stringResource(R.string.device_delete_message, state.name)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::onDeleteConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.device_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    enabled: Boolean,
    onNext: () -> Unit,
    placeholder: Int? = null,
    error: OrgFieldError? = null,
    hint: Int? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    val supporting = error?.message() ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(stringResource(it)) } },
        isError = error != null,
        supportingText = supporting?.let { { Text(stringResource(it)) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SemsSpacing.xs),
    )
}
