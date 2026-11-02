package pe.edu.upc.sems.shared.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (Navigation Compose 2.9). Feature contexts will add their own detail routes.

// region Access graph (logged out)
@Serializable
data object LoginRoute
// endregion

// region Main graph (logged in) — bottom navigation
@Serializable
data object HomeRoute

@Serializable
data object MonitoringRoute

@Serializable
data object AlertsRoute

@Serializable
data object AnalyticsRoute
// endregion

// region Main graph (logged in) — navigation drawer
@Serializable
data object DevicesRoute

@Serializable
data object SitesRoute

@Serializable
data object OrganizationRoute

@Serializable
data object ReportsRoute

@Serializable
data object SubscriptionRoute

@Serializable
data object SettingsRoute
// endregion
