package pe.edu.upc.sems.organizations.presentation.organizationform

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.domain.model.BusinessType
import pe.edu.upc.sems.organizations.presentation.common.DropdownField
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold
import pe.edu.upc.sems.organizations.presentation.common.label
import pe.edu.upc.sems.organizations.presentation.common.message
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton

@Composable
fun EditOrganizationScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditOrganizationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    FormScaffold(title = stringResource(R.string.org_edit_title), onBack = onBack) {
        if (state.isLoadingOrganization) {
            LoadingScreen()
            return@FormScaffold
        }
        state.loadError?.let {
            FormErrorBanner(
                error = it,
                conflictTitle = R.string.form_error_generic_title,
                conflictBody = R.string.form_error_generic_body,
                onRetry = viewModel::load,
            )
            return@FormScaffold
        }
        state.error?.let {
            FormErrorBanner(
                error = it,
                conflictTitle = R.string.form_error_generic_title,
                conflictBody = R.string.form_error_generic_body,
                onRetry = submit,
            )
            Spacer(Modifier.height(SemsSpacing.md))
        }
        OutlinedTextField(
            value = state.legalName,
            onValueChange = viewModel::onLegalNameChange,
            label = { Text(stringResource(R.string.org_legal_name_label)) },
            isError = state.legalNameError != null,
            supportingText = state.legalNameError?.let { { Text(stringResource(it.message())) } },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SemsSpacing.sm))
        OutlinedTextField(
            value = state.tradeName,
            onValueChange = viewModel::onTradeNameChange,
            label = { Text(stringResource(R.string.org_trade_name_label)) },
            supportingText = { Text(stringResource(R.string.form_optional)) },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SemsSpacing.sm))
        // Same as the web app: the RUC is shown but cannot be changed.
        OutlinedTextField(
            value = state.taxId,
            onValueChange = {},
            label = { Text(stringResource(R.string.org_ruc_label)) },
            supportingText = { Text(stringResource(R.string.org_ruc_locked)) },
            singleLine = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SemsSpacing.sm))
        DropdownField(
            value = state.businessType,
            options = BusinessType.entries,
            label = R.string.org_business_type_label,
            optionLabel = { stringResource(it.label()) },
            onSelect = viewModel::onBusinessTypeChange,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SemsSpacing.lg))
        SubmitButton(label = R.string.action_save, isLoading = state.isLoading, isSlow = state.isSlow, onClick = submit)
    }
}
