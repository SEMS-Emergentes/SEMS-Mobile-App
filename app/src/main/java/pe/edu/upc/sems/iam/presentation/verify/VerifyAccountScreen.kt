package pe.edu.upc.sems.iam.presentation.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.components.ErrorBanner
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton
import pe.edu.upc.sems.iam.presentation.common.message
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.SemsTheme

@Composable
fun VerifyAccountScreen(
    email: String,
    onClose: () -> Unit,
    viewModel: VerifyAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val verified = state.verifiedAccount
    if (verified == null) {
        VerifyCodeContent(
            email = email,
            state = state,
            onCodeChange = viewModel::onCodeChange,
            onSubmit = viewModel::onSubmit,
            onBack = onClose,
        )
    } else {
        AccountVerifiedContent(
            email = verified.user.emailAddress,
            onContinue = viewModel::onContinue,
            onClose = onClose,
        )
    }
}

/** Code entry. Not in the Figma: it follows the style of the other access forms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyCodeContent(
    email: String,
    state: VerifyAccountUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            // The access graph's Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.titleLarge) },
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
                text = if (email.isBlank()) {
                    stringResource(R.string.verify_body_no_email)
                } else {
                    stringResource(R.string.verify_body, email)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SemsSpacing.lg))
            state.error?.let { error ->
                when (error) {
                    VerifyError.INVALID_CODE ->
                        ErrorBanner(R.string.verify_error_invalid_title, R.string.verify_error_invalid_body)
                    VerifyError.NO_CONNECTION ->
                        ErrorBanner(R.string.login_error_no_connection_title, R.string.login_error_no_connection_body)
                    VerifyError.TIMEOUT -> ErrorBanner(
                        title = R.string.login_error_timeout_title,
                        body = R.string.login_error_timeout_body,
                        actionLabel = R.string.action_retry,
                        onAction = submit,
                    )
                    VerifyError.GENERIC ->
                        ErrorBanner(R.string.verify_error_generic_title, R.string.login_error_generic_body)
                }
                Spacer(Modifier.height(SemsSpacing.md))
            }
            OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.verify_code_label)) },
                isError = state.codeError != null || state.error == VerifyError.INVALID_CODE,
                supportingText = state.codeError?.let { { Text(stringResource(it.message())) } },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SemsSpacing.md))
            SubmitButton(
                label = R.string.verify_submit,
                isLoading = state.isLoading,
                isSlow = state.isSlow,
                onClick = submit,
            )
        }
    }
}

/** M29 "¡Cuenta verificada!". */
@Composable
fun AccountVerifiedContent(
    email: String,
    onContinue: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = SemsSpacing.lg),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.padding(SemsSpacing.xxs)) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SemsSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SemsSpacing.md),
        ) {
            Spacer(Modifier.height(SemsSpacing.lg))
            // Success uses the tertiary (green) roles, as in the Figma.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.onTertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                }
            }
            Text(
                text = stringResource(R.string.verified_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.verified_body, email),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(SemsSpacing.md)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_storefront),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(SemsSpacing.sm))
                    Text(
                        text = stringResource(R.string.verified_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SemsSpacing.lg)
                .height(48.dp),
        ) {
            Text(stringResource(R.string.verified_continue))
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AccountVerifiedPreview() {
    SemsTheme {
        Surface { AccountVerifiedContent(email = "valeria@example.com", onContinue = {}, onClose = {}) }
    }
}
