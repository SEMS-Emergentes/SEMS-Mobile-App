package pe.edu.upc.sems.shared.session

/** Logged-in state persisted between app launches. */
data class Session(
    val tokens: AuthTokens,
    val user: SessionUser,
    /** Site the user is working on; null until one is chosen. Sent with site-scoped queries. */
    val selectedSite: SelectedSite?,
)

/** JWT pair returned by `POST /auth/login` and `POST /auth/refresh`. */
data class AuthTokens(
    val token: String,
    val refreshToken: String,
)

/** User data returned by the login. */
data class SessionUser(
    val userId: String,
    val emailAddress: String,
    val fullName: String?,
    /** Account roles from IAM. Menu permissions come from organization memberships, not from here. */
    val roles: Set<String>,
    /** Raw IAM value (`RETAIL_CHAIN` | `INDEPENDENT`); the iam context maps it to its own type. */
    val segment: String?,
)

/** Site chosen in the site selector. The name is cached to always show "which site am I in" right away. */
data class SelectedSite(
    val organizationId: String,
    val siteId: String,
    val siteName: String,
)
