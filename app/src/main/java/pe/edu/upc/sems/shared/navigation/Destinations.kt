package pe.edu.upc.sems.shared.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import pe.edu.upc.sems.R

/** Destinations of the bottom NavigationBar. */
enum class BottomDestination(
    val route: Any,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    DASHBOARD(DashboardRoute, R.string.nav_dashboard, R.drawable.ic_home, R.drawable.ic_home_filled),
    MONITORING(MonitoringRoute, R.string.nav_monitoring, R.drawable.ic_monitoring, R.drawable.ic_monitoring_filled),
    ALERTS(AlertsRoute, R.string.nav_alerts, R.drawable.ic_notifications, R.drawable.ic_notifications_filled),
    ANALYTICS(AnalyticsRoute, R.string.nav_analytics, R.drawable.ic_analytics, R.drawable.ic_analytics_filled),
}

/** Destinations of the ModalNavigationDrawer, in the web app's order (logout is handled separately). */
enum class DrawerDestination(
    val route: Any,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    /** Solo visible para ORG_ADMIN. Se aplica al construir el menu, en MainNavHost. */
    val requiresOrgAdmin: Boolean = false,
) {
    DEVICES(DevicesRoute, R.string.nav_devices, R.drawable.ic_devices, R.drawable.ic_devices_filled),
    REPORTS(ReportsRoute, R.string.nav_reports, R.drawable.ic_picture_as_pdf, R.drawable.ic_picture_as_pdf_filled),
    SUBSCRIPTION(
        SubscriptionRoute, R.string.nav_subscription,
        R.drawable.ic_credit_card, R.drawable.ic_credit_card_filled,
        requiresOrgAdmin = true,
    ),
    // The organization is managed inside "Locales", as in the web app.
    SITES(SitesRoute, R.string.nav_sites, R.drawable.ic_storefront, R.drawable.ic_storefront_filled),
    SETTINGS(SettingsRoute, R.string.nav_settings, R.drawable.ic_settings, R.drawable.ic_settings_filled),
}

internal fun NavDestination?.isRoute(route: Any): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true

/** For routes with arguments (data classes), which have no single instance to compare. */
internal fun NavDestination?.isRoute(route: kotlin.reflect.KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true

internal fun NavDestination?.bottomDestination(): BottomDestination? =
    BottomDestination.entries.firstOrNull { isRoute(it.route) }

internal fun NavDestination?.drawerDestination(): DrawerDestination? =
    DrawerDestination.entries.firstOrNull { isRoute(it.route) }
