package pe.edu.upc.sems.organizations.presentation.sites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.domain.model.Organization
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.presentation.common.FormErrorBanner
import pe.edu.upc.sems.organizations.presentation.common.label
import pe.edu.upc.sems.organizations.presentation.gettingstarted.GettingStartedCard
import pe.edu.upc.sems.organizations.presentation.members.GrantAccessSheet
import pe.edu.upc.sems.organizations.presentation.members.MembersCard
import pe.edu.upc.sems.organizations.presentation.members.MembersViewModel
import pe.edu.upc.sems.organizations.presentation.members.RevokeAccessDialog
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.LoadingScreen
import pe.edu.upc.sems.shared.util.Formatters

/** "Locales" (M27 + the organization card of M23, as in the web app's sites view). */
@Composable
fun SitesScreen(
    onSiteClick: (siteId: String) -> Unit,
    onNewSite: (organizationId: String) -> Unit,
    onEditOrganization: () -> Unit,
    onRegisterCompany: () -> Unit,
    viewModel: SitesViewModel = hiltViewModel(),
    membersViewModel: MembersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Reload when coming back from a form (new site, edited site or company).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    when (val current = state) {
        SitesUiState.Loading -> LoadingScreen()
        SitesUiState.NoOrganization -> Column(Modifier.padding(SemsSpacing.md)) {
            GettingStartedCard(
                onRegisterCompany = onRegisterCompany,
                onAddFirstSite = onNewSite,
            )
        }
        is SitesUiState.Error -> Column(Modifier.padding(SemsSpacing.md)) {
            FormErrorBanner(
                error = current.error,
                conflictTitle = R.string.form_error_generic_title,
                conflictBody = R.string.form_error_generic_body,
                onRetry = viewModel::refresh,
                offerRetry = true,
            )
        }
        is SitesUiState.Success -> {
            val members by membersViewModel.uiState.collectAsStateWithLifecycle()
            // Every member can see who has access; only ORG_ADMIN manages it (backend: administraOrganizacion).
            LaunchedEffect(current.organization.id, current.canManageOrganization) {
                membersViewModel.start(current.organization.id, canManage = current.canManageOrganization)
            }
            SitesContent(
                state = current,
                onSiteClick = onSiteClick,
                onNewSite = { onNewSite(current.organization.id) },
                onEditOrganization = onEditOrganization,
                membersSection = {
                    MembersCard(
                        state = members,
                        sites = current.sites,
                        onGrantClick = membersViewModel::onGrantClick,
                        onRevoke = membersViewModel::onRevokeRequest,
                        onRetry = membersViewModel::refresh,
                    )
                },
            )
            if (current.canManageOrganization) {
                members.grantForm?.let { form ->
                    GrantAccessSheet(
                        form = form,
                        sites = current.sites,
                        onEmailChange = membersViewModel::onEmailChange,
                        onRoleChange = membersViewModel::onRoleChange,
                        onSiteChange = membersViewModel::onSiteChange,
                        onSubmit = membersViewModel::onGrantSubmit,
                        onDismiss = membersViewModel::onGrantDismiss,
                    )
                }
                members.confirmingRevoke?.let { member ->
                    RevokeAccessDialog(
                        member = member,
                        onConfirm = membersViewModel::onRevokeConfirm,
                        onDismiss = membersViewModel::onRevokeDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun SitesContent(
    state: SitesUiState.Success,
    onSiteClick: (String) -> Unit,
    onNewSite: () -> Unit,
    onEditOrganization: () -> Unit,
    membersSection: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = SemsSpacing.md, end = SemsSpacing.md, top = SemsSpacing.sm, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SemsSpacing.sm),
        ) {
            item {
                OrganizationCard(
                    organization = state.organization,
                    siteCount = state.sites.size,
                    canEdit = state.canManageOrganization,
                    onEdit = onEditOrganization,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.sites_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = SemsSpacing.sm),
                )
            }
            if (state.sites.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (state.canManageOrganization) R.string.sites_empty_admin else R.string.getting_started_site_ask_admin,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.sites, key = { it.id }) { site ->
                SiteCard(site = site, isActive = site.id == state.activeSiteId, onClick = { onSiteClick(site.id) })
            }
            item { Spacer(Modifier.height(SemsSpacing.sm)) }
            item { membersSection() }
        }
        if (state.canManageOrganization) {
            // FAB: radius 16 dp, Primary Container (design spec).
            ExtendedFloatingActionButton(
                onClick = onNewSite,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.site_new_title)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(SemsSpacing.md),
            )
        }
    }
}

@Composable
private fun OrganizationCard(organization: Organization, siteCount: Int, canEdit: Boolean, onEdit: () -> Unit) {
    OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(SemsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.ic_domain)
            Spacer(Modifier.width(SemsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(organization.legalName, style = MaterialTheme.typography.titleMedium)
                organization.tradeName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = stringResource(
                        R.string.org_summary,
                        organization.taxId,
                        stringResource(organization.businessType.label()),
                        pluralStringResource(R.plurals.sites_count, siteCount, siteCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canEdit) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            }
        }
    }
}

@Composable
private fun SiteCard(site: Site, isActive: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(SemsSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(R.drawable.ic_storefront)
                Spacer(Modifier.width(SemsSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(site.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    siteLocation(site)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = SemsSpacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SemsSpacing.xs), verticalArrangement = Arrangement.spacedBy(SemsSpacing.xs)) {
                if (isActive) InfoChip(stringResource(R.string.sites_active_badge), highlighted = true)
                InfoChip(stringResource(R.string.sites_contracted_power_chip, Formatters.kilowatts(site.contractedPowerKw)))
                InfoChip(stringResource(R.string.sites_tariff_chip, site.tariffCategory.name))
                Text(
                    text = site.siteCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** "Av. Javier Prado Este 1234 · San Isidro", or whichever part exists. */
fun siteLocation(site: Site): String? =
    listOfNotNull(site.address, site.district).joinToString(" · ").ifEmpty { null }

@Composable
fun IconBadge(icon: Int) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun InfoChip(text: String, highlighted: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xxs),
        )
    }
}
