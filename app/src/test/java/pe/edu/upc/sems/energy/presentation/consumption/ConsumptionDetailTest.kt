package pe.edu.upc.sems.energy.presentation.consumption

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.devices.FakeDeviceRepository
import pe.edu.upc.sems.energy.FakeEnergyRepository
import pe.edu.upc.sems.energy.FakeEnergyRepository.Companion.reading
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ConsumptionDetailTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // 2026-11-05 20:00 in Lima is 2026-11-06 01:00 UTC.
    private val now = Instant.parse("2026-11-06T01:00:00Z")

    // region the numbers the screen shows

    @Test
    fun `adds up the energy and the cost the backend sent, and takes the highest power as the peak`() {
        val summary = summarize(
            listOf(
                reading("2026-11-05T15:00:00Z", kw = 5.2, kwh = 1.3, cost = 0.78),
                reading("2026-11-05T15:15:00Z", kw = 6.1, kwh = 1.5, cost = 0.90),
                reading("2026-11-05T15:30:00Z", kw = 4.9, kwh = 1.2, cost = 0.72),
            ),
            ConsumptionPeriod.TODAY,
            now,
        )

        assertEquals(4.0, summary.totalKwh!!, 1e-9)
        assertEquals(2.40, summary.costSoles!!, 1e-9)
        assertEquals(6.1, summary.peakKw!!, 1e-9)
    }

    @Test
    fun `a value that no reading carries is missing, not zero`() {
        val summary = summarize(
            listOf(reading("2026-11-05T15:00:00Z", kw = 5.0, kwh = null, cost = null)),
            ConsumptionPeriod.TODAY,
            now,
        )

        assertNull(summary.totalKwh)
        assertNull(summary.costSoles)
        assertEquals(5.0, summary.peakKw!!, 1e-9)
    }

    @Test
    fun `without readings everything is missing`() {
        val summary = summarize(emptyList(), ConsumptionPeriod.TODAY, now)

        assertNull(summary.totalKwh)
        assertNull(summary.costSoles)
        assertNull(summary.peakKw)
        assertTrue(summary.recent.isEmpty())
    }

    @Test
    fun `recent readings are the newest five, newest first`() {
        val readings = (0 until 7).map { reading("2026-11-05T1$it:00:00Z", kw = it.toDouble()) }

        val recent = summarize(readings, ConsumptionPeriod.TODAY, now).recent

        assertEquals(5, recent.size)
        assertEquals(Instant.parse("2026-11-05T16:00:00Z"), recent.first().measuredAt)
    }

    // endregion

    // region the chart

    @Test
    fun `today has one bar per hour of Lima time with the average power`() {
        // 15:00Z and 15:30Z are both 10:xx in Lima (UTC-5).
        val summary = summarize(
            listOf(
                reading("2026-11-05T15:00:00Z", kw = 4.0),
                reading("2026-11-05T15:30:00Z", kw = 6.0),
            ),
            ConsumptionPeriod.TODAY,
            now,
        )

        assertTrue(summary.barsArePower)
        assertEquals(24, summary.bars.size)
        assertEquals(5.0, summary.bars[10], 1e-9)
        assertEquals(0.0, summary.bars[11], 1e-9)
    }

    @Test
    fun `a week has one bar per day with the energy of that day, today last`() {
        val summary = summarize(
            listOf(
                reading("2026-11-05T15:00:00Z", kwh = 2.0),
                reading("2026-11-05T16:00:00Z", kwh = 3.0),
                reading("2026-11-03T15:00:00Z", kwh = 4.0),
            ),
            ConsumptionPeriod.WEEK,
            now,
        )

        assertFalse(summary.barsArePower)
        assertEquals(7, summary.bars.size)
        assertEquals(5.0, summary.bars.last(), 1e-9)
        assertEquals(4.0, summary.bars[4], 1e-9) // Nov 3 is two days before Nov 5
    }

    @Test
    fun `a late reading counts for the Lima day, not the UTC day`() {
        // 2026-11-06 01:00Z is still the evening of Nov 5 in Lima.
        val bars = dailyEnergy(listOf(reading("2026-11-06T01:00:00Z", kwh = 1.5)), 2, now)

        assertEquals(listOf(0.0, 1.5), bars)
    }

    @Test
    fun `today starts at midnight in Lima and the other periods count back from now`() {
        assertEquals(Instant.parse("2026-11-05T05:00:00Z"), periodStart(ConsumptionPeriod.TODAY, now))
        assertEquals(Instant.parse("2026-10-30T01:00:00Z"), periodStart(ConsumptionPeriod.WEEK, now))
        assertEquals(Instant.parse("2026-10-07T01:00:00Z"), periodStart(ConsumptionPeriod.MONTH, now))
    }

    // endregion

    // region the screen's ViewModel

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val devices = FakeDeviceRepository()
    private val energy = FakeEnergyRepository()
    private val organizations = FakeOrganizationRepository().apply {
        myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(MembershipRole.ORG_ADMIN))
        listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
    }

    private fun viewModel() = ConsumptionDetailViewModel(devices, energy, organizations, sessionManager)

    @Test
    fun `loads the device, its place and the readings of today`() = runTest(mainRule.dispatcher) {
        energy.rangeResult = ApiResult.Success(listOf(reading("2026-11-05T15:00:00Z", kw = 5.2, kwh = 1.3, cost = 0.78)))
        val viewModel = viewModel()

        viewModel.start("dev-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Cámaras de frío", state.deviceName)
        assertEquals("Local San Isidro", state.place)
        assertEquals(ConsumptionPeriod.TODAY, state.period)
        assertNotNull(state.summary)
        // Asked for this device of this user.
        assertEquals("user-1", energy.rangeCalls.single()[0])
        assertEquals("dev-1", energy.rangeCalls.single()[1])
    }

    @Test
    fun `changing the period asks again for that period`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.start("dev-1")
        advanceUntilIdle()

        viewModel.onPeriodChange(ConsumptionPeriod.WEEK)
        advanceUntilIdle()

        assertEquals(2, energy.rangeCalls.size)
        assertEquals(ConsumptionPeriod.WEEK, viewModel.uiState.value.period)
        assertEquals(7, viewModel.uiState.value.summary!!.bars.size)
    }

    @Test
    fun `a failure loading the readings keeps the header and can be retried`() = runTest(mainRule.dispatcher) {
        energy.rangeResult = ApiResult.Failure(ApiError.Timeout)
        val viewModel = viewModel()
        viewModel.start("dev-1")
        advanceUntilIdle()

        assertEquals("Cámaras de frío", viewModel.uiState.value.deviceName)
        assertEquals(FormError.TIMEOUT, viewModel.uiState.value.periodError)

        energy.rangeResult = ApiResult.Success(emptyList())
        viewModel.onPeriodChange(ConsumptionPeriod.TODAY)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.periodError)
        assertNotNull(viewModel.uiState.value.summary)
    }

    @Test
    fun `a device that cannot be loaded shows an error`() = runTest(mainRule.dispatcher) {
        devices.getResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = viewModel()

        viewModel.start("dev-1")
        advanceUntilIdle()

        assertEquals(FormError.NO_CONNECTION, viewModel.uiState.value.error)
        assertTrue(energy.rangeCalls.isEmpty())
    }

    // endregion
}
