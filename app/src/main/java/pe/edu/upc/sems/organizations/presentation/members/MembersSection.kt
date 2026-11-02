package pe.edu.upc.sems.organizations.presentation.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.domain.model.Member
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.presentation.common.DropdownField
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.label
import pe.edu.upc.sems.organizations.presentation.sites.InfoChip
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.SubmitButton

/** Roles offered in "Dar acceso", in the web app's order. */
private val GRANTABLE_ROLES = listOf(MembershipRole.ORG_ADMIN, MembershipRole.SUPERVISOR, MembershipRole.OPERATOR)

/**
 * "Quién tiene acceso" card, inside "Locales" (web: MembersCard.vue). Shown to every member;
 * "Dar acceso" and "Quitar acceso" only for ORG_ADMIN.
 * A supervisor only receives their own site, so other members' sites show "—", as in the web app.
 */
@Composable
fun MembersCard(
    state: MembersUiState,
    sites: List<Site>,
    onGrantClick: () -> Unit,
    onRevoke: (Member) -> Unit,
    onRetry: () -> Unit,
) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_group),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(SemsSpacing.sm))
                Text(
                    stringResource(R.string.members_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.canManage) {
                    TextButton(onClick = onGrantClick) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                        Spacer(Modifier.width(SemsSpacing.xs))
                        Text(stringResource(R.string.members_grant))
                    }
                }
            }
            state.revokeError?.let {
                FormErrorBanner(
                    error = it,
                    conflictTitle = R.string.members_error_last_admin_title,
                    conflictBody = R.string.members_error_last_admin_body,
                    onRetry = {},
                    modifier = Modifier.padding(top = SemsSpacing.sm),
                )
            }
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(SemsSpacing.md)
                        .size(24.dp)
                        .align(Alignment.CenterHorizontally),
                    strokeWidth = 2.dp,
                )
                state.loadError != null -> Column(Modifier.padding(top = SemsSpacing.sm)) {
                    Text(
                        stringResource(R.string.members_load_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                else -> state.members.forEachIndexed { index, member ->
                    if (index > 0) HorizontalDivider()
                    MemberRow(
                        member = member,
                        siteName = member.siteId?.let { id -> sites.firstOrNull { it.id == id }?.name },
                        isMe = member.userId == state.currentUserId,
                        canRevoke = state.canManage,
                        isRevoking = state.revokingId == member.membershipId,
                        onRevoke = { onRevoke(member) },
                    )
                }
            }
            // The signed-in administrator is always listed: "nobody else" means only them.
            if (!state.isLoading && state.loadError == null && state.members.all { it.userId == state.currentUserId }) {
                Text(
                    stringResource(R.string.members_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SemsSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: Member,
    siteName: String?,
    isMe: Boolean,
    canRevoke: Boolean,
    isRevoking: Boolean,
    onRevoke: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SemsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = member.email ?: stringResource(R.string.members_deleted_account),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SemsSpacing.xs),
                modifier = Modifier.padding(top = SemsSpacing.xxs),
            ) {
                InfoChip(stringResource(member.role.label()))
                Text(
                    text = when {
                        member.siteId == null -> stringResource(R.string.members_whole_organization)
                        else -> siteName ?: stringResource(R.string.value_not_set)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            isMe -> InfoChip(stringResource(R.string.members_you), highlighted = true)
            // Read only for SUPERVISOR and OPERATOR.
            !canRevoke -> Unit
            isRevoking -> CircularProgressIndicator(Modifier.padding(SemsSpacing.sm).size(24.dp), strokeWidth = 2.dp)
            else -> IconButton(onClick = onRevoke) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.members_revoke),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Dar acceso": email, role and, depending on the role, the site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrantAccessSheet(
    form: GrantForm,
    sites: List<Site>,
    onEmailChange: (String) -> Unit,
    onRoleChange: (MembershipRole) -> Unit,
    onSiteChange: (String?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = SemsSpacing.lg)
                .padding(bottom = SemsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm),
        ) {
            Text(stringResource(R.string.members_grant), style = MaterialTheme.typography.titleLarge)
            form.error?.let {
                FormErrorBanner(
                    error = it,
                    conflictTitle = R.string.form_error_generic_title,
                    conflictBody = R.string.form_error_generic_body,
                    onRetry = onSubmit,
                    notFoundTitle = R.string.members_error_not_found_title,
                    notFoundBody = R.string.members_error_not_found_body,
                )
            }
            OutlinedTextField(
                value = form.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.members_email_label)) },
                placeholder = { Text(stringResource(R.string.members_email_placeholder)) },
                isError = form.emailError != null,
                supportingText = form.emailError?.let { { Text(stringResource(it.message())) } },
                singleLine = true,
                enabled = !form.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                value = form.role,
                options = GRANTABLE_ROLES,
                label = R.string.members_role_label,
                optionLabel = { stringResource(it.label()) },
                onSelect = onRoleChange,
                enabled = !form.isSaving,
                supportingText = { Text(stringResource(form.role.description())) },
                modifier = Modifier.fillMaxWidth(),
            )
            val scope = form.role.siteScope
            if (scope != SiteScope.FORBIDDEN) {
                // null = "Toda la organización" (operator) or "Elige un local" (supervisor).
                val options: List<Site?> = listOf<Site?>(null) + sites
                DropdownField(
                    value = sites.firstOrNull { it.id == form.siteId },
                    options = options,
                    label = if (scope == SiteScope.REQUIRED) R.string.members_site_required_label else R.string.members_site_optional_label,
                    optionLabel = { site ->
                        site?.name ?: stringResource(
                            if (scope == SiteScope.REQUIRED) R.string.members_site_pick else R.string.members_whole_organization,
                        )
                    },
                    onSelect = { onSiteChange(it?.id) },
                    enabled = !form.isSaving,
                    supportingText = form.siteError?.let { { Text(stringResource(it.message())) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (scope == SiteScope.REQUIRED && sites.isEmpty()) {
                    Text(
                        stringResource(R.string.members_supervisor_no_sites),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(SemsSpacing.xs))
            SubmitButton(label = R.string.members_grant, isLoading = form.isSaving, isSlow = form.isSlow, onClick = onSubmit)
        }
    }
}

@Composable
fun RevokeAccessDialog(member: Member, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val who = member.email ?: stringResource(R.string.members_this_person)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.members_revoke)) },
        text = { Text(stringResource(R.string.members_revoke_message, who)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.members_revoke_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun MemberFieldError.message(): Int = when (this) {
    MemberFieldError.REQUIRED -> R.string.field_error_required
    MemberFieldError.INVALID_EMAIL -> R.string.members_error_invalid_email
    MemberFieldError.SITE_REQUIRED -> R.string.members_error_site_required
}

/** Role description shown under the selector (web: MembersCard.vue). */
private fun MembershipRole.description(): Int = when (this) {
    MembershipRole.ORG_ADMIN -> R.string.members_role_admin_desc
    MembershipRole.SUPERVISOR -> R.string.members_role_supervisor_desc
    MembershipRole.OPERATOR, MembershipRole.UNKNOWN -> R.string.members_role_operator_desc
}
