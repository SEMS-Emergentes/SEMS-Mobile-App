package pe.edu.upc.sems.analytics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.analytics.FakeAnalyticsRepository.Companion.prediction
import pe.edu.upc.sems.analytics.FakeAnalyticsRepository.Companion.rankingEntry
import pe.edu.upc.sems.analytics.FakeAnalyticsRepository.Companion.recommendation
import pe.edu.upc.sems.analytics.data.mapper.toDomain
import pe.edu.upc.sems.analytics.data.remote.RecommendationDto
import pe.edu.upc.sems.analytics.domain.model.ConsumptionRanking
import pe.edu.upc.sems.analytics.presentation.AnalyticsMessage
import pe.edu.upc.sems.analytics.presentation.AnalyticsTab
import pe.edu.upc.sems.analytics.presentation.AnalyticsViewModel
import pe.edu.upc.sems.analytics.presentation.ComparisonPeriod
import pe.edu.upc.sems.analytics.presentation.monthForecast
import pe.edu.upc.sems.analytics.presentation.potentialSaving
import pe.edu.upc.sems.analytics.presentation.rankingRows
import pe.edu.upc.sems.analytics.presentation.signedPercent
import pe.edu.upc.sems.analytics.presentation.siteRows
import pe.edu.upc.sems.devices.FakeDeviceRepository
import pe.edu.upc.sems.devices.domain.model.DeviceStatus
import pe.edu.upc.sems.energy.FakeEnergyRepository
import pe.edu.upc.sems.energy.domain.model.SiteConsumption
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.subscriptions.FakeSubscriptionRepository
import pe.edu.upc.sems.subscriptions.domain.model.PlanTier
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val lenientJson = Json { ignoreUnknownKeys = true }

    // region the plan

    @Test
    fun `the level comes from the plan's name, old names included`() {
        assertEquals(PlanTier.STARTER, PlanTier.fromPlanName("Starter"))
        assertEquals(PlanTier.STARTER, PlanTier.fromPlanName(null))
        assertEquals(PlanTier.BUSINESS, PlanTier.fromPlanName("Business"))
        assertEquals(PlanTier.BUSINESS, PlanTier.fromPlanName("Plus"))
        assertEquals(PlanTier.ENTERPRISE, PlanTier.fromPlanName("Enterprise"))
        assertEquals(PlanTier.ENTERPRISE, PlanTier.fromPlanName("Pro"))
    }

    @Test
    fun `a plan includes its own level and the ones below`() {
        assertTrue(PlanTier.BUSINESS.includes(PlanTier.BUSINESS))
        assertTrue(PlanTier.ENTERPRISE.includes(PlanTier.BUSINESS))
        assertFalse(PlanTier.STARTER.includes(PlanTier.BUSINESS))
    }

    // endregion

    // region the figures

    @Test
    fun `the backend's status says whether a recommendation was applied`() {
        assertTrue(RecommendationDto(id = "r", status = "applied").toDomain().applied)
        assertFalse(RecommendationDto(id = "r", status = "pending").toDomain().applied)
        assertFalse(RecommendationDto(id = "r").toDomain().applied)
    }

    @Test
    fun `a recommendation is read from the backend's snake_case JSON`() {
        val json = """{"id":"r1","title":"Revisa Refrigeración","description":"Concentra el 41%",
            "estimated_saving_amount":35.5,"status":"pending","generated_at":"2026-11-05T12:00:00Z","unknown":1}"""
        val dto = lenientJson.decodeFromString(RecommendationDto.serializer(), json)

        val recommendation = dto.toDomain()

        assertEquals(35.5, recommendation.estimatedSavingSoles, 1e-9)
        assertEquals("Concentra el 41%", recommendation.detail)
        assertFalse(recommendation.applied)
    }

    @Test
    fun `the forecast adds the latest forecast of each site in the latest month`() {
        val predictions = listOf(
            prediction("a", 2026, 10, amount = 999.0),
            prediction("a", 2026, 11, amount = 100.0, kwh = 10.0, createdAt = "2026-11-03T12:00:00Z"),
            prediction("a", 2026, 11, amount = 120.0, kwh = 12.0, createdAt = "2026-11-05T12:00:00Z"), // newer wins
            prediction("b", 2026, 11, amount = 80.0, kwh = 8.0),
        )

        val forecast = monthForecast(predictions)!!

        assertEquals(11, forecast.month)
        assertEquals(200.0, forecast.amountSoles, 1e-9)
        assertEquals(20.0, forecast.kwh, 1e-9)
    }

    @Test
    fun `without forecasts there is nothing to show`() {
        assertNull(monthForecast(emptyList()))
    }

    @Test
    fun `the saving adds up the pending recommendations only`() {
        val list = listOf(
            recommendation("a", saving = 35.5),
            recommendation("b", saving = 240.0),
            recommendation("c", saving = 100.0, applied = true),
        )

        assertEquals(275.5, potentialSaving(list)!!, 1e-9)
        assertNull(potentialSaving(listOf(recommendation("c", applied = true))))
        assertNull(potentialSaving(emptyList()))
    }

    @Test
    fun `sites are compared by consumption with the change against the window before`() {
        fun site(id: String, kwh: Double, cost: Double?) = SiteConsumption(id, 1, kwh, cost, 10.0, 100.0)
        // Last 30 days: s1 1000 kWh, s2 500. The 60-day totals say the 30 before had 800 and 500.
        val recent = listOf(site("s2", 500.0, 300.0), site("s1", 1000.0, 600.0))
        val doubled = listOf(site("s1", 1800.0, null), site("s2", 1000.0, null))

        val rows = siteRows(recent, doubled, mapOf("s1" to "San Isidro", "s2" to "Miraflores"))

        assertEquals(listOf("s1", "s2"), rows.map { it.siteId })
        assertEquals("San Isidro", rows[0].name)
        assertEquals(1f, rows[0].barFraction, 1e-6f)
        assertEquals(0.5f, rows[1].barFraction, 1e-6f)
        assertEquals(25.0, rows[0].changePercent!!, 1e-9) // 1000 vs 800
        assertEquals(0.0, rows[1].changePercent!!, 1e-9) // 500 vs 500: no change
        // A site with no consumption before (doubled total = last window's) has nothing to compare with.
        assertNull(siteRows(recent, listOf(site("s1", 1000.0, null)), emptyMap()).first { it.siteId == "s1" }.changePercent)
    }

    @Test
    fun `percentages are whole numbers with their sign`() {
        assertEquals("+3 %", signedPercent(3.2))
        assertEquals("−5 %", signedPercent(-5.4))
        assertEquals("0 %", signedPercent(0.2))
    }

    @Test
    fun `the ranking uses today's equipment names and drops removed equipment`() {
        val ranking = ConsumptionRanking(
            listOf(
                rankingEntry(1, "dev-1", name = "dev-1"),
                rankingEntry(2, "gone", name = "Equipo borrado"),
                rankingEntry(3, "dev-1", name = null),
            ),
            generatedAt = null,
        )

        val rows = rankingRows(ranking, listOf(FakeDeviceRepository.DEVICE), mapOf("site-1" to "Local San Isidro"))

        assertEquals(listOf(1, 2), rows.map { it.rank })
        assertEquals("Cámaras de frío", rows[0].name)
        assertEquals("Local San Isidro", rows[0].siteName)
    }

    @Test
    fun `a ranking without equipment loaded keeps the backend's names`() {
        val ranking = ConsumptionRanking(listOf(rankingEntry(1, "x", name = "Horno")), generatedAt = null)

        assertEquals("Horno", rankingRows(ranking, emptyList(), emptyMap()).single().name)
        assertTrue(rankingRows(null, emptyList(), emptyMap()).isEmpty())
    }

    // endregion

    // region the ViewModel

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val analytics = FakeAnalyticsRepository()
    private val subscriptions = FakeSubscriptionRepository()
    private val devices = FakeDeviceRepository().apply { listResult = ApiResult.Success(listOf(FakeDeviceRepository.DEVICE)) }
    private val organizations = FakeOrganizationRepository().apply {
        myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(pe.edu.upc.sems.organizations.domain.model.MembershipRole.ORG_ADMIN))
        listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
    }
    private val energy = FakeEnergyRepository()

    private fun viewModel() = AnalyticsViewModel(analytics, subscriptions, devices, organizations, energy, sessionManager)

    private fun TestScope.load(viewModel: AnalyticsViewModel) {
        viewModel.refresh()
        advanceUntilIdle()
    }

    @Test
    fun `a plan below Business sees the lock and nothing is requested`() = runTest(mainRule.dispatcher) {
        subscriptions.result = ApiResult.Success(PlanTier.STARTER)
        val viewModel = viewModel()

        load(viewModel)

        assertTrue(viewModel.uiState.value.locked)
        assertNull(viewModel.uiState.value.recommendations)
        assertTrue(energy.comparisonCalls.isEmpty())
    }

    @Test
    fun `if the plan cannot be read the screen reports it instead of locking`() = runTest(mainRule.dispatcher) {
        subscriptions.result = ApiResult.Failure(ApiError.Timeout)
        val viewModel = viewModel()

        load(viewModel)

        assertFalse(viewModel.uiState.value.locked)
        assertEquals(FormError.TIMEOUT, viewModel.uiState.value.error)
    }

    @Test
    fun `loads the forecast, the recommendations and the comparison`() = runTest(mainRule.dispatcher) {
        analytics.recommendationsResult = ApiResult.Success(
            listOf(recommendation("old", generatedAt = java.time.Instant.parse("2026-11-01T12:00:00Z")), recommendation("new")),
        )
        analytics.predictionsResult = ApiResult.Success(listOf(prediction("site-1", 2026, 11, amount = 3412.0)))
        energy.comparisonResults = mapOf(
            30 to ApiResult.Success(listOf(SiteConsumption("site-1", 1, 100.0, 50.0, 5.0, 95.0))),
            60 to ApiResult.Success(listOf(SiteConsumption("site-1", 1, 150.0, 70.0, 5.0, 95.0))),
        )
        val viewModel = viewModel()

        load(viewModel)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3412.0, state.forecast!!.amountSoles, 1e-9)
        assertEquals(listOf("new", "old"), state.recommendations!!.map { it.id }) // newest first
        assertEquals("Local San Isidro", state.siteRows!!.single().name)
        assertEquals(listOf("org-1" to 30, "org-1" to 60), energy.comparisonCalls.sortedBy { it.second })
    }

    @Test
    fun `a section that fails does not take the others down`() = runTest(mainRule.dispatcher) {
        analytics.recommendationsResult = ApiResult.Failure(ApiError.NoConnection)
        analytics.predictionsResult = ApiResult.Success(listOf(prediction("site-1", 2026, 11, amount = 50.0)))
        val viewModel = viewModel()

        load(viewModel)

        val state = viewModel.uiState.value
        assertNull(state.recommendations)
        assertEquals(50.0, state.forecast!!.amountSoles, 1e-9)
        assertNull(state.error)
    }

    @Test
    fun `changing the period asks the backend for that period again`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        load(viewModel)
        energy.comparisonCalls.clear()

        viewModel.onPeriodChange(ComparisonPeriod.WEEK)
        advanceUntilIdle()

        assertEquals(ComparisonPeriod.WEEK, viewModel.uiState.value.period)
        assertEquals(listOf("org-1" to 7, "org-1" to 14), energy.comparisonCalls.sortedBy { it.second })
    }

    @Test
    fun `removed equipment does not appear in the ranking`() = runTest(mainRule.dispatcher) {
        devices.listResult = ApiResult.Success(
            listOf(FakeDeviceRepository.DEVICE, FakeDeviceRepository.DEVICE.copy(id = "dev-2", status = DeviceStatus.REMOVED)),
        )
        analytics.rankingResult = ApiResult.Success(
            ConsumptionRanking(listOf(rankingEntry(1, "dev-2", "Borrado"), rankingEntry(2, "dev-1")), generatedAt = null),
        )
        val viewModel = viewModel()

        load(viewModel)

        assertEquals(listOf("Cámaras de frío"), viewModel.uiState.value.ranking!!.map { it.name })
    }

    @Test
    fun `applying a recommendation marks it applied and tells the user`() = runTest(mainRule.dispatcher) {
        analytics.recommendationsResult = ApiResult.Success(listOf(recommendation("r1"), recommendation("r2")))
        val viewModel = viewModel()
        load(viewModel)

        viewModel.onApply("r1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("r1"), analytics.applyCalls)
        assertTrue(state.recommendations!!.first { it.id == "r1" }.applied)
        assertFalse(state.recommendations.first { it.id == "r2" }.applied)
        assertEquals(AnalyticsMessage.APPLIED, state.message)
        assertTrue(state.busyIds.isEmpty())
    }

    @Test
    fun `a failed apply leaves the recommendation pending and says so`() = runTest(mainRule.dispatcher) {
        analytics.recommendationsResult = ApiResult.Success(listOf(recommendation("r1")))
        analytics.applyFailure = ApiResult.Failure(ApiError.Http(500, "boom"))
        val viewModel = viewModel()
        load(viewModel)

        viewModel.onApply("r1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.recommendations!!.single().applied)
        assertEquals(AnalyticsMessage.ACTION_FAILED, state.message)
        assertTrue(state.busyIds.isEmpty())
    }

    @Test
    fun `the tab is remembered by the ViewModel`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()

        viewModel.onTabChange(AnalyticsTab.TOP_CONSUMERS)

        assertEquals(AnalyticsTab.TOP_CONSUMERS, viewModel.uiState.value.tab)
    }

    // endregion
}
