package pe.edu.upc.sems.shared.designsystem.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.SemsSpacing

/** Form error banner (M01 style), with an optional action such as "Reintentar". */
@Composable
fun ErrorBanner(
    @StringRes title: Int,
    @StringRes body: Int,
    modifier: Modifier = Modifier,
    @StringRes actionLabel: Int? = null,
    onAction: (() -> Unit)? = null,
) = ErrorBanner(stringResource(title), stringResource(body), modifier, actionLabel, onAction)

/** Same banner with texts already formatted (e.g. with numbers from the API). */
@Composable
fun ErrorBanner(
    title: String,
    body: String,
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
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(SemsSpacing.xxs))
                Text(body, style = MaterialTheme.typography.bodyMedium)
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
fun SubmitButton(
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
                Text(stringResource(R.string.connecting))
            }
        } else {
            Text(stringResource(label))
        }
    }
}
