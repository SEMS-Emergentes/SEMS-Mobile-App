package pe.edu.upc.sems.shared.session

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferencesSerializer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSessionManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var manager: DataStoreSessionManager

    private val tokens = AuthTokens(token = "jwt-1", refreshToken = "refresh-1")
    private val user = SessionUser(
        userId = "user-1",
        emailAddress = "ana@example.com",
        fullName = "Ana Pérez",
        roles = setOf("ORG_ADMIN"),
        segment = "RETAIL_CHAIN",
    )
    private val site = SelectedSite(organizationId = "org-1", siteId = "site-1", siteName = "Local San Isidro")

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        file = File(tmp.root, "test_session.preferences_pb")
        manager = newManager()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // The app uses PreferenceDataStoreFactory.create, backed by java.io.File. On the Windows JVM,
    // File.renameTo cannot replace an existing file, so every second write fails in unit tests
    // (on Android it works). Okio's atomic move does replace it; same preferences format and serializer.
    private fun newManager() = DataStoreSessionManager(
        DataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = { file.toOkioPath() },
            ),
            scope = scope,
        ),
    )

    /**
     * Simula el reinicio de la app: un DataStore nuevo sobre el mismo archivo.
     *
     * Espera a que el scope termine en vez de solo cancelarlo porque DataStore
     * no suelta el archivo hasta que su corrutina acaba, y abrir el segundo
     * antes revienta con "multiple DataStores active for the same file". Con dos
     * pruebas que reinician, cual de las dos fallaba dependia del orden en que
     * JUnit decidiera correrlas.
     */
    private suspend fun restart(): DataStoreSessionManager {
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return newManager()
    }

    @Test
    fun `no session when nothing was saved`() = runBlocking {
        assertNull(manager.session.first())
    }

    @Test
    fun `saveLogin stores tokens and user without a selected site`() = runBlocking {
        manager.saveLogin(tokens, user)
        assertEquals(Session(tokens, user, selectedSite = null), manager.session.first())
    }

    @Test
    fun `nullable user fields are stored as null`() = runBlocking {
        val minimal = user.copy(fullName = null, segment = null, roles = emptySet())
        manager.saveLogin(tokens, minimal)
        assertEquals(minimal, manager.session.first()?.user)
    }

    @Test
    fun `updateTokens replaces only the tokens`() = runBlocking {
        manager.saveLogin(tokens, user)
        manager.selectSite(site)
        val refreshed = AuthTokens("jwt-2", "refresh-2")

        manager.updateTokens(refreshed)

        assertEquals(Session(refreshed, user, site), manager.session.first())
    }

    @Test
    fun `selected site survives a new login of the same user`() = runBlocking {
        manager.saveLogin(tokens, user)
        manager.selectSite(site)

        manager.saveLogin(AuthTokens("jwt-2", "refresh-2"), user)

        assertEquals(site, manager.session.first()?.selectedSite)
    }

    @Test
    fun `selected site is dropped when another user logs in`() = runBlocking {
        manager.saveLogin(tokens, user)
        manager.selectSite(site)

        manager.saveLogin(tokens, user.copy(userId = "user-2"))

        assertNull(manager.session.first()?.selectedSite)
    }

    @Test
    fun `updateTokens and selectSite do nothing when logged out`() = runBlocking {
        manager.updateTokens(tokens)
        manager.selectSite(site)
        assertNull(manager.session.first())
    }

    @Test
    fun `clear removes the whole session`() = runBlocking {
        manager.saveLogin(tokens, user)
        manager.selectSite(site)

        manager.clear()

        assertNull(manager.session.first())
    }

    @Test
    fun `session persists across instances`() = runBlocking {
        manager.saveLogin(tokens, user)
        manager.selectSite(site)

        assertEquals(Session(tokens, user, site), restart().session.first())
    }

    private val membership = SessionMembership("org-1", "Cadena Flores S.A.C.", "SUPERVISOR", "site-1")

    @Test
    fun `membership is stored and survives a restart`() = runBlocking {
        manager.saveLogin(tokens, user)
        manager.saveMembership(membership)

        assertEquals(membership, restart().session.first()?.membership)
    }

    @Test
    fun `another user does not inherit the membership`() = runBlocking {
        manager.saveLogin(tokens, user)
        manager.saveMembership(membership)

        manager.saveLogin(tokens, user.copy(userId = "user-2"))

        assertNull(manager.session.first()?.membership)
    }

    @Test
    fun `losing the organization or changing it drops the selected site`() = runBlocking {
        manager.saveLogin(tokens, user)
        manager.saveMembership(membership.copy(organizationId = "org-1"))
        manager.selectSite(site)
        manager.saveMembership(membership.copy(organizationId = "org-2"))
        assertNull(manager.session.first()?.selectedSite)

        manager.selectSite(site.copy(organizationId = "org-2"))
        manager.saveMembership(null)
        assertNull(manager.session.first()?.membership)
        assertNull(manager.session.first()?.selectedSite)
    }

    @Test
    fun `saveMembership does nothing when logged out`() = runBlocking {
        manager.saveMembership(membership)
        assertNull(manager.session.first())
    }
}
