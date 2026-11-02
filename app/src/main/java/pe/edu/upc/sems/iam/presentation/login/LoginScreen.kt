package pe.edu.upc.sems.iam.presentation.login

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.BuildConfig
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.SemsTheme
import pe.edu.upc.sems.shared.designsystem.components.SemsLogoMark

@Composable
fun LoginScreen(
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::onSubmit,
        onForgotPassword = onForgotPassword,
        onCreateAccount = onCreateAccount,
    )
}

/** Stateless M00/M01 layout. Google sign-in is hidden: /auth/google is not active in the deploy. */
@Composable
fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

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
                    ErrorBanner(
                        error = error,
                        // Render's cold start can exceed the timeout: offer a visible retry.
                        onRetry = if (error == LoginError.TIMEOUT) onSubmit else null,
                    )
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
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.login_password_label)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            Icon(
                                painter = painterResource(
                                    if (state.passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                ),
                                contentDescription = stringResource(
                                    if (state.passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                                ),
                            )
                        }
                    },
                    // Wrong credentials also mark the password field, as in M01.
                    isError = state.passwordError != null || state.error == LoginError.INVALID_CREDENTIALS,
                    supportingText = state.passwordError?.let { { Text(stringResource(it.message())) } },
                    visualTransformation = if (state.passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onSubmit()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onForgotPassword, enabled = !state.isLoading) {
                        Text(stringResource(R.string.login_forgot_password))
                    }
                }
                Spacer(Modifier.height(SemsSpacing.xs))
                SubmitButton(isLoading = state.isLoading, isSlow = state.isSlow, onClick = {
                    focusManager.clearFocus()
                    onSubmit()
                })
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
private fun ErrorBanner(error: LoginError, onRetry: (() -> Unit)?) {
    val (title, body) = error.texts()
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(modifier = Modifier.padding(SemsSpacing.md)) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null)
            Spacer(Modifier.width(SemsSpacing.sm))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(SemsSpacing.xxs))
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmitButton(isLoading: Boolean, isSlow: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            if (isSlow) {
                Spacer(Modifier.width(SemsSpacing.xs))
                Text(stringResource(R.string.login_connecting))
            }
        } else {
            Text(stringResource(R.string.login_submit))
        }
    }
}

@StringRes
private fun FieldError.message(): Int = when (this) {
    FieldError.REQUIRED -> R.string.field_error_required
    FieldError.INVALID_EMAIL -> R.string.field_error_invalid_email
}

private fun LoginError.texts(): Pair<Int, Int> = when (this) {
    LoginError.INVALID_CREDENTIALS -> R.string.login_error_credentials_title to R.string.login_error_credentials_body
    LoginError.NO_CONNECTION -> R.string.login_error_no_connection_title to R.string.login_error_no_connection_body
    LoginError.TIMEOUT -> R.string.login_error_timeout_title to R.string.login_error_timeout_body
    LoginError.GENERIC -> R.string.login_error_generic_title to R.string.login_error_generic_body
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
