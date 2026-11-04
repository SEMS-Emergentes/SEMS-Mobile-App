package pe.edu.upc.sems.devices.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.devices.FakeDeviceRepository
import pe.edu.upc.sems.devices.domain.model.ConnectionProtocol
import pe.edu.upc.sems.devices.presentation.deviceform.DeviceFormMode
import pe.edu.upc.sems.devices.presentation.deviceform.DeviceFormViewModel
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.common.OrgFieldError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceFormViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val devices = FakeDeviceRepository()
    private val organizations = FakeOrganizationRepository().apply {
        myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(MembershipRole.ORG_ADMIN))
        listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
        listZonesResult = ApiResult.Success(listOf(FakeOrganizationRepository.ZONE))
    }

    private fun viewModel() = DeviceFormViewModel(devices, organizations, sessionManager)

    @Test
    fun `create loads the sites and the zones of the first site`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Create)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingForm)
        assertEquals("site-1", state.siteId)
        assertEquals(listOf(FakeOrganizationRepository.SITE.id), state.sites.map { it.id })
        assertEquals(listOf(FakeOrganizationRepository.ZONE.id), state.zones.map { it.id })
        assertNull(state.zoneId)
        assertEquals("REFRIGERATOR", state.type)
        assertEquals(ConnectionProtocol.WIFI, state.protocol)
    }

    @Test
    fun `without a site the form cannot be used`() = runTest(mainRule.dispatcher) {
        organizations.listSitesResult = ApiResult.Success(emptyList())
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Create)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasNoSites)
    }

    @Test
    fun `name and code are required`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Create)
        advanceUntilIdle()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(OrgFieldError.REQUIRED, viewModel.uiState.value.nameError)
        assertEquals(OrgFieldError.REQUIRED, viewModel.uiState.value.externalCodeError)
        assertTrue(devices.createCalls.isEmpty())
    }

    @Test
    fun `create sends the form values and finishes`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Create)
        advanceUntilIdle()
        viewModel.onNameChange("Horno eléctrico")
        viewModel.onExternalCodeChange("HOR-001")
        viewModel.onTypeChange("MICROWAVE")
        viewModel.onProtocolChange(ConnectionProtocol.BLUETOOTH)
        viewModel.onZoneChange("zone-1")

        viewModel.onSubmit()
        advanceUntilIdle()

        val (userId, draft) = devices.createCalls.single()
        assertEquals("user-1", userId)
        assertEquals("site-1", draft.siteId)
        assertEquals("zone-1", draft.zoneId)
        assertEquals("Horno eléctrico", draft.name)
        assertEquals("HOR-001", draft.externalCode)
        assertEquals("MICROWAVE", draft.type)
        assertEquals(ConnectionProtocol.BLUETOOTH, draft.protocol)
        assertTrue(viewModel.uiState.value.done)
    }

    @Test
    fun `a duplicate code shows a conflict and keeps the form`() = runTest(mainRule.dispatcher) {
        devices.createResult = ApiResult.Failure(ApiError.Http(409, "device code already exists"))
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Create)
        advanceUntilIdle()
        viewModel.onNameChange("Horno")
        viewModel.onExternalCodeChange("HOR-001")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(FormError.CONFLICT, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.done)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `edit fills the form and locks the site and the code`() = runTest(mainRule.dispatcher) {
        devices.getResult = ApiResult.Success(FakeDeviceRepository.DEVICE.copy(type = "cold_room", model = "X1"))
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Edit("dev-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEdit)
        assertEquals("Cámaras de frío", state.name)
        assertEquals("CAM-001", state.externalCode)
        assertEquals("X1", state.model)
        // A type created elsewhere is kept as an option instead of being lost.
        assertEquals("cold_room", state.type)
        assertTrue("cold_room" in state.typeOptions)

        viewModel.onExternalCodeChange("OTRO")
        viewModel.onSiteChange("site-9")
        assertEquals("CAM-001", viewModel.uiState.value.externalCode)
        assertEquals("site-1", viewModel.uiState.value.siteId)
    }

    @Test
    fun `edit saves through update and never creates`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Edit("dev-1"))
        advanceUntilIdle()
        viewModel.onNameChange("Cámaras nuevas")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals("dev-1", devices.updateCalls.single().first)
        assertEquals("Cámaras nuevas", devices.updateCalls.single().second.name)
        assertTrue(devices.createCalls.isEmpty())
        assertTrue(viewModel.uiState.value.done)
    }

    @Test
    fun `delete asks for confirmation first`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Edit("dev-1"))
        advanceUntilIdle()

        viewModel.onDeleteRequest()
        assertTrue(viewModel.uiState.value.confirmingDelete)
        assertTrue(devices.deleteCalls.isEmpty())

        viewModel.onDeleteDismiss()
        assertFalse(viewModel.uiState.value.confirmingDelete)
        assertTrue(devices.deleteCalls.isEmpty())

        viewModel.onDeleteRequest()
        viewModel.onDeleteConfirm()
        advanceUntilIdle()

        assertEquals(listOf("dev-1"), devices.deleteCalls)
        assertTrue(viewModel.uiState.value.done)
    }

    @Test
    fun `a load failure can be retried`() = runTest(mainRule.dispatcher) {
        devices.getResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = viewModel()
        viewModel.start(DeviceFormMode.Edit("dev-1"))
        advanceUntilIdle()
        assertEquals(FormError.NO_CONNECTION, viewModel.uiState.value.loadError)

        devices.getResult = ApiResult.Success(FakeDeviceRepository.DEVICE)
        viewModel.retryLoad()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.loadError)
        assertEquals("Cámaras de frío", viewModel.uiState.value.name)
    }
}
