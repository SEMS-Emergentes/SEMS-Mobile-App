package pe.edu.upc.sems.shared.navigation

import androidx.compose.foundation.layout.Spacer
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
import kotlinx.coroutines.launch
import pe.edu.upc.sems.R
import pe.edu.upc.sems.shared.designsystem.SemsSpacing
import pe.edu.upc.sems.shared.designsystem.components.ComingSoonScreen

/**
 * Main graph (logged in): top app bar, bottom NavigationBar for the four main sections and
 * ModalNavigationDrawer for the rest. Drawer screens show "back" and hide the bottom bar.
 */
@Composable
fun MainNavHost(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val bottomDestination = destination.bottomDestination()
    val drawerDestination = destination.drawerDestination()
    // Before the first destination is attached, treat the screen as top level (Home).
    val isTopLevel = drawerDestination == null

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Edge swipe only on top-level screens, where the menu button is shown.
        gesturesEnabled = isTopLevel || drawerState.isOpen,
        drawerContent = {
            SemsDrawerContent(
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
                SemsTopAppBar(
                    title = stringResource(
                        drawerDestination?.label ?: bottomDestination?.label ?: BottomDestination.HOME.label,
                    ),
                    showBack = !isTopLevel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                )
            },
            bottomBar = {
                if (isTopLevel) {
                    SemsNavigationBar(
                        selected = bottomDestination ?: BottomDestination.HOME,
                        onSelect = navController::navigateToBottomDestination,
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.padding(padding),
                enterTransition = semsEnterTransition(),
                exitTransition = semsExitTransition(),
                popEnterTransition = semsEnterTransition(),
                popExitTransition = semsExitTransition(),
            ) {
                // Bottom navigation
                composable<HomeRoute> { ComingSoonScreen() }
                composable<MonitoringRoute> { ComingSoonScreen() }
                composable<AlertsRoute> { ComingSoonScreen() }
                composable<AnalyticsRoute> { ComingSoonScreen() }
                // Drawer
                composable<DevicesRoute> { ComingSoonScreen() }
                composable<SitesRoute> { ComingSoonScreen() }
                composable<OrganizationRoute> { ComingSoonScreen() }
                composable<ReportsRoute> { ComingSoonScreen() }
                composable<SubscriptionRoute> { ComingSoonScreen() }
                composable<SettingsRoute> { ComingSoonScreen() }
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
    selected: DrawerDestination?,
    onSelect: (DrawerDestination) -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(SemsSpacing.sm))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = SemsSpacing.xl, vertical = SemsSpacing.xxs),
        )
        Text(
            text = stringResource(R.string.app_company),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SemsSpacing.xl),
        )
        Spacer(Modifier.height(SemsSpacing.md))
        // TODO(organizations): hide items with requiresOrgAdmin when the membership role is not ORG_ADMIN.
        DrawerDestination.entries.forEach { item ->
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
        HorizontalDivider(Modifier.padding(horizontal = SemsSpacing.xl, vertical = SemsSpacing.xs))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_logout)) },
            icon = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
            selected = false,
            onClick = onLogout,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}
