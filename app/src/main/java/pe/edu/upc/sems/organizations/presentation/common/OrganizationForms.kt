package pe.edu.upc.sems.organizations.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.components.ErrorBanner
import pe.edu.upc.sems.shared.network.ApiError

/** Field validation errors of the organizations forms. */
enum class OrgFieldError { REQUIRED, INVALID_RUC, INVALID_NUMBER, MUST_BE_POSITIVE }

@StringRes
fun OrgFieldError.message(): Int = when (this) {
    OrgFieldError.REQUIRED -> R.string.field_error_required
    OrgFieldError.INVALID_RUC -> R.string.org_ruc_hint
    OrgFieldError.INVALID_NUMBER -> R.string.field_error_invalid_number
    OrgFieldError.MUST_BE_POSITIVE -> R.string.field_error_must_be_positive
}

/** Errors of the organizations forms. CONFLICT is a duplicate (RUC, site code or zone name). */
enum class FormError { CONFLICT, NOT_FOUND, NO_CONNECTION, TIMEOUT, GENERIC }

fun ApiError.toFormError(): FormError = when (this) {
    is ApiError.Http -> when (code) {
        409 -> FormError.CONFLICT
        404 -> FormError.NOT_FOUND
        else -> FormError.GENERIC
    }
    ApiError.NoConnection -> FormError.NO_CONNECTION
    ApiError.Timeout -> FormError.TIMEOUT
    is ApiError.Unauthorized, is ApiError.Serialization, is ApiError.Unknown -> FormError.GENERIC
}

/** Decimal typed with a comma (Peru) or a dot. Null when empty or not a number. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/**
 * Banner for a form error. [conflictTitle]/[conflictBody] explain the duplicate for each form
 * (RUC, site code…); [notFoundTitle]/[notFoundBody] are used by the members form (unknown email).
 */
@Composable
fun FormErrorBanner(
    error: FormError,
    @StringRes conflictTitle: Int,
    @StringRes conflictBody: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes notFoundTitle: Int = R.string.form_error_generic_title,
    @StringRes notFoundBody: Int = R.string.form_error_generic_body,
) {
    when (error) {
        FormError.CONFLICT -> ErrorBanner(conflictTitle, conflictBody, modifier)
        FormError.NOT_FOUND -> ErrorBanner(notFoundTitle, notFoundBody, modifier)
        FormError.NO_CONNECTION ->
            ErrorBanner(R.string.login_error_no_connection_title, R.string.login_error_no_connection_body, modifier)
        // Render's cold start can exceed the timeout: offer a visible retry.
        FormError.TIMEOUT -> ErrorBanner(
            title = R.string.login_error_timeout_title,
            body = R.string.login_error_timeout_body,
            modifier = modifier,
            actionLabel = R.string.action_retry,
            onAction = onRetry,
        )
        FormError.GENERIC -> ErrorBanner(R.string.form_error_generic_title, R.string.form_error_generic_body, modifier)
    }
}

/** Read-only text field that opens a menu with [options] (enums: business type, tariff category, zone type…). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    value: T,
    options: List<T>,
    @StringRes label: Int,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = supportingText,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
