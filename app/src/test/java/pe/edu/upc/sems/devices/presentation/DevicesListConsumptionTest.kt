package pe.edu.upc.sems.devices.presentation

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.devices.FakeDeviceRepository
import pe.edu.upc.sems.devices.presentation.devices.DevicesUiState
import pe.edu.upc.sems.devices.presentation.devices.DevicesViewModel
import pe.edu.upc.sems.devices.presentation.devices.todayFromReadings
import pe.edu.upc.sems.energy.FakeEnergyRepository
import pe.edu.upc.sems.energy.FakeEnergyRepository.Companion.reading
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

/** The "hoy" line of the Equipos list: the backend's totals when there are any, otherwise the readings of today. */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesListConsumptionTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // region adding up the readings

    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun `adds up what the device reported today, with the cost the backend put on each reading`() {
        val readings = listOf(
            reading("2026-11-05T15:00:00Z", kwh = 1.3, cost = 0.78), // 10:00 in Lima
            reading("2026-11-05T16:00:00Z", kwh = 1.5, cost = 0.90),
        )

        val (kwh, cost) = todayFromReadings(readings, "dev-1", today)!!
        assertEquals(2.8, kwh!!, 1e-9)
        assertEquals(1.68, cost!!, 1e-9)
    }

    @Test
    fun `other days, other devices and readings without a device are not counted`() {
        val readings = listOf(
            reading("2026-11-04T15:00:00Z", kwh = 9.0, cost = 9.0), // yesterday
            reading("2026-11-05T15:00:00Z", kwh = 9.0, cost = 9.0, deviceId = "dev-2"),
            reading("2026-11-05T15:00:00Z", kwh = 9.0, cost = 9.0, deviceId = null), // belongs to the site
        )

        assertNull(todayFromReadings(readings, "dev-1", today))
    }

    @Test
    fun `the day is the Lima day, not the UTC day`() {
        // 2026-11-06 02:00 UTC is still 2026-11-05 21:00 in Lima.
        val readings = listOf(reading("2026-11-06T02:00:00Z", kwh = 2.0, cost = 1.0))

        assertEquals(2.0 to 1.0, todayFromReadings(readings, "dev-1", today))
    }

    @Test
    fun `a value no reading carries stays missing instead of becoming zero`() {
        val readings = listOf(reading("2026-11-05T15:00:00Z", kwh = 1.0, cost = null))

        assertEquals(1.0 to null, todayFromReadings(readings, "dev-1", today))
    }

    // endregion

    // region the list

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val devices = FakeDeviceRepository().apply {
        listResult = ApiResult.Success(listOf(FakeDeviceRepository.DEVICE))
    }
    private val organizations = FakeOrganizationRepository()
    private val energy = FakeEnergyRepository()

    private fun viewModel() = DevicesViewModel(devices, organizations, energy, sessionManager)

    private fun todayReading(kwh: Double, cost: Double) = reading(Instant.now().toString(), kwh = kwh, cost = cost)

    private fun items(viewModel: DevicesViewModel) = (viewModel.uiState.value as DevicesUiState.Success).items

    @Test
    fun `without totals from the backend the card shows what the readings add up to`() = runTest(mainRule.dispatcher) {
        energy.userRangeResult = ApiResult.Success(listOf(todayReading(1.3, 0.78), todayReading(1.5, 0.90)))
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val item = items(viewModel).single()
        assertEquals(2.8, item.todayKwh!!, 1e-9)
        assertEquals(1.68, item.todayCostSoles!!, 1e-9)
    }

    @Test
    fun `the backend's totals win and the readings are not even asked for`() = runTest(mainRule.dispatcher) {
        energy.deviceConsumptionsResult = ApiResult.Success(
            listOf(DeviceConsumption("dev-1", 118.0, 52.4, periodStart = null, periodEnd = Instant.now())),
        )
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(118.0, items(viewModel).single().todayKwh!!, 1e-9)
        assertTrue(energy.userRangeCalls.isEmpty())
    }

    @Test
    fun `a device that sent nothing today shows no figures`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val item = items(viewModel).single()
        assertNull(item.todayKwh)
        assertNull(item.todayCostSoles)
    }

    @Test
    fun `when the backend's totals fail the readings still fill the card`() = runTest(mainRule.dispatcher) {
        energy.deviceConsumptionsResult = ApiResult.Failure(ApiError.Http(500, "boom"))
        energy.userRangeResult = ApiResult.Success(listOf(todayReading(2.0, 1.0)))
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2.0, items(viewModel).single().todayKwh!!, 1e-9)
        assertNull((viewModel.uiState.value as DevicesUiState.Success).consumptionError)
    }

    @Test
    fun `when nothing can be loaded the list still shows and the error is reported`() = runTest(mainRule.dispatcher) {
        energy.deviceConsumptionsResult = ApiResult.Failure(ApiError.Timeout)
        energy.userRangeResult = ApiResult.Failure(ApiError.Timeout)
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value as DevicesUiState.Success
        assertEquals(1, state.items.size)
        assertNull(state.items.single().todayKwh)
        assertEquals(FormError.TIMEOUT, state.consumptionError)
    }

    // endregion
}
