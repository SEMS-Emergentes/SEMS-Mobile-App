package pe.edu.upc.sems.organizations.presentation.siteform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.domain.model.TariffCategory
import pe.edu.upc.sems.organizations.presentation.common.DropdownField
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.common.description
import pe.edu.upc.sems.organizations.presentation.common.message
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton

/** "Nuevo local" / "Editar local" (web: SiteModal.vue). */
@Composable
fun SiteFormScreen(
    mode: SiteFormMode,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SiteFormViewModel = hiltViewModel(),
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

    val title = stringResource(if (state.isEdit) R.string.site_edit_title else R.string.site_new_title)
    if (state.isLoadingSite) {
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
            )
        }
        return
    }

    FormScaffold(title = title, onBack = onBack) {
        Text(
            text = stringResource(R.string.getting_started_site_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SemsSpacing.lg))
        state.error?.let { error ->
            FormErrorBanner(
                error = error,
                conflictTitle = R.string.site_error_code_taken_title,
                conflictBody = R.string.site_error_code_taken_body,
                onRetry = submit,
                planLimit = state.planLimit,
            )
            Spacer(Modifier.height(SemsSpacing.md))
        }
        FormTextField(
            value = state.siteCode,
            onValueChange = viewModel::onSiteCodeChange,
            label = R.string.site_code_label,
            placeholder = R.string.site_code_placeholder,
            error = state.siteCodeError,
            hint = R.string.site_code_hint,
            // The backend does not allow changing it.
            enabled = !state.isLoading && !state.isEdit,
            capitalization = KeyboardCapitalization.Characters,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        FormTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = R.string.site_name_label,
            placeholder = R.string.site_name_placeholder,
            error = state.nameError,
            enabled = !state.isLoading,
            capitalization = KeyboardCapitalization.Words,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        FormTextField(
            value = state.address,
            onValueChange = viewModel::onAddressChange,
            label = R.string.site_address_label,
            placeholder = R.string.site_address_placeholder,
            hint = R.string.form_optional,
            enabled = !state.isLoading,
            capitalization = KeyboardCapitalization.Sentences,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        FormTextField(
            value = state.district,
            onValueChange = viewModel::onDistrictChange,
            label = R.string.site_district_label,
            hint = R.string.form_optional,
            enabled = !state.isLoading,
            capitalization = KeyboardCapitalization.Words,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        FormTextField(
            value = state.floorAreaM2,
            onValueChange = viewModel::onFloorAreaChange,
            label = R.string.site_floor_area_label,
            placeholder = R.string.site_floor_area_placeholder,
            error = state.floorAreaError,
            hint = R.string.form_optional,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Decimal,
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        )
        FormTextField(
            value = state.contractedPowerKw,
            onValueChange = viewModel::onContractedPowerChange,
            label = R.string.site_contracted_power_label,
            placeholder = R.string.site_contracted_power_placeholder,
            error = state.contractedPowerError,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Decimal,
            onNext = { focusManager.clearFocus() },
        )
        DropdownField(
            value = state.tariffCategory,
            options = TariffCategory.selectable,
            label = R.string.site_tariff_label,
            optionLabel = { stringResource(it.description()) },
            onSelect = viewModel::onTariffCategoryChange,
            enabled = !state.isLoading,
            supportingText = { Text(stringResource(R.string.site_tariff_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SemsSpacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.excludesSundaysFromPeak,
                    enabled = !state.isLoading,
                    role = Role.Switch,
                    onValueChange = viewModel::onExcludesSundaysChange,
                )
                .padding(vertical = SemsSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.site_excludes_sundays_label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.site_excludes_sundays_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(SemsSpacing.md))
            // Handled by the row, so the whole line is tappable.
            Switch(checked = state.excludesSundaysFromPeak, onCheckedChange = null, enabled = !state.isLoading)
        }
        Spacer(Modifier.height(SemsSpacing.lg))
        SubmitButton(
            label = if (state.isEdit) R.string.action_save else R.string.site_create_submit,
            isLoading = state.isLoading,
            isSlow = state.isSlow,
            onClick = submit,
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
    keyboardType: KeyboardType = KeyboardType.Text,
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
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SemsSpacing.xs),
    )
}
