package pe.edu.upc.sems.iam.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.BuildConfig
import pe.edu.upc.sems.R
import pe.edu.upc.sems.iam.presentation.common.AuthErrorBanner
import pe.edu.upc.sems.iam.presentation.common.AuthSubmitButton
import pe.edu.upc.sems.iam.presentation.common.PasswordField
import pe.edu.upc.sems.iam.presentation.common.message
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.SemsTheme
import pe.edu.upc.sems.shared.designsystem.components.SemsLogoMark

@Composable
fun LoginScreen(
    onForgotPassword: (email: String) -> Unit,
    onCreateAccount: () -> Unit,
    onVerifyAccount: (email: String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        onForgotPassword = { onForgotPassword(state.email) },
        onCreateAccount = onCreateAccount,
        onVerifyAccount = { onVerifyAccount(state.email) },
    )
}

/** Stateless M00/M01 layout. Google sign-in is hidden: /auth/google is not active in the deploy. */
@Composable
fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onVerifyAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }

    // BoxWithConstraints + heightIn(min) keeps the footer at the bottom while the form can still scroll.
    BoxWithConstraints(modifier = modifier.imePadding()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = SemsSpacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Spacer(Modifier.height(SemsSpacing.xl))
                BrandHeader()
                Spacer(Modifier.height(SemsSpacing.lg))
                Text(
                    text = stringResource(R.string.login_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(SemsSpacing.xs))
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SemsSpacing.lg))

                state.error?.let { error ->
                    LoginErrorBanner(error = error, onRetry = submit, onVerifyAccount = onVerifyAccount)
                    Spacer(Modifier.height(SemsSpacing.md))
                }

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.login_email_label)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_mail), contentDescription = null) },
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
                Spacer(Modifier.height(SemsSpacing.md))
                PasswordField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = R.string.login_password_label,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
                    // Wrong credentials also mark the password field, as in M01.
                    isError = state.passwordError != null || state.error == LoginError.INVALID_CREDENTIALS,
                    supportingText = state.passwordError?.let { { Text(stringResource(it.message())) } },
                    enabled = !state.isLoading,
                    onImeAction = submit,
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onForgotPassword, enabled = !state.isLoading) {
                        Text(stringResource(R.string.login_forgot_password))
                    }
                }
                Spacer(Modifier.height(SemsSpacing.xs))
                AuthSubmitButton(
                    label = R.string.login_submit,
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
                        text = stringResource(R.string.login_no_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCreateAccount, enabled = !state.isLoading) {
                        Text(stringResource(R.string.login_create_account))
                    }
                }
            }
            Text(
                text = stringResource(R.string.login_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = SemsSpacing.lg),
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SemsLogoMark()
        Spacer(Modifier.width(SemsSpacing.sm))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LoginErrorBanner(error: LoginError, onRetry: () -> Unit, onVerifyAccount: () -> Unit) {
    when (error) {
        LoginError.INVALID_CREDENTIALS ->
            AuthErrorBanner(R.string.login_error_credentials_title, R.string.login_error_credentials_body)
        // Fallback flow: only happens if the backend requires email verification.
        LoginError.NOT_VERIFIED -> AuthErrorBanner(
            title = R.string.login_error_not_verified_title,
            body = R.string.login_error_not_verified_body,
            actionLabel = R.string.login_enter_code,
            onAction = onVerifyAccount,
        )
        LoginError.NO_CONNECTION ->
            AuthErrorBanner(R.string.login_error_no_connection_title, R.string.login_error_no_connection_body)
        // Render's cold start can exceed the timeout: offer a visible retry.
        LoginError.TIMEOUT -> AuthErrorBanner(
            title = R.string.login_error_timeout_title,
            body = R.string.login_error_timeout_body,
            actionLabel = R.string.action_retry,
            onAction = onRetry,
        )
        LoginError.GENERIC ->
            AuthErrorBanner(R.string.login_error_generic_title, R.string.login_error_generic_body)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LoginPreview() {
    SemsTheme {
        Surface {
            LoginContent(LoginUiState(), {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LoginErrorPreview() {
    SemsTheme {
        Surface {
            LoginContent(
                LoginUiState(email = "ana@example.com", password = "secret", error = LoginError.INVALID_CREDENTIALS),
                {}, {}, {}, {}, {}, {},
            )
        }
    }
}
