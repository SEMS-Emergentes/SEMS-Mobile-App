package pe.edu.upc.sems.shared.navigation

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import pe.edu.upc.sems.dashboard.presentation.DashboardScreen
import pe.edu.upc.sems.organizations.presentation.organizationform.RegisterOrganizationScreen
import pe.edu.upc.sems.organizations.presentation.siteform.SiteFormScreen
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
    val isFullScreenForm = destination.isRoute(RegisterOrganizationRoute) || destination.isRoute(NewSiteRoute::class)
    // Before the first destination is attached, treat the screen as top level (Home).
    val isTopLevel = drawerDestination == null && !isFullScreenForm

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
                onSelect = { target ->
                    scope.launch { drawerState.close() }
                    navController.navigateToDrawerDestination(target, from = drawerDestination)
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    onLogout()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                if (!isFullScreenForm) SemsTopAppBar(
                    title = stringResource(
                        drawerDestination?.label ?: bottomDestination?.label ?: BottomDestination.DASHBOARD.label,
                    ),
                    showBack = !isTopLevel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                )
            },
            bottomBar = {
                if (isTopLevel) {
                    SemsNavigationBar(
                        selected = bottomDestination ?: BottomDestination.DASHBOARD,
                        onSelect = navController::navigateToBottomDestination,
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
                    )
                }
                composable<MonitoringRoute> { ComingSoonScreen() }
                composable<AlertsRoute> { ComingSoonScreen() }
                composable<AnalyticsRoute> { ComingSoonScreen() }
                // Drawer
                composable<DevicesRoute> { ComingSoonScreen() }
                composable<SitesRoute> { ComingSoonScreen() }
                composable<ReportsRoute> { ComingSoonScreen() }
                composable<SubscriptionRoute> { ComingSoonScreen() }
                composable<SettingsRoute> { ComingSoonScreen() }
                // Full-screen forms
                composable<RegisterOrganizationRoute> {
                    RegisterOrganizationScreen(
                        onBack = navController::popBackStack,
                        onRegistered = navController::popBackStack,
                    )
                }
                composable<NewSiteRoute> { entry ->
                    SiteFormScreen(
                        organizationId = entry.toRoute<NewSiteRoute>().organizationId,
                        onBack = navController::popBackStack,
                        onSaved = navController::popBackStack,
                    )
                }
            }
        }
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
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                }
            } else {
                IconButton(onClick = onMenu) {
                    Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.action_open_menu))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SemsNavigationBar(
    selected: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
) {
    NavigationBar {
        BottomDestination.entries.forEach { item ->
            val isSelected = item == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(item) },
                icon = {
                    Icon(
                        painter = painterResource(if (isSelected) item.selectedIcon else item.icon),
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(item.label)) },
                // Active item in a Primary Container pill (design spec).
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun SemsDrawerContent(
    account: DrawerAccount?,
    selected: DrawerDestination?,
    onSelect: (DrawerDestination) -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet {
        // Header: SEMS logo and the organization (Figma v2, M06).
        Row(
            modifier = Modifier.padding(horizontal = SemsSpacing.lg, vertical = SemsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SemsLogoMark()
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
        HorizontalDivider(Modifier.padding(horizontal = SemsSpacing.lg))
        Spacer(Modifier.height(SemsSpacing.xs))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            DrawerDestination.entries
                .filter { !it.requiresOrgAdmin || account?.canSeeAdminEntries == true }
                .forEach { item ->
                val isSelected = item == selected
                NavigationDrawerItem(
                    label = { Text(stringResource(item.label)) },
                    icon = {
                        Icon(painterResource(if (isSelected) item.selectedIcon else item.icon), contentDescription = null)
                    },
                    selected = isSelected,
                    onClick = { onSelect(item) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }

        // Footer: who is logged in, with the logout action.
        HorizontalDivider(Modifier.padding(horizontal = SemsSpacing.lg))
        Row(
            modifier = Modifier.padding(start = SemsSpacing.lg, end = SemsSpacing.xs, top = SemsSpacing.sm, bottom = SemsSpacing.md),
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

/** Membership role (as the web app's MembersCard) or, without organization, the IAM account role. */
@StringRes
private fun DrawerAccount?.roleLabel(): Int = when (this?.membershipRole) {
    "ORG_ADMIN" -> R.string.membership_role_org_admin
    "SUPERVISOR" -> R.string.membership_role_supervisor
    "OPERATOR" -> R.string.membership_role_operator
    null -> if (this?.isPlatformAdmin == true) R.string.role_admin else R.string.role_staff
    else -> R.string.membership_role_unknown
}
