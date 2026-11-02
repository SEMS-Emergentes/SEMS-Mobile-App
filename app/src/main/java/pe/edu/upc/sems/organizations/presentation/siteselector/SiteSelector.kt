package pe.edu.upc.sems.organizations.presentation.siteselector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.presentation.sites.siteLocation
import pe.edu.upc.sems.shared.designsystem.SemsSpacing

/**
 * Active site under the screen title: "Local San Isidro ▾". With more than one site it opens the
 * selector; with one it is fixed (no arrow, not clickable). Nothing when there is no active site.
 */
@Composable
fun SiteSelectorLabel(state: SiteSelectorUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val site = state.selected ?: return
    val description = stringResource(
        if (state.canChange) R.string.site_selector_change_description else R.string.site_selector_fixed_description,
        site.siteName,
    )
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                // Fixed: aligned with the title. Changeable: inside a pill, so it needs some room.
                start = if (state.canChange) SemsSpacing.xs else 0.dp,
                end = if (state.canChange) SemsSpacing.xxs else SemsSpacing.sm,
                top = SemsSpacing.xxs,
                bottom = SemsSpacing.xxs,
            ),
        ) {
            Icon(
                painterResource(R.drawable.ic_storefront),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(SemsSpacing.xs))
            Text(
                text = site.siteName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.canChange) {
                Icon(
                    painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (state.canChange) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier.semantics { contentDescription = description },
        ) { content() }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier.semantics { contentDescription = description },
        ) { content() }
    }
}

/** "Elegir local": the sites the user reaches, with the active one checked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSelectorSheet(state: SiteSelectorUiState, onSelect: (Site) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = SemsSpacing.lg)
                .selectableGroup(),
        ) {
            Text(
                stringResource(R.string.site_selector_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = SemsSpacing.lg, vertical = SemsSpacing.sm),
            )
            state.sites.orEmpty().forEach { site ->
                val isSelected = site.id == state.selected?.siteId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(site) })
                        .padding(horizontal = SemsSpacing.lg, vertical = SemsSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Spacer(Modifier.width(SemsSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(site.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = listOfNotNull(site.siteCode, siteLocation(site)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
