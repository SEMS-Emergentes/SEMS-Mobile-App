package pe.edu.upc.sems.shared.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (Navigation Compose 2.9). Feature contexts will add their own detail routes.

// region Access graph (logged out)
@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

/** M03. [email] prefills the field with what was typed on the login screen. */
@Serializable
data class ForgotPasswordRoute(val email: String = "")

/** M04. */
@Serializable
data class ResetLinkSentRoute(val email: String)

/**
 * M30. [token] comes with an App Link (`https://<web>/reset-password?token=…`); when null, the user pastes the link.
 */
@Serializable
data class ResetPasswordRoute(val token: String? = null, val email: String? = null)

/** Fallback flow when the backend requires email verification. */
@Serializable
data class VerifyAccountRoute(val email: String)
// endregion

// region Main graph (logged in) — bottom navigation
@Serializable
data object DashboardRoute

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
data object ReportsRoute

@Serializable
data object SubscriptionRoute

@Serializable
data object SettingsRoute
// endregion

// region Main graph (logged in) — full-screen forms (own top bar, no bottom bar)
/** "Registra tu organización" (Primeros pasos). */
@Serializable
data object RegisterOrganizationRoute

/** "Nuevo local". */
@Serializable
data class NewSiteRoute(val organizationId: String)

/** "Detalle del local" (M28): supply data and zones. */
@Serializable
data class SiteDetailRoute(val siteId: String)

/** "Editar local". */
@Serializable
data class EditSiteRoute(val siteId: String)

/** "Agregar equipo". */
@Serializable
data object NewDeviceRoute

/** "Editar equipo". */
@Serializable
data class EditDeviceRoute(val deviceId: String)

/** M08: camera scanner, followed by the meter linking form. */
@Serializable
data object ScanMeterRoute

/** M09: links a serial manually or after scanning. */
@Serializable
data class LinkMeterRoute(val serial: String = "", val model: String? = null)

/** Lecturas reales del medidor en el período seleccionado. */
@Serializable
data class MeterHistoryRoute(val meterId: String)

/** M22: preview of the monthly report of the days from [from] to [to] (ISO dates, both included). */
@Serializable
data class ReportPreviewRoute(val from: String, val to: String)

/** M13: energy detail for one device. */
@Serializable
data class ConsumptionDetailRoute(val deviceId: String)

/** "Editar organización" (ORG_ADMIN). */
@Serializable
data object EditOrganizationRoute
// endregion
