package pe.edu.upc.sems.dashboard

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.alerts.FakeAlertRepository
import pe.edu.upc.sems.alerts.FakeAlertRepository.Companion.alert
import pe.edu.upc.sems.alerts.domain.model.AlertSeverity
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.presentation.badge.ActiveAlertCount
import pe.edu.upc.sems.dashboard.presentation.DashboardUiState
import pe.edu.upc.sems.dashboard.presentation.DashboardViewModel
import pe.edu.upc.sems.dashboard.presentation.demandPercent
import pe.edu.upc.sems.dashboard.presentation.fortnightSaving
import pe.edu.upc.sems.dashboard.presentation.kwhChangePercent
import pe.edu.upc.sems.dashboard.presentation.pendingAlerts
import pe.edu.upc.sems.dashboard.presentation.readingsOfSite
import pe.edu.upc.sems.energy.FakeEnergyRepository
import pe.edu.upc.sems.energy.FakeEnergyRepository.Companion.reading
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.MeterStatus
import pe.edu.upc.sems.energy.domain.model.SiteConsumption
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.SelectedSite
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-05T20:00:00Z")

    // region the figures

    @Test
    fun `the change in kWh compares the last window with the one before it`() {
        // 4 820 kWh in the last 30 days and 9 100 in the last 60: the 30 before had 4 280.
        assertEquals(12.6, kwhChangePercent(4820.0, 9100.0)!!, 0.05)
        // Less than before is a negative change.
        assertEquals(-50.0, kwhChangePercent(100.0, 300.0)!!, 1e-9) // 200 before, 100 now
    }

    @Test
    fun `without a window before there is nothing to compare`() {
        assertNull(kwhChangePercent(100.0, 100.0))
        assertNull(kwhChangePercent(100.0, 90.0))
    }

    @Test
    fun `peak demand is a share of the contracted power`() {
        assertEquals(95.2, demandPercent(238.0, 250.0)!!, 0.05)
        assertEquals(110.0, demandPercent(110.0, 100.0)!!, 1e-9)
        assertNull(demandPercent(50.0, null))
        assertNull(demandPercent(50.0, 0.0))
    }

    @Test
    fun `the saving compares the cost of the last fortnight with the one before`() {
        val readings = listOf(
            reading("2026-11-01T15:00:00Z", cost = 6.0), // last fortnight
            reading("2026-11-03T15:00:00Z", cost = 4.0),
            reading("2026-10-20T15:00:00Z", cost = 12.0), // fortnight before
            reading("2026-10-18T15:00:00Z", cost = 8.0),
        )

        val saving = fortnightSaving(readings, now)!!

        assertEquals(10.0, saving.amountSoles, 1e-9) // 20 before - 10 now
        assertEquals(50.0, saving.percent, 1e-9)
    }

    @Test
    fun `spending more is a negative saving`() {
        val readings = listOf(
            reading("2026-11-01T15:00:00Z", cost = 15.0),
            reading("2026-10-20T15:00:00Z", cost = 10.0),
        )

        val saving = fortnightSaving(readings, now)!!

        assertEquals(-5.0, saving.amountSoles, 1e-9)
        assertEquals(-50.0, saving.percent, 1e-9)
    }

    @Test
    fun `no saving is shown without priced readings on both sides`() {
        assertNull(fortnightSaving(emptyList(), now))
        // Only the last fortnight: nothing to compare with.
        assertNull(fortnightSaving(listOf(reading("2026-11-01T15:00:00Z", cost = 5.0)), now))
        // Only the fortnight before: the meter went quiet, which is not a saving.
        assertNull(fortnightSaving(listOf(reading("2026-10-20T15:00:00Z", cost = 5.0)), now))
        // Readings without a cost count for nothing.
        assertNull(
            fortnightSaving(
                listOf(reading("2026-11-01T15:00:00Z", cost = null), reading("2026-10-20T15:00:00Z", cost = 5.0)),
                now,
            ),
        )
    }

    @Test
    fun `only the readings of the site's meters are used`() {
        val mine = reading("2026-11-01T15:00:00Z").copy(meterId = "meter-1")
        val other = reading("2026-11-01T15:00:00Z").copy(meterId = "meter-9")
        val noMeter = reading("2026-11-01T15:00:00Z").copy(meterId = null)

        assertEquals(listOf(mine), readingsOfSite(listOf(mine, other, noMeter), setOf("meter-1")))
    }

    @Test
    fun `pending alerts are the most severe active ones, at most three`() {
        val list = listOf(
            alert("low", severity = AlertSeverity.LOW),
            alert("high", severity = AlertSeverity.HIGH),
            alert("medium", severity = AlertSeverity.MEDIUM),
            alert("acknowledged", status = AlertStatus.ACKNOWLEDGED, severity = AlertSeverity.HIGH),
            alert("another-low", severity = AlertSeverity.LOW),
        )

        assertEquals(listOf("high", "medium", "low"), pendingAlerts(list).map { it.id }.take(3))
        assertEquals(3, pendingAlerts(list).size)
    }

    // endregion

    // region the ViewModel

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val site = SelectedSite("org-1", "site-1", "Local San Isidro")
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = site))
    private val energy = FakeEnergyRepository()
    private val alerts = FakeAlertRepository()
    private val count = ActiveAlertCount()

    private fun siteConsumption(kwh: Double) = SiteConsumption(
        siteId = "site-1",
        meterCount = 1,
        kwh = kwh,
        energyCostSoles = 1200.5,
        peakDemandKw = 238.0,
        contractedPowerKw = 250.0,
    )

    private fun meter(id: String) = EnergyMeter(id, "SN-$id", null, "user-1", "site-1", null, MeterStatus.ACTIVE, null)

    private fun viewModel() = DashboardViewModel(energy, alerts, sessionManager, count)

    @Test
    fun `without a selected site it asks for one`() = runTest(mainRule.dispatcher) {
        val withoutSite = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
        val viewModel = DashboardViewModel(energy, alerts, withoutSite, count)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(DashboardUiState.NoSite, viewModel.uiState.value)
    }

    @Test
    fun `loads the figures, the chart and the pending alerts of the site`() = runTest(mainRule.dispatcher) {
        energy.comparisonResults = mapOf(
            30 to ApiResult.Success(listOf(siteConsumption(4820.0), siteConsumption(1.0).copy(siteId = "site-2"))),
            60 to ApiResult.Success(listOf(siteConsumption(9100.0))),
        )
        energy.metersBySiteResult = ApiResult.Success(listOf(meter("meter-1")))
        energy.userRangeResult = ApiResult.Success(
            listOf(
                reading("2026-11-03T15:00:00Z", kwh = 2.0, cost = 1.0).copy(meterId = "meter-1"),
                reading("2026-11-03T16:00:00Z", kwh = 3.0, cost = 2.0).copy(meterId = "meter-1"),
                // Another site's meter must not count.
                reading("2026-11-03T17:00:00Z", kwh = 99.0, cost = 99.0).copy(meterId = "meter-9"),
            ),
        )
        alerts.listResult = ApiResult.Success(listOf(alert("a1", severity = AlertSeverity.HIGH), alert("a2")))
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val content = (viewModel.uiState.value as DashboardUiState.Success).content
        assertNull(content.error)
        assertEquals(4820.0, content.consumption!!.kwh, 1e-9)
        assertEquals(12.6, content.kwhChangePercent!!, 0.05)
        assertEquals(1, content.meterCount)
        assertTrue(content.hasReadings)
        assertEquals(14, content.dailyKwh!!.size)
        assertEquals(5.0, content.dailyKwh!!.sum(), 1e-9)
        assertEquals(listOf("a1", "a2"), content.pendingAlerts.map { it.id })
        // The count of the bottom bar follows.
        assertEquals(2, count.count.value)
        // Both windows were asked for the site's organization.
        assertEquals(listOf("org-1" to 30, "org-1" to 60), energy.comparisonCalls.sortedBy { it.second })
    }

    @Test
    fun `a failed load shows an error and keeps what did load`() = runTest(mainRule.dispatcher) {
        energy.comparisonResults = mapOf(30 to ApiResult.Failure(ApiError.Timeout))
        energy.metersBySiteResult = ApiResult.Success(listOf(meter("meter-1")))
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val content = (viewModel.uiState.value as DashboardUiState.Success).content
        assertEquals(FormError.TIMEOUT, content.error)
        assertNull(content.consumption)
        assertEquals(1, content.meterCount)
    }

    @Test
    fun `alerts that cannot be loaded do not break the screen`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Failure(ApiError.NoConnection)
        count.set(4)
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val content = (viewModel.uiState.value as DashboardUiState.Success).content
        assertTrue(content.alertsUnavailable)
        assertNull(content.error)
        assertNotNull(content)
        // The bottom bar keeps its last number.
        assertEquals(4, count.count.value)
    }

    // endregion
}
