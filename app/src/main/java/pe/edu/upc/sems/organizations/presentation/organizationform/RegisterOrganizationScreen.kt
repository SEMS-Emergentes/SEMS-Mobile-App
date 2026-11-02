package pe.edu.upc.sems.organizations.presentation.organizationform

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton

/** "Registra tu organización" (web: SitesView.vue, first step of "Primeros pasos"). */
@Composable
fun RegisterOrganizationScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: RegisterOrganizationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.onDoneHandled()
            onRegistered()
        }
    }
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.onSubmit()
    }
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    FormScaffold(title = stringResource(R.string.org_register_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.getting_started_company_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SemsSpacing.lg))
        state.error?.let { error ->
            FormErrorBanner(
                error = error,
                conflictTitle = R.string.org_error_ruc_taken_title,
                conflictBody = R.string.org_error_ruc_taken_body,
                onRetry = submit,
            )
            Spacer(Modifier.height(SemsSpacing.md))
        }
        OutlinedTextField(
            value = state.legalName,
            onValueChange = viewModel::onLegalNameChange,
            label = { Text(stringResource(R.string.org_legal_name_label)) },
            placeholder = { Text(stringResource(R.string.org_legal_name_placeholder)) },
            isError = state.legalNameError != null,
            supportingText = state.legalNameError?.let { { Text(stringResource(it.message())) } },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = next,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SemsSpacing.sm))
        OutlinedTextField(
            value = state.tradeName,
            onValueChange = viewModel::onTradeNameChange,
            label = { Text(stringResource(R.string.org_trade_name_label)) },
            placeholder = { Text(stringResource(R.string.org_trade_name_placeholder)) },
            supportingText = { Text(stringResource(R.string.form_optional)) },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = next,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SemsSpacing.sm))
        OutlinedTextField(
            value = state.taxId,
            onValueChange = viewModel::onTaxIdChange,
            label = { Text(stringResource(R.string.org_ruc_label)) },
            isError = state.taxIdError != null,
            // The rule is always visible, as in the web app; it turns red when it fails.
            supportingText = {
                Text(stringResource(state.taxIdError?.message() ?: R.string.org_ruc_hint))
            },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
        SubmitButton(
            label = R.string.org_register_submit,
            isLoading = state.isLoading,
            isSlow = state.isSlow,
            onClick = submit,
        )
    }
}
