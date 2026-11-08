package pe.edu.upc.sems.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.iam.domain.repository.UserSegment
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.FormScaffold
import pe.edu.upc.sems.shared.designsystem.SemsSpacing

/** "Editar perfil": name and segment, saved in the backend (PUT /users/me). */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    FormScaffold(title = stringResource(R.string.settings_edit_profile), onBack = onBack) {
        Spacer(Modifier.height(SemsSpacing.md))
        state.error?.let {
            FormErrorBanner(
                error = it,
                conflictTitle = R.string.form_error_generic_title,
                conflictBody = R.string.form_error_generic_body,
                onRetry = viewModel::onSave,
                modifier = Modifier.padding(bottom = SemsSpacing.md),
            )
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.settings_name)) },
            isError = state.nameError,
            supportingText = { if (state.nameError) Text(stringResource(R.string.settings_name_required)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = {},
            label = { Text(stringResource(R.string.settings_email_label)) },
            supportingText = { Text(stringResource(R.string.settings_email_fixed)) },
            readOnly = true,
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            stringResource(R.string.settings_segment),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = SemsSpacing.md),
        )
        Text(
            stringResource(R.string.settings_segment_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = SemsSpacing.xs),
        )
        Column(verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
            UserSegment.entries.forEach { segment ->
                val selected = segment == state.segment
                OutlinedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.onSegmentChange(segment) })
                            .padding(SemsSpacing.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(SemsSpacing.sm))
                        Column {
                            Text(stringResource(segment.title()), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(segment.description()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = viewModel::onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(top = SemsSpacing.lg),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(SemsSpacing.xs))
            }
            Text(stringResource(R.string.settings_save))
        }
    }
}

private fun UserSegment.title(): Int = when (this) {
    UserSegment.RETAIL_CHAIN -> R.string.settings_segment_chain
    UserSegment.INDEPENDENT -> R.string.settings_segment_independent
}

private fun UserSegment.description(): Int = when (this) {
    UserSegment.RETAIL_CHAIN -> R.string.settings_segment_chain_desc
    UserSegment.INDEPENDENT -> R.string.settings_segment_independent_desc
}
