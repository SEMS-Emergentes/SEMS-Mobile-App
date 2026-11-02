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
import pe.edu.upc.sems.organizations.domain.model.BusinessType
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.TariffCategory
import pe.edu.upc.sems.organizations.domain.model.ZoneType
import pe.edu.upc.sems.organizations.domain.usecase.RefreshMembershipUseCase
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.organizations.presentation.organizationform.EditOrganizationViewModel
import pe.edu.upc.sems.organizations.presentation.sitedetail.SiteDetailViewModel
import pe.edu.upc.sems.organizations.presentation.siteform.SiteFormMode
import pe.edu.upc.sems.organizations.presentation.siteform.SiteFormViewModel
import pe.edu.upc.sems.organizations.presentation.sites.SitesUiState
import pe.edu.upc.sems.organizations.presentation.sites.SitesViewModel
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.SelectedSite
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionMembership
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.shared.util.Formatters
import pe.edu.upc.sems.testing.MainDispatcherRule

/** "Locales", "Detalle del local", "Editar local" and "Editar organización". */
@OptIn(ExperimentalCoroutinesApi::class)
class SitesAndDetailTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val repository = FakeOrganizationRepository()

    private suspend fun signInAs(role: MembershipRole) {
        sessionManager.saveMembership(SessionMembership("org-1", "Cadena Flores S.A.C.", role.name, null))
        repository.myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(role))
    }

    // region Locales

    @Test
    fun `sites screen shows getting started when there is no organization`() = runTest(mainRule.dispatcher) {
        val viewModel = SitesViewModel(repository, sessionManager)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(SitesUiState.NoOrganization, viewModel.uiState.value)
    }

    @Test
    fun `sites screen lists the sites and marks the active one`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.ORG_ADMIN)
        repository.listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
        sessionManager.selectSite(SelectedSite("org-1", "site-1", "Local San Isidro"))
        val viewModel = SitesViewModel(repository, sessionManager)
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value as SitesUiState.Success
        assertEquals("Cadena Flores S.A.C.", state.organization.legalName)
        assertEquals(listOf("site-1"), state.sites.map { it.id })
        assertEquals("site-1", state.activeSiteId)
        assertTrue(state.canManageOrganization)
    }

    @Test
    fun `supervisors cannot create sites nor edit the company`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.SUPERVISOR)
        val viewModel = SitesViewModel(repository, sessionManager)
        viewModel.refresh()
        advanceUntilIdle()

        assertFalse((viewModel.uiState.value as SitesUiState.Success).canManageOrganization)
    }

    @Test
    fun `a loading failure shows an error`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.ORG_ADMIN)
        repository.listSitesResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = SitesViewModel(repository, sessionManager)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(SitesUiState.Error(FormError.NO_CONNECTION), viewModel.uiState.value)
    }

    // endregion

    // region Detalle del local

    private fun detailViewModel() = SiteDetailViewModel(repository, sessionManager).apply { start("site-1") }

    @Test
    fun `detail loads the site and its zones`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.OPERATOR)
        repository.listZonesResult = ApiResult.Success(listOf(FakeOrganizationRepository.ZONE))
        val viewModel = detailViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Local San Isidro", state.site?.name)
        assertEquals(listOf("Refrigeración"), state.zones.map { it.name })
        // OPERATOR: read only.
        assertFalse(state.canEdit)
        viewModel.onAddZone()
        assertNull(viewModel.uiState.value.zoneEditor)
    }

    @Test
    fun `supervisors can add a zone, as the backend allows`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.SUPERVISOR)
        val viewModel = detailViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canEdit)

        viewModel.onAddZone()
        viewModel.onSaveZone()
        assertEquals(OrgFieldError.REQUIRED, viewModel.uiState.value.zoneEditor?.nameError)

        viewModel.onZoneNameChange("Sala de ventas")
        viewModel.onZoneTypeChange(ZoneType.SALES_FLOOR)
        repository.listZonesResult = ApiResult.Success(listOf(FakeOrganizationRepository.ZONE))
        viewModel.onSaveZone()
        advanceUntilIdle()

        val (siteId, draft) = repository.createZoneCalls.single()
        assertEquals("site-1", siteId)
        assertEquals("Sala de ventas", draft.name)
        assertEquals(ZoneType.SALES_FLOOR, draft.zoneType)
        assertFalse(draft.operatesOffHours)
        assertNull(viewModel.uiState.value.zoneEditor)
        assertEquals(1, viewModel.uiState.value.zones.size)
    }

    @Test
    fun `a duplicated zone name keeps the editor open with a conflict`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.ORG_ADMIN)
        repository.createZoneResult = ApiResult.Failure(ApiError.Http(409, "a zone with that name already exists in this site"))
        val viewModel = detailViewModel()
        advanceUntilIdle()

        viewModel.onAddZone()
        viewModel.onZoneNameChange("Refrigeración")
        viewModel.onSaveZone()
        advanceUntilIdle()

        assertEquals(FormError.CONFLICT, viewModel.uiState.value.zoneEditor?.error)
        assertFalse(viewModel.uiState.value.zoneEditor!!.isSaving)
    }

    @Test
    fun `an existing zone is edited and can be archived`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.ORG_ADMIN)
        val viewModel = detailViewModel()
        advanceUntilIdle()

        viewModel.onZoneClick(FakeOrganizationRepository.ZONE)
        viewModel.onZoneOffHoursChange(false)
        viewModel.onSaveZone()
        advanceUntilIdle()
        assertEquals("zone-1", repository.updateZoneCalls.single().first)
        assertFalse(repository.updateZoneCalls.single().second.operatesOffHours)

        viewModel.onZoneClick(FakeOrganizationRepository.ZONE)
        viewModel.onArchiveZone()
        advanceUntilIdle()
        assertEquals(listOf("zone-1"), repository.archiveZoneCalls)
        assertNull(viewModel.uiState.value.zoneEditor)
    }

    @Test
    fun `archiving the site asks for confirmation first`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.ORG_ADMIN)
        val viewModel = detailViewModel()
        advanceUntilIdle()

        viewModel.onArchiveSiteRequest()
        assertTrue(viewModel.uiState.value.confirmingArchive)
        assertTrue(repository.archiveSiteCalls.isEmpty())

        viewModel.onArchiveSiteConfirm()
        advanceUntilIdle()
        assertEquals(listOf("site-1"), repository.archiveSiteCalls)
        assertTrue(viewModel.uiState.value.archived)
    }

    @Test
    fun `archiving the active site moves the session to the next site, like the web app`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.ORG_ADMIN)
        sessionManager.selectSite(SelectedSite("org-1", "site-1", "Local San Isidro"))
        val miraflores = FakeOrganizationRepository.SITE.copy(id = "site-2", name = "Local Miraflores", siteCode = "LIM-002")
        repository.listSitesResult = ApiResult.Success(listOf(miraflores))
        val viewModel = detailViewModel()
        advanceUntilIdle()

        viewModel.onArchiveSiteRequest()
        viewModel.onArchiveSiteConfirm()
        advanceUntilIdle()
        assertEquals("site-2", sessionManager.session.value?.selectedSite?.siteId)

        // Archiving the last site leaves no active site.
        repository.listSitesResult = ApiResult.Success(emptyList())
        val last = SiteDetailViewModel(repository, sessionManager).apply { start("site-2") }
        advanceUntilIdle()
        last.onArchiveSiteRequest()
        last.onArchiveSiteConfirm()
        advanceUntilIdle()
        assertNull(sessionManager.session.value?.selectedSite)
    }

    // endregion

    // region Editar local / organización

    @Test
    fun `editing a site prefills the form, locks the code and updates`() = runTest(mainRule.dispatcher) {
        repository.getSiteResult = ApiResult.Success(FakeOrganizationRepository.SITE.copy(contractedPowerKw = 95.5, floorAreaM2 = 600.0))
        val viewModel = SiteFormViewModel(repository, sessionManager).apply { start(SiteFormMode.Edit("site-1")) }
        advanceUntilIdle()

        val loaded = viewModel.uiState.value
        assertTrue(loaded.isEdit)
        assertEquals("LIM-001", loaded.siteCode)
        assertEquals("95,5", loaded.contractedPowerKw)
        assertEquals("600", loaded.floorAreaM2)

        viewModel.onSiteCodeChange("OTRO")
        viewModel.onTariffCategoryChange(TariffCategory.BT4)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals("LIM-001", viewModel.uiState.value.siteCode)
        val (siteId, draft) = repository.updateSiteCalls.single()
        assertEquals("site-1", siteId)
        assertEquals(TariffCategory.BT4, draft.tariffCategory)
        assertTrue(repository.createSiteCalls.isEmpty())
    }

    @Test
    fun `renaming the active site keeps its cached name in sync`() = runTest(mainRule.dispatcher) {
        sessionManager.selectSite(SelectedSite("org-1", "site-1", "Local San Isidro"))
        repository.updateSiteResult = ApiResult.Success(FakeOrganizationRepository.SITE.copy(name = "Local San Isidro Centro"))
        val viewModel = SiteFormViewModel(repository, sessionManager).apply { start(SiteFormMode.Edit("site-1")) }
        advanceUntilIdle()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals("Local San Isidro Centro", sessionManager.session.value?.selectedSite?.siteName)
    }

    @Test
    fun `editing the organization keeps the RUC and refreshes the drawer name`() = runTest(mainRule.dispatcher) {
        signInAs(MembershipRole.ORG_ADMIN)
        repository.updateOrganizationResult = ApiResult.Success(FakeOrganizationRepository.ORGANIZATION.copy(legalName = "Cadena Flores Perú S.A.C."))
        val viewModel = EditOrganizationViewModel(repository, sessionManager, RefreshMembershipUseCase(repository, sessionManager))
        advanceUntilIdle()
        assertEquals("20123456789", viewModel.uiState.value.taxId)

        viewModel.onLegalNameChange("Cadena Flores Perú S.A.C.")
        viewModel.onBusinessTypeChange(BusinessType.DEPARTMENT_STORE)
        repository.myOrganizationResult = ApiResult.Success(
            FakeOrganizationRepository.access(MembershipRole.ORG_ADMIN).let {
                it.copy(organization = it.organization.copy(legalName = "Cadena Flores Perú S.A.C."))
            },
        )
        viewModel.onSubmit()
        advanceUntilIdle()

        val (organizationId, details) = repository.updateOrganizationCalls.single()
        assertEquals("org-1", organizationId)
        assertEquals(BusinessType.DEPARTMENT_STORE, details.businessType)
        assertEquals("Cadena Flores Perú S.A.C.", sessionManager.session.value?.membership?.organizationName)
        assertTrue(viewModel.uiState.value.done)
    }

    // endregion

    @Test
    fun `numbers follow the project format`() {
        assertEquals("95 kW", Formatters.kilowatts(95.0))
        assertEquals("95,5 kW", Formatters.kilowatts(95.5))
        assertEquals("1 250 kW", Formatters.kilowatts(1250.0))
        assertEquals("600 m²", Formatters.squareMeters(600.0))
        assertEquals("1250,5", Formatters.editable(1250.5))
    }
}
