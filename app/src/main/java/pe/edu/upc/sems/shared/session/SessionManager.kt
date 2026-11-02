package pe.edu.upc.sems.shared.session

import kotlinx.coroutines.flow.Flow

/** Single source of truth for the logged-in session. */
interface SessionManager {

    /** Current session, or null when logged out. Emits on every change. */
    val session: Flow<Session?>

    /**
     * Stores a new login. The selected site is kept only if the same user logs in again;
     * a different user starts without a selected site.
     */
    suspend fun saveLogin(tokens: AuthTokens, user: SessionUser)

    /** Replaces the tokens after a refresh. Does nothing when logged out. */
    suspend fun updateTokens(tokens: AuthTokens)

    /** Stores the site chosen in the site selector (null: no active site). Does nothing when logged out. */
    suspend fun selectSite(site: SelectedSite?)

    /**
     * Stores the user's organization and role (null: no organization). Does nothing when logged out.
     * A selected site from another organization is dropped.
     */
    suspend fun saveMembership(membership: SessionMembership?)

    /** Removes everything (logout or 401). */
    suspend fun clear()
}
