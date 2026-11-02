package pe.edu.upc.sems.organizations.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.presentation.siteselector.SiteSelectorViewModel
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.SelectedSite
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionMembership
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

/** "Selector de local": the active site lives in the session and is always one the user reaches. */
@OptIn(ExperimentalCoroutinesApi::class)
class SiteSelectorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val repository = FakeOrganizationRepository()

    private val sanIsidro = FakeOrganizationRepository.SITE
    private val miraflores = sanIsidro.copy(id = "site-2", name = "Local Miraflores", siteCode = "LIM-002")

    private fun selected() = sessionManager.session.value?.selectedSite

    private suspend fun membership(role: String = "ORG_ADMIN", siteId: String? = null) {
        sessionManager.saveMembership(SessionMembership("org-1", "Cadena Flores S.A.C.", role, siteId))
    }

    @Test
    fun `with a single site it is selected and stays fixed`() = runTest(mainRule.dispatcher) {
        membership()
        repository.listSitesResult = ApiResult.Success(listOf(sanIsidro))
        val viewModel = SiteSelectorViewModel(repository, sessionManager)
        advanceUntilIdle()

        assertEquals(SelectedSite("org-1", "site-1", "Local San Isidro"), selected())
        assertEquals("Local San Isidro", viewModel.uiState.value.selected?.siteName)
        assertFalse(viewModel.uiState.value.canChange)

        // Nothing to choose: tapping does not open the selector.
        viewModel.onOpen()
        assertFalse(viewModel.uiState.value.isSheetOpen)
    }

    @Test
    fun `with several sites the user picks one and the session keeps it`() = runTest(mainRule.dispatcher) {
        membership()
        repository.listSitesResult = ApiResult.Success(listOf(sanIsidro, miraflores))
        val viewModel = SiteSelectorViewModel(repository, sessionManager)
        advanceUntilIdle()
        assertEquals("site-1", selected()?.siteId)
        assertTrue(viewModel.uiState.value.canChange)

        viewModel.onOpen()
        assertTrue(viewModel.uiState.value.isSheetOpen)
        viewModel.onSelect(miraflores)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSheetOpen)
        assertEquals(SelectedSite("org-1", "site-2", "Local Miraflores"), selected())
        assertEquals("Local Miraflores", viewModel.uiState.value.selected?.siteName)
    }

    @Test
    fun `a supervisor starts on the site of their membership`() = runTest(mainRule.dispatcher) {
        membership(role = "SUPERVISOR", siteId = "site-2")
        repository.listSitesResult = ApiResult.Success(listOf(sanIsidro, miraflores))
        SiteSelectorViewModel(repository, sessionManager)
        advanceUntilIdle()

        assertEquals("site-2", selected()?.siteId)
    }

    @Test
    fun `an active site the user no longer reaches is replaced`() = runTest(mainRule.dispatcher) {
        membership()
        sessionManager.selectSite(SelectedSite("org-1", "archived-site", "Local cerrado"))
        repository.listSitesResult = ApiResult.Success(listOf(sanIsidro))
        SiteSelectorViewModel(repository, sessionManager)
        advanceUntilIdle()

        assertEquals("site-1", selected()?.siteId)
    }

    @Test
    fun `a valid choice is kept, and a renamed site updates its cached name`() = runTest(mainRule.dispatcher) {
        membership()
        sessionManager.selectSite(SelectedSite("org-1", "site-2", "Local Miraflores"))
        repository.listSitesResult = ApiResult.Success(listOf(sanIsidro, miraflores.copy(name = "Local Miraflores Centro")))
        SiteSelectorViewModel(repository, sessionManager)
        advanceUntilIdle()

        assertEquals(SelectedSite("org-1", "site-2", "Local Miraflores Centro"), selected())
    }

    @Test
    fun `offline the cached active site is still shown`() = runTest(mainRule.dispatcher) {
        membership()
        sessionManager.selectSite(SelectedSite("org-1", "site-1", "Local San Isidro"))
        repository.listSitesResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = SiteSelectorViewModel(repository, sessionManager)
        advanceUntilIdle()

        assertEquals("Local San Isidro", viewModel.uiState.value.selected?.siteName)
        assertNull(viewModel.uiState.value.sites)
        assertFalse(viewModel.uiState.value.canChange)
        assertEquals("site-1", selected()?.siteId)
    }

    @Test
    fun `without organization or sites there is no active site`() = runTest(mainRule.dispatcher) {
        val viewModel = SiteSelectorViewModel(repository, sessionManager)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selected)
        assertTrue(repository.listSitesCalls.isEmpty())

        membership()
        repository.listSitesResult = ApiResult.Success(emptyList())
        advanceUntilIdle()
        assertNull(selected())
        assertFalse(viewModel.uiState.value.canChange)
    }

    @Test
    fun `a site created later is picked up when a main screen is shown again`() = runTest(mainRule.dispatcher) {
        membership()
        repository.listSitesResult = ApiResult.Success(listOf(sanIsidro))
        val viewModel = SiteSelectorViewModel(repository, sessionManager)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canChange)

        repository.listSitesResult = ApiResult.Success(listOf(sanIsidro, miraflores))
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canChange)
        assertEquals("site-1", selected()?.siteId)
    }
}
