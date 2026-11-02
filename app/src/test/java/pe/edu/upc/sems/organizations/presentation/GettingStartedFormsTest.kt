package pe.edu.upc.sems.organizations.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.domain.model.BusinessType
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.TariffCategory
import pe.edu.upc.sems.organizations.domain.usecase.RefreshMembershipUseCase
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.common.parseDecimal
import pe.edu.upc.sems.organizations.presentation.gettingstarted.GettingStartedViewModel
import pe.edu.upc.sems.organizations.presentation.organizationform.RegisterOrganizationViewModel
import pe.edu.upc.sems.organizations.presentation.siteform.SiteFormMode
import pe.edu.upc.sems.organizations.presentation.siteform.SiteFormViewModel
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.SelectedSite
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionMembership
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

/** "Primeros pasos": register the company, add the first site, and the card that guides both. */
@OptIn(ExperimentalCoroutinesApi::class)
class GettingStartedFormsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val repository = FakeOrganizationRepository()

    private fun session() = sessionManager.session.value

    // region Register the company

    private val registerViewModel by lazy {
        RegisterOrganizationViewModel(repository, sessionManager, RefreshMembershipUseCase(repository, sessionManager))
    }

    @Test
    fun `legal name is required and the RUC needs 11 digits`() = runTest(mainRule.dispatcher) {
        registerViewModel.onTaxIdChange("20 123-456")
        registerViewModel.onSubmit()
        advanceUntilIdle()

        val state = registerViewModel.uiState.value
        assertEquals("20123456", state.taxId)
        assertEquals(OrgFieldError.REQUIRED, state.legalNameError)
        assertEquals(OrgFieldError.INVALID_RUC, state.taxIdError)
        assertTrue(repository.createOrganizationCalls.isEmpty())
    }

    @Test
    fun `RUC input keeps only 11 digits`() {
        registerViewModel.onTaxIdChange("201234567890123")
        assertEquals("20123456789", registerViewModel.uiState.value.taxId)
    }

    @Test
    fun `registering creates the organization for the user and loads the new membership`() = runTest(mainRule.dispatcher) {
        repository.myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(MembershipRole.ORG_ADMIN))
        registerViewModel.onLegalNameChange("Cadena Flores S.A.C.")
        registerViewModel.onTaxIdChange("20123456789")
        registerViewModel.onBusinessTypeChange(BusinessType.RESTAURANT)
        registerViewModel.onSubmit()
        advanceUntilIdle()

        val (owner, organization) = repository.createOrganizationCalls.single()
        assertEquals("user-1", owner)
        assertEquals("Cadena Flores S.A.C.", organization.legalName)
        assertEquals(BusinessType.RESTAURANT, organization.businessType)
        assertEquals(SessionMembership("org-1", "Cadena Flores S.A.C.", "ORG_ADMIN", null), session()?.membership)
        assertTrue(registerViewModel.uiState.value.done)
    }

    @Test
    fun `a RUC already registered shows a conflict`() = runTest(mainRule.dispatcher) {
        repository.createOrganizationResult = ApiResult.Failure(ApiError.Http(409, "an organization with that tax_id already exists"))
        registerViewModel.onLegalNameChange("Cadena Flores S.A.C.")
        registerViewModel.onTaxIdChange("20123456789")
        registerViewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(FormError.CONFLICT, registerViewModel.uiState.value.error)
        assertFalse(registerViewModel.uiState.value.done)
        assertNull(session()?.membership)
    }

    // endregion

    // region Add the first site

    private val siteViewModel by lazy { SiteFormViewModel(repository, sessionManager).apply { start(SiteFormMode.Create("org-1")) } }

    private fun fillSite(power: String = "95,5", area: String = "") {
        siteViewModel.onSiteCodeChange("lim-001")
        siteViewModel.onNameChange("Local San Isidro")
        siteViewModel.onDistrictChange("San Isidro")
        siteViewModel.onFloorAreaChange(area)
        siteViewModel.onContractedPowerChange(power)
        siteViewModel.onTariffCategoryChange(TariffCategory.BT3)
    }

    @Test
    fun `site needs a code, a name and a positive contracted power`() = runTest(mainRule.dispatcher) {
        siteViewModel.onContractedPowerChange("0")
        siteViewModel.onFloorAreaChange("abc")
        siteViewModel.onSubmit()
        advanceUntilIdle()

        val state = siteViewModel.uiState.value
        assertEquals(OrgFieldError.REQUIRED, state.siteCodeError)
        assertEquals(OrgFieldError.REQUIRED, state.nameError)
        assertEquals(OrgFieldError.MUST_BE_POSITIVE, state.contractedPowerError)
        assertEquals(OrgFieldError.INVALID_NUMBER, state.floorAreaError)
        assertTrue(repository.createSiteCalls.isEmpty())
    }

    @Test
    fun `decimals accept a comma or a dot`() {
        assertEquals(95.5, parseDecimal(" 95,5 ")!!, 0.0)
        assertEquals(95.5, parseDecimal("95.5")!!, 0.0)
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("95 kW"))
    }

    @Test
    fun `the first site is created and becomes the active site`() = runTest(mainRule.dispatcher) {
        fillSite()
        siteViewModel.onSubmit()
        advanceUntilIdle()

        val (organizationId, draft) = repository.createSiteCalls.single()
        assertEquals("org-1", organizationId)
        assertEquals("LIM-001", draft.siteCode)
        assertEquals(95.5, draft.contractedPowerKw, 0.0)
        assertNull(draft.floorAreaM2)
        assertEquals(TariffCategory.BT3, draft.tariffCategory)
        assertEquals(SelectedSite("org-1", "site-1", "Local San Isidro"), session()?.selectedSite)
        assertTrue(siteViewModel.uiState.value.done)
    }

    @Test
    fun `a new site does not replace the active site`() = runTest(mainRule.dispatcher) {
        val active = SelectedSite("org-1", "site-0", "Local Miraflores")
        sessionManager.selectSite(active)
        fillSite()
        siteViewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(active, session()?.selectedSite)
    }

    @Test
    fun `a duplicated site code shows a conflict`() = runTest(mainRule.dispatcher) {
        repository.createSiteResult = ApiResult.Failure(ApiError.Http(409, "a site with that site_code already exists"))
        fillSite()
        siteViewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(FormError.CONFLICT, siteViewModel.uiState.value.error)
        assertNull(session()?.selectedSite)
    }

    // endregion

    // region Getting started card

    @Test
    fun `without organization only the first step is available`() = runTest(mainRule.dispatcher) {
        val viewModel = GettingStartedViewModel(repository, sessionManager)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isVisible)
        assertFalse(state.hasOrganization)
        assertEquals(0, state.completedSteps)
        assertTrue(state.canManage)
        assertTrue(repository.listSitesCalls.isEmpty())
    }

    @Test
    fun `with organization and no sites the second step is pending, then the card hides`() = runTest(mainRule.dispatcher) {
        sessionManager.saveMembership(SessionMembership("org-1", "Cadena Flores S.A.C.", "ORG_ADMIN", null))
        val viewModel = GettingStartedViewModel(repository, sessionManager)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.completedSteps)
        assertTrue(viewModel.uiState.value.isVisible)

        // A site is created: the session's active site changes and the card reloads.
        repository.listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
        sessionManager.selectSite(SelectedSite("org-1", "site-1", "Local San Isidro"))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.completedSteps)
        assertFalse(viewModel.uiState.value.isVisible)
    }

    @Test
    fun `members who are not ORG_ADMIN cannot add the first site`() = runTest(mainRule.dispatcher) {
        sessionManager.saveMembership(SessionMembership("org-1", "Cadena Flores S.A.C.", "OPERATOR", null))
        val viewModel = GettingStartedViewModel(repository, sessionManager)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canManage)
        assertTrue(viewModel.uiState.value.isVisible)
    }

    @Test
    fun `a failure loading sites keeps the card with a retry`() = runTest(mainRule.dispatcher) {
        sessionManager.saveMembership(SessionMembership("org-1", "Cadena Flores S.A.C.", "ORG_ADMIN", null))
        repository.listSitesResult = ApiResult.Failure(ApiError.Timeout)
        val viewModel = GettingStartedViewModel(repository, sessionManager)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loadFailed)
        assertTrue(viewModel.uiState.value.isVisible)

        repository.listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
        viewModel.retry()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isVisible)
    }

    // endregion
}
