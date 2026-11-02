package pe.edu.upc.sems.organizations.presentation.gettingstarted

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.SemsSpacing

/** Shows "Primeros pasos" while there is no company or no site yet; nothing once both are done. */
@Composable
fun GettingStartedCard(
    onRegisterCompany: () -> Unit,
    onAddFirstSite: (organizationId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GettingStartedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.isVisible) return
    GettingStartedContent(
        state = state,
        onRegisterCompany = onRegisterCompany,
        onAddFirstSite = { state.organizationId?.let(onAddFirstSite) },
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun GettingStartedContent(
    state: GettingStartedUiState,
    onRegisterCompany: () -> Unit,
    onAddFirstSite: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.getting_started_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.getting_started_progress,
                        state.completedSteps,
                        GettingStartedUiState.TOTAL_STEPS,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(SemsSpacing.xxs))
            Text(
                text = stringResource(R.string.getting_started_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SemsSpacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
                StepRow(
                    number = 1,
                    done = state.hasOrganization,
                    title = R.string.getting_started_company_title,
                    detail = R.string.getting_started_company_detail,
                    onClick = onRegisterCompany.takeIf { !state.hasOrganization && state.canManage },
                )
                StepRow(
                    number = 2,
                    done = state.hasSites,
                    title = R.string.getting_started_site_title,
                    detail = when {
                        // Only ORG_ADMIN can create sites: tell the others who can.
                        state.hasOrganization && !state.canManage -> R.string.getting_started_site_ask_admin
                        else -> R.string.getting_started_site_detail
                    },
                    // Available once the company exists.
                    onClick = onAddFirstSite.takeIf { state.hasOrganization && !state.hasSites && state.canManage },
                    enabled = state.hasOrganization,
                )
            }
            if (state.loadFailed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.getting_started_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    done: Boolean,
    @StringRes title: Int,
    @StringRes detail: Int,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(SemsSpacing.sm), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        // Completed steps are an achieved goal: tertiary (green), as in the web app.
                        if (done) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.getting_started_step_done),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(SemsSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (done || !enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                if (!done) {
                    Text(
                        text = stringResource(detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
