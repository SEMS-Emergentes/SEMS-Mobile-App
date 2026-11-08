package pe.edu.upc.sems.reports

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.alerts.FakeAlertRepository
import pe.edu.upc.sems.alerts.FakeAlertRepository.Companion.alert
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.analytics.FakeAnalyticsRepository
import pe.edu.upc.sems.analytics.FakeAnalyticsRepository.Companion.recommendation
import pe.edu.upc.sems.energy.FakeEnergyRepository
import pe.edu.upc.sems.energy.FakeEnergyRepository.Companion.reading
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.MeterStatus
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.reports.domain.ReportData
import pe.edu.upc.sems.reports.domain.ReportFiles
import pe.edu.upc.sems.reports.domain.ReportPreset
import pe.edu.upc.sems.reports.domain.ReportRange
import pe.edu.upc.sems.reports.domain.buildReport
import pe.edu.upc.sems.reports.domain.reportFileName
import pe.edu.upc.sems.reports.domain.reportRange
import pe.edu.upc.sems.reports.presentation.ReportMessage
import pe.edu.upc.sems.reports.presentation.ReportPreviewUiState
import pe.edu.upc.sems.reports.presentation.ReportPreviewViewModel
import pe.edu.upc.sems.reports.presentation.ReportsViewModel
import pe.edu.upc.sems.reports.presentation.formatDuration
import pe.edu.upc.sems.reports.presentation.reportPeriodLabel
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.subscriptions.FakeSubscriptionRepository
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.testing.MainDispatcherRule

class FakeReportFiles : ReportFiles {
    val saved = mutableListOf<Pair<String, ReportData>>()
    var uri: String? = "content://downloads/report.pdf"

    override suspend fun savePdf(data: ReportData, fileName: String): String? {
        saved += fileName to data
        return uri
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // region the periods

    private val today = LocalDate.of(2026, 11, 6)

    @Test
    fun `this month goes from its first day to today`() {
        assertEquals(ReportRange(LocalDate.of(2026, 11, 1), today), reportRange(ReportPreset.THIS_MONTH, today, null))
    }

    @Test
    fun `last month is the whole previous month`() {
        assertEquals(
            ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
            reportRange(ReportPreset.LAST_MONTH, today, null),
        )
        // Across a year change and a short month.
        assertEquals(
            ReportRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
            reportRange(ReportPreset.LAST_MONTH, LocalDate.of(2026, 3, 10), null),
        )
        assertEquals(
            ReportRange(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)),
            reportRange(ReportPreset.LAST_MONTH, LocalDate.of(2026, 1, 3), null),
        )
    }

    @Test
    fun `the last three months are the current one and the two before`() {
        assertEquals(
            ReportRange(LocalDate.of(2026, 9, 1), today),
            reportRange(ReportPreset.LAST_THREE_MONTHS, today, null),
        )
    }

    @Test
    fun `a custom range is what the user picked, and nothing before they pick`() {
        val picked = ReportRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 20))
        assertEquals(picked, reportRange(ReportPreset.CUSTOM, today, picked))
        assertNull(reportRange(ReportPreset.CUSTOM, today, null))
    }

    @Test
    fun `labels and file names tell a whole month from any other range`() {
        val september = ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals("Septiembre 2026", reportPeriodLabel(september))
        assertEquals("Reporte_SEMS_sep-2026.pdf", reportFileName(september))

        val partial = ReportRange(LocalDate.of(2026, 11, 1), today)
        assertEquals("01/11/2026 – 06/11/2026", reportPeriodLabel(partial))
        assertEquals("Reporte_SEMS_2026-11-01_2026-11-06.pdf", reportFileName(partial))
    }

    @Test
    fun `durations read as minutes or hours`() {
        assertEquals("14 min", formatDuration(14))
        assertEquals("1 h 5 min", formatDuration(65))
        assertEquals("2 h", formatDuration(120))
    }

    // endregion

    // region the figures

    private val october = ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val site = FakeOrganizationRepository.SITE // site-1, 95 kW contracted
    private val meter = EnergyMeter("meter-1", "SN-1", null, "user-1", "site-1", null, MeterStatus.ACTIVE, null)

    private fun report(
        readings: List<pe.edu.upc.sems.energy.domain.model.EnergyReading> = emptyList(),
        alerts: List<pe.edu.upc.sems.alerts.domain.model.Alert> = emptyList(),
        recommendations: List<pe.edu.upc.sems.analytics.domain.model.Recommendation> = emptyList(),
    ) = buildReport("Cadena Flores", listOf(site), listOf(meter), readings, alerts, recommendations, october, today)

    @Test
    fun `a site adds up the readings of its meters in the period, with the cost the backend put on them`() {
        val data = report(
            readings = listOf(
                reading("2026-10-05T15:00:00Z", kw = 80.0, kwh = 10.0, cost = 6.0),
                reading("2026-10-20T15:00:00Z", kw = 120.0, kwh = 5.0, cost = 3.0),
                reading("2026-09-30T15:00:00Z", kw = 999.0, kwh = 99.0, cost = 99.0), // before the period
                reading("2026-11-01T15:00:00Z", kw = 999.0, kwh = 99.0, cost = 99.0), // after the period
                reading("2026-10-10T15:00:00Z", kwh = 50.0).copy(meterId = "other-meter"), // another site's meter
            ),
        )

        val row = data.sites.single()
        assertEquals(15.0, row.kwh, 1e-9)
        assertEquals(9.0, row.costSoles!!, 1e-9)
        assertEquals(120.0, row.peakKw!!, 1e-9)
        assertEquals(95.0, row.contractedKw, 1e-9)
        assertEquals(15.0, data.totalKwh, 1e-9)
        assertEquals(120.0, data.peakKw!!, 1e-9)
        assertTrue(data.hasReadings)
    }

    @Test
    fun `a period without readings shows no figures instead of invented ones`() {
        val data = report()

        assertEquals(0.0, data.totalKwh, 1e-9)
        assertNull(data.totalCostSoles)
        assertNull(data.peakKw)
        assertFalse(data.hasReadings)
    }

    @Test
    fun `the last day of the period is included`() {
        // 2026-10-31 23:30 in Lima is 2026-11-01 04:30 UTC.
        val data = report(readings = listOf(reading("2026-11-01T04:30:00Z", kwh = 2.0)))

        assertEquals(2.0, data.totalKwh, 1e-9)
    }

    @Test
    fun `alerts count as attended once they are no longer waiting, and the response time comes from the resolved ones`() {
        val data = report(
            alerts = listOf(
                alert("a", AlertStatus.ACTIVE, triggeredAt = Instant.parse("2026-10-05T15:00:00Z")),
                alert("b", AlertStatus.ACKNOWLEDGED, triggeredAt = Instant.parse("2026-10-06T15:00:00Z")),
                alert("c", AlertStatus.RESOLVED, triggeredAt = Instant.parse("2026-10-07T15:00:00Z"), resolvedAt = Instant.parse("2026-10-07T15:10:00Z")),
                alert("d", AlertStatus.RESOLVED, triggeredAt = Instant.parse("2026-10-08T15:00:00Z"), resolvedAt = Instant.parse("2026-10-08T15:20:00Z")),
                alert("e", AlertStatus.RESOLVED, triggeredAt = Instant.parse("2026-09-01T15:00:00Z"), resolvedAt = Instant.parse("2026-09-01T16:00:00Z")), // another month
            ),
        )

        assertEquals(3, data.alertsAttended)
        assertEquals(15L, data.avgResponseMinutes)
    }

    @Test
    fun `without resolved alerts there is no response time`() {
        val data = report(alerts = listOf(alert("a", AlertStatus.ACKNOWLEDGED, triggeredAt = Instant.parse("2026-10-06T15:00:00Z"))))

        assertEquals(1, data.alertsAttended)
        assertNull(data.avgResponseMinutes)
    }

    @Test
    fun `the saving adds up the recommendations applied in the period`() {
        val data = report(
            recommendations = listOf(
                recommendation("a", saving = 100.0, applied = true).copy(appliedAt = Instant.parse("2026-10-10T12:00:00Z")),
                recommendation("b", saving = 176.0, applied = true).copy(appliedAt = Instant.parse("2026-10-20T12:00:00Z")),
                recommendation("c", saving = 50.0, applied = true).copy(appliedAt = Instant.parse("2026-09-10T12:00:00Z")), // before
                recommendation("d", saving = 70.0, applied = false),
            ),
        )

        assertEquals(276.0, data.appliedSavingSoles!!, 1e-9)
        assertNull(report().appliedSavingSoles)
    }

    // endregion

    // region the ViewModels

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val subscriptions = FakeSubscriptionRepository()
    private val organizations = FakeOrganizationRepository().apply {
        myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(MembershipRole.ORG_ADMIN))
        listSitesResult = ApiResult.Success(listOf(site))
    }
    private val energy = FakeEnergyRepository().apply { metersResult = ApiResult.Success(listOf(meter)) }
    private val alerts = FakeAlertRepository()
    private val analytics = FakeAnalyticsRepository()
    private val files = FakeReportFiles()

    private fun TestScope.loadReports(viewModel: ReportsViewModel) {
        viewModel.refresh()
        advanceUntilIdle()
    }

    private fun previewViewModel() = ReportPreviewViewModel(organizations, energy, alerts, analytics, files, sessionManager)

    @Test
    fun `a plan below Business sees the lock`() = runTest(mainRule.dispatcher) {
        subscriptions.result = ApiResult.Success(PlanTier.STARTER)
        val viewModel = ReportsViewModel(subscriptions, sessionManager)

        loadReports(viewModel)

        assertTrue(viewModel.uiState.value.locked)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `Business sees the screen with last month chosen`() = runTest(mainRule.dispatcher) {
        val viewModel = ReportsViewModel(subscriptions, sessionManager)

        loadReports(viewModel)

        val state = viewModel.uiState.value
        assertFalse(state.locked)
        assertEquals(ReportPreset.LAST_MONTH, state.preset)
        assertEquals(state.today.minusMonths(1).withDayOfMonth(1), state.range!!.from)
    }

    @Test
    fun `a custom range is kept in order`() = runTest(mainRule.dispatcher) {
        val viewModel = ReportsViewModel(subscriptions, sessionManager)

        viewModel.onCustomRange(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 5))

        assertEquals(ReportPreset.CUSTOM, viewModel.uiState.value.preset)
        assertEquals(ReportRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 20)), viewModel.uiState.value.range)
    }

    @Test
    fun `the plan that cannot be read is reported, not locked`() = runTest(mainRule.dispatcher) {
        subscriptions.result = ApiResult.Failure(ApiError.Timeout)
        val viewModel = ReportsViewModel(subscriptions, sessionManager)

        loadReports(viewModel)

        assertFalse(viewModel.uiState.value.locked)
        assertEquals(FormError.TIMEOUT, viewModel.uiState.value.error)
    }

    @Test
    fun `the preview builds the report, saves the PDF and says it was saved`() = runTest(mainRule.dispatcher) {
        energy.userRangeResult = ApiResult.Success(listOf(reading("2026-10-05T15:00:00Z", kw = 80.0, kwh = 10.0, cost = 6.0)))
        val viewModel = previewViewModel()

        viewModel.load(october.from, october.to)
        advanceUntilIdle()

        val ready = viewModel.uiState.value as ReportPreviewUiState.Ready
        assertEquals("Cadena Flores S.A.C.", ready.data.organizationName)
        assertEquals(10.0, ready.data.totalKwh, 1e-9)
        assertEquals("content://downloads/report.pdf", ready.fileUri)
        assertEquals(1, files.saved.size)
        assertEquals(reportFileName(october), files.saved.single().first)
        assertEquals(ReportMessage.SAVED, viewModel.message.value!!.kind)
    }

    @Test
    fun `a PDF that could not be saved is reported and the preview stays`() = runTest(mainRule.dispatcher) {
        files.uri = null
        val viewModel = previewViewModel()

        viewModel.load(october.from, october.to)
        advanceUntilIdle()

        val ready = viewModel.uiState.value as ReportPreviewUiState.Ready
        assertNull(ready.fileUri)
        assertEquals(ReportMessage.SAVE_FAILED, viewModel.message.value!!.kind)
    }

    @Test
    fun `if the readings cannot be loaded the preview reports it instead of showing zeros`() = runTest(mainRule.dispatcher) {
        energy.userRangeResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = previewViewModel()

        viewModel.load(october.from, october.to)
        advanceUntilIdle()

        assertEquals(FormError.NO_CONNECTION, (viewModel.uiState.value as ReportPreviewUiState.Error).error)
        assertTrue(files.saved.isEmpty())
    }

    @Test
    fun `alerts that cannot be loaded do not stop the report`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = previewViewModel()

        viewModel.load(october.from, october.to)
        advanceUntilIdle()

        assertEquals(0, (viewModel.uiState.value as ReportPreviewUiState.Ready).data.alertsAttended)
    }

    @Test
    fun `the download icon saves another copy`() = runTest(mainRule.dispatcher) {
        val viewModel = previewViewModel()
        viewModel.load(october.from, october.to)
        advanceUntilIdle()

        viewModel.onDownloadAgain()
        advanceUntilIdle()

        assertEquals(2, files.saved.size)
    }

    // endregion
}
