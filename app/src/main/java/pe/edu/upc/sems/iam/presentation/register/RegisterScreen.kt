package pe.edu.upc.sems.iam.presentation.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.iam.presentation.common.AuthErrorBanner
import pe.edu.upc.sems.iam.presentation.common.AuthSubmitButton
import pe.edu.upc.sems.iam.presentation.common.PasswordField
import pe.edu.upc.sems.iam.presentation.common.message
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.SemsTheme

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RegisterContent(
        state = state,
        onFullNameChange = viewModel::onFullNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTermsAcceptedChange = viewModel::onTermsAcceptedChange,
        onSubmit = viewModel::onSubmit,
        onBack = onBack,
        onLogin = onLogin,
    )
}

/** Stateless M02 layout (the RUC field is asked later, in "Primeros pasos"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterContent(
    state: RegisterUiState,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            // The access graph's Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.register_title), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                }
            },
        )
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SemsSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.register_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SemsSpacing.lg))

            state.error?.let { error ->
                RegisterErrorBanner(error = error, onRetry = submit, onLogin = onLogin)
                Spacer(Modifier.height(SemsSpacing.md))
            }

            OutlinedTextField(
                value = state.fullName,
                onValueChange = onFullNameChange,
                label = { Text(stringResource(R.string.register_full_name_label)) },
                isError = state.fullNameError != null,
                supportingText = state.fullNameError?.let { { Text(stringResource(it.message())) } },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.PersonFullName },
            )
            Spacer(Modifier.height(SemsSpacing.sm))
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.login_email_label)) },
                isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(stringResource(it.message())) } },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.EmailAddress },
            )
            Spacer(Modifier.height(SemsSpacing.sm))
            PasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = R.string.login_password_label,
                isError = state.passwordError != null,
                // Always show the rule, as in M02; the error replaces it when it fails.
                supportingText = {
                    Text(stringResource(state.passwordError?.message() ?: R.string.register_password_hint))
                },
                enabled = !state.isLoading,
                contentType = ContentType.NewPassword,
                onImeAction = { focusManager.clearFocus() },
            )
            Spacer(Modifier.height(SemsSpacing.sm))
            TermsCheckbox(
                checked = state.termsAccepted,
                onCheckedChange = onTermsAcceptedChange,
                enabled = !state.isLoading,
                isError = state.termsError != null,
            )
            state.termsError?.let {
                Text(
                    text = stringResource(it.message()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = SemsSpacing.md),
                )
            }
            Spacer(Modifier.height(SemsSpacing.md))
            AuthSubmitButton(
                label = R.string.register_submit,
                isLoading = state.isLoading,
                isSlow = state.isSlow,
                onClick = submit,
            )
            Spacer(Modifier.height(SemsSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.register_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onLogin, enabled = !state.isLoading) {
                    Text(stringResource(R.string.register_login))
                }
            }
            Spacer(Modifier.height(SemsSpacing.lg))
        }
    }
}

@Composable
private fun TermsCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    isError: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            // Handled by the row, so the whole line is tappable.
            onCheckedChange = null,
            enabled = enabled,
            colors = if (isError) {
                CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.error)
            } else {
                CheckboxDefaults.colors()
            },
            modifier = Modifier.padding(SemsSpacing.sm),
        )
        Text(
            text = stringResource(R.string.register_terms),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RegisterErrorBanner(error: RegisterError, onRetry: () -> Unit, onLogin: () -> Unit) {
    when (error) {
        RegisterError.EMAIL_TAKEN -> AuthErrorBanner(
            title = R.string.register_error_email_taken_title,
            body = R.string.register_error_email_taken_body,
            actionLabel = R.string.register_login,
            onAction = onLogin,
        )
        RegisterError.NO_CONNECTION ->
            AuthErrorBanner(R.string.login_error_no_connection_title, R.string.login_error_no_connection_body)
        RegisterError.TIMEOUT -> AuthErrorBanner(
            title = R.string.login_error_timeout_title,
            body = R.string.login_error_timeout_body,
            actionLabel = R.string.action_retry,
            onAction = onRetry,
        )
        RegisterError.GENERIC ->
            AuthErrorBanner(R.string.register_error_generic_title, R.string.login_error_generic_body)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun RegisterPreview() {
    SemsTheme {
        Surface {
            RegisterContent(
                RegisterUiState(fullName = "Valeria Rojas", email = "valeria@example.com", termsAccepted = true),
                {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
