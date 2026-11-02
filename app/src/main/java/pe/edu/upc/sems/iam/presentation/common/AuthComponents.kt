package pe.edu.upc.sems.iam.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
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
import androidx.compose.ui.unit.dp
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.SemsSpacing

/** Error banner of the access forms (M01 style), with an optional action such as "Reintentar". */
@Composable
fun AuthErrorBanner(
    @StringRes title: Int,
    @StringRes body: Int,
    modifier: Modifier = Modifier,
    @StringRes actionLabel: Int? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
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
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(stringResource(actionLabel))
                    }
                }
            }
        }
    }
}

/** Full-width pill button that shows a spinner while loading and "Conectando…" when the request is slow. */
@Composable
fun AuthSubmitButton(
    @StringRes label: Int,
    isLoading: Boolean,
    isSlow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
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
            Text(stringResource(label))
        }
    }
}

/** Password field with the show/hide toggle. Visibility survives configuration changes. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    contentType: ContentType = ContentType.Password,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        leadingIcon = leadingIcon,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(
                        if (visible) R.string.login_hide_password else R.string.login_show_password,
                    ),
                )
            }
        },
        isError = isError,
        supportingText = supportingText,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentType = contentType },
    )
}

@StringRes
fun FieldError.message(): Int = when (this) {
    FieldError.REQUIRED -> R.string.field_error_required
    FieldError.INVALID_EMAIL -> R.string.field_error_invalid_email
    FieldError.PASSWORD_TOO_SHORT -> R.string.field_error_password_too_short
    FieldError.TERMS_REQUIRED -> R.string.field_error_terms_required
}
