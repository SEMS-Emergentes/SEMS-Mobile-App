package pe.edu.upc.sems.shared.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SessionManager] backed by DataStore Preferences.
 * Stored unencrypted in app-private storage and excluded from backups (see res/xml backup rules).
 */
@Singleton
class DataStoreSessionManager @Inject constructor(
    @param:SessionDataStore private val dataStore: DataStore<Preferences>,
) : SessionManager {

    override val session: Flow<Session?> = dataStore.data
        // A corrupted or unreadable file means "logged out", not a crash.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSession() }
        .distinctUntilChanged()

    override suspend fun saveLogin(tokens: AuthTokens, user: SessionUser) {
        dataStore.edit { prefs ->
            val sameUser = prefs[Keys.USER_ID] == user.userId
            prefs.writeTokens(tokens)
            prefs[Keys.USER_ID] = user.userId
            prefs[Keys.EMAIL_ADDRESS] = user.emailAddress
            prefs.putOrRemove(Keys.FULL_NAME, user.fullName)
            prefs[Keys.ROLES] = user.roles
            prefs.putOrRemove(Keys.SEGMENT, user.segment)
            if (!sameUser) {
                prefs.removeSelectedSite()
                prefs.removeMembership()
            }
        }
    }

    override suspend fun updateTokens(tokens: AuthTokens) {
        dataStore.edit { prefs ->
            if (prefs[Keys.USER_ID] != null) prefs.writeTokens(tokens)
        }
    }

    override suspend fun selectSite(site: SelectedSite) {
        dataStore.edit { prefs ->
            if (prefs[Keys.USER_ID] == null) return@edit
            prefs[Keys.ORGANIZATION_ID] = site.organizationId
            prefs[Keys.SITE_ID] = site.siteId
            prefs[Keys.SITE_NAME] = site.siteName
        }
    }

    override suspend fun saveMembership(membership: SessionMembership?) {
        dataStore.edit { prefs ->
            if (prefs[Keys.USER_ID] == null) return@edit
            if (membership == null) {
                prefs.removeMembership()
                prefs.removeSelectedSite()
                return@edit
            }
            prefs[Keys.MEMBERSHIP_ORGANIZATION_ID] = membership.organizationId
            prefs[Keys.MEMBERSHIP_ORGANIZATION_NAME] = membership.organizationName
            prefs[Keys.MEMBERSHIP_ROLE] = membership.role
            prefs.putOrRemove(Keys.MEMBERSHIP_SITE_ID, membership.siteId)
            if (prefs[Keys.ORGANIZATION_ID] != null && prefs[Keys.ORGANIZATION_ID] != membership.organizationId) {
                prefs.removeSelectedSite()
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSession(): Session? {
        val token = this[Keys.TOKEN] ?: return null
        val refreshToken = this[Keys.REFRESH_TOKEN] ?: return null
        val userId = this[Keys.USER_ID] ?: return null
        val organizationId = this[Keys.ORGANIZATION_ID]
        val siteId = this[Keys.SITE_ID]
        val siteName = this[Keys.SITE_NAME]
        return Session(
            tokens = AuthTokens(token, refreshToken),
            user = SessionUser(
                userId = userId,
                emailAddress = this[Keys.EMAIL_ADDRESS].orEmpty(),
                fullName = this[Keys.FULL_NAME],
                roles = this[Keys.ROLES].orEmpty(),
                segment = this[Keys.SEGMENT],
            ),
            selectedSite = if (organizationId != null && siteId != null && siteName != null) {
                SelectedSite(organizationId, siteId, siteName)
            } else {
                null
            },
            membership = toMembership(),
        )
    }

    private fun Preferences.toMembership(): SessionMembership? {
        val organizationId = this[Keys.MEMBERSHIP_ORGANIZATION_ID] ?: return null
        return SessionMembership(
            organizationId = organizationId,
            organizationName = this[Keys.MEMBERSHIP_ORGANIZATION_NAME].orEmpty(),
            role = this[Keys.MEMBERSHIP_ROLE].orEmpty(),
            siteId = this[Keys.MEMBERSHIP_SITE_ID],
        )
    }

    private fun MutablePreferences.writeTokens(tokens: AuthTokens) {
        this[Keys.TOKEN] = tokens.token
        this[Keys.REFRESH_TOKEN] = tokens.refreshToken
    }

    private fun MutablePreferences.removeSelectedSite() {
        remove(Keys.ORGANIZATION_ID)
        remove(Keys.SITE_ID)
        remove(Keys.SITE_NAME)
    }

    private fun MutablePreferences.removeMembership() {
        remove(Keys.MEMBERSHIP_ORGANIZATION_ID)
        remove(Keys.MEMBERSHIP_ORGANIZATION_NAME)
        remove(Keys.MEMBERSHIP_ROLE)
        remove(Keys.MEMBERSHIP_SITE_ID)
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL_ADDRESS = stringPreferencesKey("email_address")
        val FULL_NAME = stringPreferencesKey("full_name")
        val ROLES = stringSetPreferencesKey("roles")
        val SEGMENT = stringPreferencesKey("segment")
        val ORGANIZATION_ID = stringPreferencesKey("organization_id")
        val SITE_ID = stringPreferencesKey("site_id")
        val SITE_NAME = stringPreferencesKey("site_name")
        val MEMBERSHIP_ORGANIZATION_ID = stringPreferencesKey("membership_organization_id")
        val MEMBERSHIP_ORGANIZATION_NAME = stringPreferencesKey("membership_organization_name")
        val MEMBERSHIP_ROLE = stringPreferencesKey("membership_role")
        val MEMBERSHIP_SITE_ID = stringPreferencesKey("membership_site_id")
    }
}
