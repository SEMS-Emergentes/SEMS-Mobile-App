package pe.edu.upc.sems.iam.presentation.password

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.components.ErrorBanner
import pe.edu.upc.sems.iam.presentation.common.AuthHeaderIcon
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton
import pe.edu.upc.sems.iam.presentation.common.AuthValidation
import pe.edu.upc.sems.iam.presentation.common.PasswordField
import pe.edu.upc.sems.iam.presentation.common.message
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.SemsTheme

// region M03 — Recupera tu contraseña

@Composable
fun ForgotPasswordScreen(
    initialEmail: String,
    onBack: () -> Unit,
    onLinkSent: (email: String) -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.prefill(initialEmail) }
    LaunchedEffect(state.sentTo) {
        state.sentTo?.let { email ->
            viewModel.onSentHandled()
            onLinkSent(email)
        }
    }
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.onSubmit()
    }

    PasswordScreenScaffold(navigationIcon = R.drawable.ic_arrow_back, onNavigation = onBack) {
        AuthHeaderIcon(
            icon = R.drawable.ic_lock,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(SemsSpacing.lg))
        Heading(R.string.forgot_title)
        Spacer(Modifier.height(SemsSpacing.xs))
        Body(stringResource(R.string.forgot_body))
        Spacer(Modifier.height(SemsSpacing.lg))
        state.error?.let { error ->
            PasswordErrorBanner(error = error, onRetry = submit)
            Spacer(Modifier.height(SemsSpacing.md))
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(stringResource(R.string.login_email_label)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_mail), contentDescription = null) },
            isError = state.emailError != null,
            supportingText = state.emailError?.let { { Text(stringResource(it.message())) } },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress },
        )
        Spacer(Modifier.height(SemsSpacing.md))
        SubmitButton(
            label = R.string.forgot_submit,
            isLoading = state.isLoading,
            isSlow = state.isSlow,
            onClick = submit,
        )
        Spacer(Modifier.height(SemsSpacing.sm))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.forgot_back_to_login))
        }
    }
}

// endregion

// region M04 — Revisa tu correo

@Composable
fun ResetLinkSentScreen(
    email: String,
    onBackToLogin: () -> Unit,
    onHaveLink: () -> Unit,
    viewModel: ResetLinkSentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PasswordScreenScaffold(
        navigationIcon = R.drawable.ic_close,
        onNavigation = onBackToLogin,
        bottom = {
            Button(
                onClick = onBackToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.forgot_back_to_login))
            }
            TextButton(
                onClick = { viewModel.onResend(email) },
                enabled = state.resendSecondsLeft == 0 && !state.isResending,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    if (state.resendSecondsLeft > 0) {
                        stringResource(R.string.link_sent_resend_countdown, formatCountdown(state.resendSecondsLeft))
                    } else {
                        stringResource(R.string.link_sent_resend)
                    },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(SemsSpacing.md))
            // Success uses the tertiary (green) roles, as in the Figma.
            AuthHeaderIcon(
                icon = R.drawable.ic_mail,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(SemsSpacing.lg))
            Heading(R.string.link_sent_title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(SemsSpacing.sm))
            Body(stringResource(R.string.link_sent_body, email), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(SemsSpacing.lg))
        InfoCard(icon = R.drawable.ic_schedule, text = stringResource(R.string.link_sent_spam_tip))
        Spacer(Modifier.height(SemsSpacing.md))
        state.error?.let { error ->
            PasswordErrorBanner(error = error, onRetry = { viewModel.onResend(email) })
            Spacer(Modifier.height(SemsSpacing.md))
        }
        if (state.resent) {
            Body(stringResource(R.string.link_sent_resent), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(SemsSpacing.md))
        }
        // The email link opens the web app unless App Links are verified: let the user paste it here.
        TextButton(onClick = onHaveLink, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.link_sent_have_link))
        }
    }
}

private fun formatCountdown(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

// endregion

// region M30 — Crea una nueva contraseña

@Composable
fun ResetPasswordScreen(
    tokenFromLink: String?,
    email: String?,
    onBack: () -> Unit,
    onPasswordChanged: () -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(tokenFromLink) { viewModel.setTokenFromLink(tokenFromLink) }
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.onDoneHandled()
            onPasswordChanged()
        }
    }
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        viewModel.onSubmit()
    }

    PasswordScreenScaffold(
        navigationIcon = R.drawable.ic_arrow_back,
        onNavigation = onBack,
        bottom = {
            SubmitButton(
                label = R.string.reset_submit,
                isLoading = state.isLoading,
                isSlow = state.isSlow,
                onClick = submit,
            )
        },
    ) {
        AuthHeaderIcon(
            icon = R.drawable.ic_lock,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(SemsSpacing.lg))
        Heading(R.string.reset_title)
        Spacer(Modifier.height(SemsSpacing.xs))
        Body(
            if (email.isNullOrBlank()) {
                stringResource(R.string.reset_body_no_email)
            } else {
                stringResource(R.string.reset_body, email)
            },
        )
        Spacer(Modifier.height(SemsSpacing.lg))
        state.error?.let { error ->
            PasswordErrorBanner(error = error, onRetry = submit)
            Spacer(Modifier.height(SemsSpacing.md))
        }
        if (state.needsLink) {
            OutlinedTextField(
                value = state.link,
                onValueChange = viewModel::onLinkChange,
                label = { Text(stringResource(R.string.reset_link_label)) },
                isError = state.linkError != null || state.error == PasswordError.INVALID_LINK,
                supportingText = {
                    Text(stringResource(state.linkError?.message() ?: R.string.reset_link_hint))
                },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SemsSpacing.sm))
        }
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = R.string.reset_new_password_label,
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { { Text(stringResource(it.message())) } },
            enabled = !state.isLoading,
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        Spacer(Modifier.height(SemsSpacing.sm))
        PasswordField(
            value = state.confirmation,
            onValueChange = viewModel::onConfirmationChange,
            label = R.string.reset_confirm_password_label,
            isError = state.confirmationError != null,
            supportingText = state.confirmationError?.let { { Text(stringResource(it.message())) } },
            enabled = !state.isLoading,
            contentType = ContentType.NewPassword,
            onImeAction = submit,
        )
        Spacer(Modifier.height(SemsSpacing.md))
        // Only the rules the backend checks (Figma's uppercase and number rules are not enforced by the API).
        RuleRow(
            met = state.hasMinLength,
            text = stringResource(R.string.reset_rule_min_length, AuthValidation.RESET_MIN_PASSWORD_LENGTH),
        )
        RuleRow(met = state.passwordsMatch, text = stringResource(R.string.reset_rule_match))
    }
}

@Composable
private fun RuleRow(met: Boolean, text: String) {
    Row(
        modifier = Modifier.padding(vertical = SemsSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (met) R.drawable.ic_check_circle_filled else R.drawable.ic_radio_button_unchecked),
            contentDescription = stringResource(if (met) R.string.reset_rule_met else R.string.reset_rule_not_met),
            // Met rules are a goal achieved: tertiary (green), as in the Figma.
            tint = if (met) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(SemsSpacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// endregion

// region Shared pieces

/** Navigation icon at the top, scrollable content and an optional bottom area (M03, M04, M30). */
@Composable
private fun PasswordScreenScaffold(
    @DrawableRes navigationIcon: Int,
    onNavigation: () -> Unit,
    bottom: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        IconButton(onClick = onNavigation, modifier = Modifier.padding(SemsSpacing.xxs)) {
            Icon(
                painter = painterResource(navigationIcon),
                contentDescription = stringResource(
                    if (navigationIcon == R.drawable.ic_close) R.string.action_close else R.string.action_back,
                ),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SemsSpacing.lg),
            content = content,
        )
        if (bottom != null) {
            Column(
                modifier = Modifier.padding(horizontal = SemsSpacing.lg, vertical = SemsSpacing.md),
                content = bottom,
            )
        }
    }
}

@Composable
private fun Heading(text: Int, textAlign: TextAlign? = null) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
    )
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
private fun InfoCard(@DrawableRes icon: Int, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(SemsSpacing.md)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(SemsSpacing.sm))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasswordErrorBanner(error: PasswordError, onRetry: () -> Unit) {
    when (error) {
        PasswordError.INVALID_LINK ->
            ErrorBanner(R.string.reset_error_invalid_link_title, R.string.reset_error_invalid_link_body)
        PasswordError.NO_CONNECTION ->
            ErrorBanner(R.string.login_error_no_connection_title, R.string.login_error_no_connection_body)
        PasswordError.TIMEOUT -> ErrorBanner(
            title = R.string.login_error_timeout_title,
            body = R.string.login_error_timeout_body,
            actionLabel = R.string.action_retry,
            onAction = onRetry,
        )
        PasswordError.GENERIC ->
            ErrorBanner(R.string.password_error_generic_title, R.string.login_error_generic_body)
    }
}

// endregion

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun RuleRowPreview() {
    SemsTheme {
        Surface {
            Column(Modifier.padding(SemsSpacing.lg)) {
                RuleRow(met = true, text = stringResource(R.string.reset_rule_min_length, 8))
                RuleRow(met = false, text = stringResource(R.string.reset_rule_match))
            }
        }
    }
}
