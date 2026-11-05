package pe.edu.upc.sems.shared.navigation

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import pe.edu.upc.sems.shared.designsystem.components.SemsLogoMark
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import pe.edu.upc.sems.alerts.presentation.alerts.AlertsScreen
import pe.edu.upc.sems.alerts.presentation.badge.AlertBadgeViewModel
import pe.edu.upc.sems.dashboard.presentation.DashboardScreen
import pe.edu.upc.sems.devices.presentation.deviceform.DeviceFormMode
import pe.edu.upc.sems.devices.presentation.deviceform.DeviceFormScreen
import pe.edu.upc.sems.devices.presentation.devices.DevicesScreen
import pe.edu.upc.sems.energy.presentation.meters.MeterLinkScreen
import pe.edu.upc.sems.energy.presentation.meters.MeterHistoryScreen
import pe.edu.upc.sems.energy.presentation.meters.MeterScannerScreen
import pe.edu.upc.sems.energy.presentation.monitoring.MonitoringScreen
import pe.edu.upc.sems.energy.presentation.consumption.ConsumptionDetailScreen
import pe.edu.upc.sems.organizations.presentation.organizationform.RegisterOrganizationScreen
import pe.edu.upc.sems.organizations.presentation.organizationform.EditOrganizationScreen
import pe.edu.upc.sems.organizations.presentation.sitedetail.SiteDetailScreen
import pe.edu.upc.sems.organizations.presentation.sites.SitesScreen
import pe.edu.upc.sems.organizations.presentation.siteform.SiteFormMode
import pe.edu.upc.sems.organizations.presentation.siteform.SiteFormScreen
import pe.edu.upc.sems.organizations.presentation.siteselector.SiteSelectorLabel
import pe.edu.upc.sems.organizations.presentation.siteselector.SiteSelectorSheet
import pe.edu.upc.sems.organizations.presentation.siteselector.SiteSelectorViewModel
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.ComingSoonScreen

/**
 * Main graph (logged in): top app bar, bottom NavigationBar for the four main sections and
 * ModalNavigationDrawer for the rest. Drawer screens show "back" and hide the bottom bar.
 */
@Composable
fun MainNavHost(account: DrawerAccount?, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val bottomDestination = destination.bottomDestination()
    val drawerDestination = destination.drawerDestination()
    // Forms draw their own top bar with "back" and hide the bottom bar.
    val isFullScreenForm = destination.isRoute(RegisterOrganizationRoute) ||
        destination.isRoute(EditOrganizationRoute) ||
        destination.isRoute(NewDeviceRoute) ||
        destination.isRoute(EditDeviceRoute::class) ||
        destination.isRoute(ScanMeterRoute) ||
        destination.isRoute(LinkMeterRoute::class) ||
        destination.isRoute(MeterHistoryRoute::class) ||
        destination.isRoute(ConsumptionDetailRoute::class) ||
        destination.isRoute(NewSiteRoute::class) ||
        destination.isRoute(EditSiteRoute::class) ||
        destination.isRoute(SiteDetailRoute::class)
    // Before the first destination is attached, treat the screen as top level (Home).
    val isTopLevel = drawerDestination == null && !isFullScreenForm

    // Active site under the title of the main screens: always show which site the user is in.
    val siteSelector: SiteSelectorViewModel = hiltViewModel()
    val siteSelectorState by siteSelector.uiState.collectAsStateWithLifecycle()
    // Back on a main screen (e.g. after creating or archiving a site in Locales): refresh the site list.
    LaunchedEffect(isTopLevel) { if (isTopLevel) siteSelector.refresh() }

    // Number of active alerts, on the Alertas item of the bottom bar.
    val alertBadge: AlertBadgeViewModel = hiltViewModel()
    val activeAlerts by alertBadge.count.collectAsStateWithLifecycle()
    LaunchedEffect(isTopLevel) { if (isTopLevel) alertBadge.refresh() }

    // System back closes the open drawer instead of leaving the app.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Edge swipe only on top-level screens, where the menu button is shown.
        gesturesEnabled = isTopLevel || drawerState.isOpen,
        drawerContent = {
            SemsDrawerContent(
                account = account,
                selected = drawerDestination,
                selectedBottom = bottomDestination,
                onSelect = { target ->
                    scope.launch { drawerState.close() }
                    navController.navigateToDrawerDestination(target, from = drawerDestination)
                },
                onSelectBottom = { target ->
                    scope.launch { drawerState.close() }
                    navController.navigateToBottomDestination(target)
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    onLogout()
                },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            topBar = {
                if (!isFullScreenForm) SemsTopAppBar(
                    title = stringResource(
                        drawerDestination?.label ?: bottomDestination?.label ?: BottomDestination.DASHBOARD.label,
                    ),
                    showBack = !isTopLevel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                    onAlerts = if (isTopLevel) {
                        { navController.navigateToBottomDestination(BottomDestination.ALERTS) }
                    } else null,
                    subtitle = if (isTopLevel) {
                        { SiteSelectorLabel(state = siteSelectorState, onClick = siteSelector::onOpen) }
                    } else {
                        null
                    },
                )
            },
            bottomBar = {
                if (isTopLevel) {
                    SemsNavigationBar(
                        selected = bottomDestination ?: BottomDestination.DASHBOARD,
                        onSelect = navController::navigateToBottomDestination,
                        activeAlerts = activeAlerts,
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = DashboardRoute,
                modifier = Modifier.padding(padding),
                enterTransition = semsEnterTransition(),
                exitTransition = semsExitTransition(),
                popEnterTransition = semsEnterTransition(),
                popExitTransition = semsExitTransition(),
            ) {
                // Bottom navigation
                composable<DashboardRoute> {
                    DashboardScreen(
                        onRegisterCompany = { navController.navigate(RegisterOrganizationRoute) },
                        onAddFirstSite = { organizationId -> navController.navigate(NewSiteRoute(organizationId)) },
                        onOpenMonitoring = { navController.navigateToBottomDestination(BottomDestination.MONITORING) },
                    )
                }
                composable<MonitoringRoute> {
                    MonitoringScreen(
                        onLinkMeter = { navController.navigate(ScanMeterRoute) },
                        onMeterHistory = { navController.navigate(MeterHistoryRoute(it)) },
                        onDeviceClick = { navController.navigate(ConsumptionDetailRoute(it)) },
                    )
                }
                composable<AlertsRoute> {
                    AlertsScreen(onViewSite = { siteId -> navController.navigate(SiteDetailRoute(siteId)) })
                }
                composable<AnalyticsRoute> { ComingSoonScreen() }
                // Drawer
                composable<DevicesRoute> {
                    DevicesScreen(
                        onNewDevice = { navController.navigate(NewDeviceRoute) },
                        onDeviceClick = { deviceId -> navController.navigate(EditDeviceRoute(deviceId)) },
                    )
                }
                composable<SitesRoute> {
                    SitesScreen(
                        onSiteClick = { siteId -> navController.navigate(SiteDetailRoute(siteId)) },
                        onNewSite = { organizationId -> navController.navigate(NewSiteRoute(organizationId)) },
                        onEditOrganization = { navController.navigate(EditOrganizationRoute) },
                        onRegisterCompany = { navController.navigate(RegisterOrganizationRoute) },
                    )
                }
                composable<ReportsRoute> { ComingSoonScreen() }
                composable<SubscriptionRoute> { ComingSoonScreen() }
                composable<SettingsRoute> { ComingSoonScreen() }
                // Full-screen forms
                composable<NewDeviceRoute> {
                    DeviceFormScreen(
                        mode = DeviceFormMode.Create,
                        onBack = navController::popBackStack,
                        onSaved = navController::popBackStack,
                    )
                }
                composable<EditDeviceRoute> { entry ->
                    DeviceFormScreen(
                        mode = DeviceFormMode.Edit(entry.toRoute<EditDeviceRoute>().deviceId),
                        onBack = navController::popBackStack,
                        onSaved = navController::popBackStack,
                    )
                }
                composable<ScanMeterRoute> {
                    MeterScannerScreen(
                        onBack = navController::popBackStack,
                        onManual = { navController.navigate(LinkMeterRoute()) },
                        onScanned = { serial, model -> navController.navigate(LinkMeterRoute(serial, model)) },
                    )
                }
                composable<LinkMeterRoute> { entry ->
                    val route = entry.toRoute<LinkMeterRoute>()
                    MeterLinkScreen(
                        route.serial,
                        route.model,
                        navController::popBackStack,
                        { navController.popBackStack(MonitoringRoute, false) },
                    )
                }
                composable<MeterHistoryRoute> { entry ->
                    MeterHistoryScreen(entry.toRoute<MeterHistoryRoute>().meterId, navController::popBackStack)
                }
                composable<ConsumptionDetailRoute> { entry ->
                    ConsumptionDetailScreen(entry.toRoute<ConsumptionDetailRoute>().deviceId, navController::popBackStack)
                }
                composable<RegisterOrganizationRoute> {
                    RegisterOrganizationScreen(
                        onBack = navController::popBackStack,
                        onRegistered = navController::popBackStack,
                    )
                }
                composable<NewSiteRoute> { entry ->
                    SiteFormScreen(
                        mode = SiteFormMode.Create(entry.toRoute<NewSiteRoute>().organizationId),
                        onBack = navController::popBackStack,
                        onSaved = navController::popBackStack,
                    )
                }
                composable<EditSiteRoute> { entry ->
                    SiteFormScreen(
                        mode = SiteFormMode.Edit(entry.toRoute<EditSiteRoute>().siteId),
                        onBack = navController::popBackStack,
                        onSaved = navController::popBackStack,
                    )
                }
                composable<SiteDetailRoute> { entry ->
                    SiteDetailScreen(
                        siteId = entry.toRoute<SiteDetailRoute>().siteId,
                        onBack = navController::popBackStack,
                        onEdit = { siteId -> navController.navigate(EditSiteRoute(siteId)) },
                    )
                }
                composable<EditOrganizationRoute> {
                    EditOrganizationScreen(
                        onBack = navController::popBackStack,
                        onSaved = navController::popBackStack,
                    )
                }
            }
        }
    }

    if (siteSelectorState.isSheetOpen) {
        SiteSelectorSheet(
            state = siteSelectorState,
            onSelect = siteSelector::onSelect,
            onDismiss = siteSelector::onDismiss,
        )
    }
}

/** Standard M3 tab behavior: one copy of each tab, state saved and restored when switching. */
private fun NavHostController.navigateToBottomDestination(target: BottomDestination) {
    navigate(target.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Drawer screens stack one level above the current tab, so "back" always returns to that tab. */
private fun NavHostController.navigateToDrawerDestination(target: DrawerDestination, from: DrawerDestination?) {
    if (target == from) return
    if (from != null) popBackStack()
    navigate(target.route) { launchSingleTop = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemsTopAppBar(
    title: String,
    showBack: Boolean,
    onMenu: () -> Unit,
    onBack: () -> Unit,
    onAlerts: (() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
) {
    Surface(shadowElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SemsLogoMark(size = 34.dp)
                    Spacer(Modifier.width(SemsSpacing.sm))
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.invoke()
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = if (showBack) onBack else onMenu) {
                    Icon(
                        painterResource(if (showBack) R.drawable.ic_arrow_back else R.drawable.ic_menu),
                        stringResource(if (showBack) R.string.action_back else R.string.action_open_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                onAlerts?.let { navigate ->
                    IconButton(
                        onClick = navigate,
                        modifier = Modifier.padding(end = SemsSpacing.xs)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_notifications),
                            contentDescription = stringResource(R.string.nav_alerts),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        )
    }
}

@Composable
private fun SemsNavigationBar(
    selected: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
    activeAlerts: Int = 0,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .navigationBarsPadding(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xs),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 1.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(SemsSpacing.xxs).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(SemsSpacing.xxs),
            ) {
                BottomDestination.entries.forEach { item ->
                    val isSelected = item == selected
                    val background by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                        label = "Navigation selection",
                    )
                    val foreground by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "Navigation icon",
                    )
                    Column(
                        modifier = Modifier.weight(1f)
                            .heightIn(min = 60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(background)
                            .selectable(
                                selected = isSelected,
                                onClick = { if (!isSelected) onSelect(item) },
                                role = Role.Tab,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val showBadge = item == BottomDestination.ALERTS && activeAlerts > 0
                        val badgeDescription = pluralStringResource(R.plurals.alerts_badge_description, activeAlerts, activeAlerts)
                        BadgedBox(
                            badge = {
                                if (showBadge) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ) { Text(if (activeAlerts > 99) "99+" else activeAlerts.toString()) }
                                }
                            },
                            modifier = if (showBadge) {
                                Modifier.semantics {
                                    contentDescription = badgeDescription
                                }
                            } else {
                                Modifier
                            },
                        ) {
                            Icon(
                                painter = painterResource(if (isSelected) item.selectedIcon else item.icon),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = foreground,
                            )
                        }
                        Spacer(Modifier.height(SemsSpacing.xxs))
                        Text(
                            stringResource(item.label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = foreground,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SemsDrawerContent(
    account: DrawerAccount?,
    selected: DrawerDestination?,
    selectedBottom: BottomDestination?,
    onSelect: (DrawerDestination) -> Unit,
    onSelectBottom: (BottomDestination) -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
    ) {
        // Header: SEMS logo and the organization (Figma v2, M06).
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SemsSpacing.lg, vertical = SemsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SemsLogoMark(size = 36.dp)
            Spacer(Modifier.width(SemsSpacing.sm))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = account?.organizationName ?: stringResource(R.string.drawer_no_organization),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            BottomDestination.entries.forEach { item ->
                SemsDrawerItem(
                    label = stringResource(item.label),
                    icon = if (selectedBottom == item) item.selectedIcon else item.icon,
                    selected = selectedBottom == item,
                    onClick = { onSelectBottom(item) },
                )
            }
            Spacer(Modifier.height(SemsSpacing.sm))
            HorizontalDivider(Modifier.fillMaxWidth().padding(horizontal = SemsSpacing.lg))
            Spacer(Modifier.height(SemsSpacing.sm))
            DrawerDestination.entries
                .filter { !it.requiresOrgAdmin || account?.canSeeAdminEntries == true }
                .forEach { item ->
                val isSelected = item == selected
                SemsDrawerItem(
                    label = stringResource(item.label),
                    icon = if (isSelected) item.selectedIcon else item.icon,
                    selected = isSelected,
                    onClick = { onSelect(item) },
                )
            }
        }

        // Footer: who is logged in, with the logout action.
        HorizontalDivider(Modifier.fillMaxWidth().padding(horizontal = SemsSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = SemsSpacing.lg, end = SemsSpacing.xs, top = SemsSpacing.md, bottom = SemsSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = account?.initials.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(SemsSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account?.displayName.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(account.roleLabel()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onLogout) {
                Icon(painterResource(R.drawable.ic_logout), contentDescription = stringResource(R.string.nav_logout))
            }
        }
    }
}

@Composable
private fun SemsDrawerItem(
    label: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)) },
        icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp)) },
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SemsSpacing.sm, vertical = SemsSpacing.xxs).height(52.dp),
    )
}

/** Membership role (as the web app's MembersCard) or, without organization, the IAM account role. */
@StringRes
private fun DrawerAccount?.roleLabel(): Int = when (this?.membershipRole) {
    "ORG_ADMIN" -> R.string.membership_role_org_admin
    "SUPERVISOR" -> R.string.membership_role_supervisor
    "OPERATOR" -> R.string.membership_role_operator
    null -> if (this?.isPlatformAdmin == true) R.string.role_admin else R.string.role_staff
    else -> R.string.membership_role_unknown
}
