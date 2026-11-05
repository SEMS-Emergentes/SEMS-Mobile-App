package pe.edu.upc.sems.alerts.presentation

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import pe.edu.upc.sems.alerts.domain.model.AlertSeverity
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.presentation.alerts.AlertFilter
import pe.edu.upc.sems.alerts.presentation.alerts.AlertItem
import pe.edu.upc.sems.alerts.presentation.alerts.AlertMessage
import pe.edu.upc.sems.alerts.presentation.alerts.AlertsViewModel
import pe.edu.upc.sems.alerts.presentation.alerts.visibleAlerts
import pe.edu.upc.sems.alerts.presentation.badge.ActiveAlertCount
import pe.edu.upc.sems.alerts.presentation.badge.AlertBadgeViewModel
import pe.edu.upc.sems.devices.FakeDeviceRepository
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
class AlertsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val alerts = FakeAlertRepository()
    private val devices = FakeDeviceRepository()
    private val organizations = FakeOrganizationRepository().apply {
        myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(MembershipRole.ORG_ADMIN))
        listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
    }
    private val count = ActiveAlertCount()

    private fun viewModel() = AlertsViewModel(alerts, devices, organizations, sessionManager, count)

    private fun item(alert: pe.edu.upc.sems.alerts.domain.model.Alert) = AlertItem(alert, null, null, null)

    private val now = Instant.parse("2026-11-05T20:00:00Z")

    // region what each tab shows

    @Test
    fun `active alerts come most severe first, then newest`() {
        val low = alert("low", severity = AlertSeverity.LOW, triggeredAt = Instant.parse("2026-11-05T19:00:00Z"))
        val oldHigh = alert("old-high", severity = AlertSeverity.HIGH, triggeredAt = Instant.parse("2026-11-05T10:00:00Z"))
        val newHigh = alert("new-high", severity = AlertSeverity.HIGH, triggeredAt = Instant.parse("2026-11-05T18:00:00Z"))
        val medium = alert("medium", severity = AlertSeverity.MEDIUM)

        val ids = visibleAlerts(listOf(low, oldHigh, medium, newHigh).map(::item), AlertFilter.ACTIVE, now).map { it.alert.id }

        assertEquals(listOf("new-high", "old-high", "medium", "low"), ids)
    }

    @Test
    fun `each tab shows only its status`() {
        val items = listOf(
            item(alert("a", AlertStatus.ACTIVE)),
            item(alert("b", AlertStatus.ACKNOWLEDGED)),
            item(alert("c", AlertStatus.RESOLVED, resolvedAt = Instant.parse("2026-11-05T19:00:00Z"))),
        )

        assertEquals(listOf("a"), visibleAlerts(items, AlertFilter.ACTIVE, now).map { it.alert.id })
        assertEquals(listOf("b"), visibleAlerts(items, AlertFilter.ACKNOWLEDGED, now).map { it.alert.id })
        assertEquals(listOf("c"), visibleAlerts(items, AlertFilter.RESOLVED, now).map { it.alert.id })
    }

    @Test
    fun `resolved alerts older than seven days are left out`() {
        val recent = alert("recent", AlertStatus.RESOLVED, resolvedAt = Instant.parse("2026-11-01T10:00:00Z"))
        val old = alert("old", AlertStatus.RESOLVED, resolvedAt = Instant.parse("2026-10-27T10:00:00Z"))

        val ids = visibleAlerts(listOf(old, recent).map(::item), AlertFilter.RESOLVED, now).map { it.alert.id }

        assertEquals(listOf("recent"), ids)
    }

    // endregion

    @Test
    fun `loading fills the alerts with device and site names`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Success(listOf(alert("a1", deviceId = "dev-1"), alert("a2", deviceId = null)))
        devices.listResult = ApiResult.Success(listOf(FakeDeviceRepository.DEVICE))
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        val withDevice = state.items.first { it.alert.id == "a1" }
        assertEquals("Cámaras de frío", withDevice.deviceName)
        assertEquals("site-1", withDevice.siteId)
        assertEquals("Local San Isidro", withDevice.siteName)
        // An alert that is not about a device has no site to go to.
        assertNull(state.items.first { it.alert.id == "a2" }.siteId)
        assertEquals(2, count.count.value)
    }

    @Test
    fun `alerts still show when the names cannot be loaded`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Success(listOf(alert("a1", deviceId = "dev-1")))
        devices.listResult = ApiResult.Failure(ApiError.NoConnection)
        organizations.listSitesResult = ApiResult.Failure(ApiError.NoConnection)
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertNull(viewModel.uiState.value.items.single().deviceName)
    }

    @Test
    fun `a load failure shows an error and can be retried`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Failure(ApiError.Timeout)
        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(FormError.TIMEOUT, viewModel.uiState.value.error)

        alerts.listResult = ApiResult.Success(listOf(alert("a1")))
        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun `acknowledging moves the alert to the other tab and updates the count`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Success(listOf(alert("a1"), alert("a2")))
        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, count.count.value)

        viewModel.onAcknowledge("a1")
        advanceUntilIdle()

        assertEquals(Triple("a1", AlertStatus.ACKNOWLEDGED, null), alerts.updateCalls.single())
        assertEquals(AlertStatus.ACKNOWLEDGED, viewModel.uiState.value.items.first { it.alert.id == "a1" }.alert.status)
        assertEquals(1, count.count.value)
        assertTrue(viewModel.uiState.value.busyIds.isEmpty())
        // Acknowledging has no "Deshacer" message.
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `resolving sends resolved_at and offers undo`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Success(listOf(alert("a1", AlertStatus.ACKNOWLEDGED)))
        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.onResolve("a1")
        advanceUntilIdle()

        val (id, status, resolvedAt) = alerts.updateCalls.single()
        assertEquals("a1", id)
        assertEquals(AlertStatus.RESOLVED, status)
        assertTrue(resolvedAt != null)
        assertEquals(AlertMessage.Resolved("a1", AlertStatus.ACKNOWLEDGED), viewModel.uiState.value.message)
    }

    @Test
    fun `undo puts the alert back in the status it had`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Success(listOf(alert("a1", AlertStatus.ACKNOWLEDGED)))
        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.onResolve("a1")
        advanceUntilIdle()
        viewModel.onMessageShown()

        viewModel.onUndo("a1", AlertStatus.ACKNOWLEDGED)
        advanceUntilIdle()

        assertEquals(AlertStatus.ACKNOWLEDGED, alerts.updateCalls.last().second)
        assertNull(alerts.updateCalls.last().third)
        assertEquals(AlertStatus.ACKNOWLEDGED, viewModel.uiState.value.items.single().alert.status)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `a failed update keeps the alert as it was and says so`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Success(listOf(alert("a1")))
        alerts.updateFailure = ApiResult.Failure(ApiError.Http(403, "forbidden"))
        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.onAcknowledge("a1")
        advanceUntilIdle()

        assertEquals(AlertStatus.ACTIVE, viewModel.uiState.value.items.single().alert.status)
        assertEquals(AlertMessage.ActionFailed, viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.busyIds.isEmpty())
        assertEquals(1, count.count.value)
    }

    @Test
    fun `the badge counts only active alerts`() = runTest(mainRule.dispatcher) {
        alerts.listResult = ApiResult.Success(
            listOf(alert("a", AlertStatus.ACTIVE), alert("b", AlertStatus.ACKNOWLEDGED), alert("c", AlertStatus.RESOLVED)),
        )
        val badge = AlertBadgeViewModel(alerts, sessionManager, count)

        badge.refresh()
        advanceUntilIdle()

        assertEquals(1, badge.count.value)
    }

    @Test
    fun `the badge keeps its number when the refresh fails`() = runTest(mainRule.dispatcher) {
        count.set(3)
        alerts.listResult = ApiResult.Failure(ApiError.NoConnection)
        val badge = AlertBadgeViewModel(alerts, sessionManager, count)

        badge.refresh()
        advanceUntilIdle()

        assertEquals(3, badge.count.value)
    }
}
