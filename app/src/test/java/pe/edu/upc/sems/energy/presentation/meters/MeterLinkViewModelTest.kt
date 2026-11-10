package pe.edu.upc.sems.energy.presentation.meters

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.model.EnergyMeter
import pe.edu.upc.sems.energy.domain.model.EnergyReading
import pe.edu.upc.sems.energy.domain.model.MeterDraft
import pe.edu.upc.sems.energy.domain.model.MeterStatus
import pe.edu.upc.sems.energy.domain.model.SiteZoneConsumption
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.shared.session.SelectedSite
import pe.edu.upc.sems.shared.session.SessionMembership
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MeterLinkViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val org = FakeOrganizationRepository().apply {
        myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(MembershipRole.ORG_ADMIN))
        listSitesResult = ApiResult.Success(listOf(FakeOrganizationRepository.SITE))
        listZonesResult = ApiResult.Success(emptyList())
    }
    private val session = FakeSessionManager(
        Session(
            AuthTokens("token", "refresh"),
            SessionUser("user-1", "a@b.pe", "Ana", emptySet(), null),
            SelectedSite(FakeOrganizationRepository.ORGANIZATION.id, FakeOrganizationRepository.SITE.id, FakeOrganizationRepository.SITE.name),
            SessionMembership(FakeOrganizationRepository.ORGANIZATION.id, FakeOrganizationRepository.ORGANIZATION.legalName, "ORG_ADMIN", null),
        ),
    )
    private val energy = FakeEnergy()
    private val viewModel = MeterLinkViewModel(energy, org, session)

    @Test fun `links to site when no zones exist and only submits once`() = runTest(dispatcher.dispatcher) {
        viewModel.load()
        advanceUntilIdle()
        viewModel.onSerial("MED-002")
        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(1, energy.created.size)
        assertEquals(null, energy.created.single().third)
        assertTrue(viewModel.uiState.value.done)
    }

    @Test fun `invalid serial never reaches backend`() = runTest(dispatcher.dispatcher) {
        viewModel.load()
        advanceUntilIdle()
        viewModel.onSerial("bad serial")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.serialInvalid)
        assertTrue(energy.created.isEmpty())
    }

    @Test fun `server failure stays on form`() = runTest(dispatcher.dispatcher) {
        energy.result = ApiResult.Failure(ApiError.Http(503, "Unavailable"))
        viewModel.load()
        advanceUntilIdle()
        viewModel.onSerial("MED-002")
        viewModel.submit()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.done)
        assertFalse(viewModel.uiState.value.saving)
        assertEquals(1, energy.created.size)
    }

    private class FakeEnergy : EnergyRepository {
        val created = mutableListOf<Triple<String, String, String?>>()
        var result: ApiResult<EnergyMeter> = ApiResult.Success(EnergyMeter("meter-1", "MED-002", null, "user-1", "site-1", null, MeterStatus.ACTIVE, null))
        override suspend fun createMeter(userId: String, siteId: String, zoneId: String?, draft: MeterDraft): ApiResult<EnergyMeter> {
            created += Triple(userId, siteId, zoneId)
            return result
        }
        override suspend fun listDeviceConsumptions(userId: String): ApiResult<List<DeviceConsumption>> = error("unused")
        override suspend fun listMeters(userId: String): ApiResult<List<EnergyMeter>> = error("unused")
        override suspend fun listMetersBySite(siteId: String): ApiResult<List<EnergyMeter>> = error("unused")
        override suspend fun assignMeterZone(meterId: String, zoneId: String?): ApiResult<EnergyMeter> = error("unused")
        override suspend fun deleteMeter(meterId: String): ApiResult<Unit> = error("unused")
        override suspend fun latestMeterReading(meterId: String): ApiResult<EnergyReading?> = error("unused")
        override suspend fun meterReadingHistory(userId: String, meterId: String, from: java.time.Instant, to: java.time.Instant): ApiResult<List<EnergyReading>> = error("unused")
        override suspend fun deviceReadingsInRange(userId: String, deviceId: String, from: java.time.Instant, to: java.time.Instant): ApiResult<List<EnergyReading>> = error("unused")
        override suspend fun currentDeviceReading(deviceId: String): ApiResult<EnergyReading?> = error("unused")
        override suspend fun deviceReadingHistory(deviceId: String, limit: Int): ApiResult<List<EnergyReading>> = error("unused")
        override suspend fun siteZoneConsumption(siteId: String, days: Int): ApiResult<SiteZoneConsumption> = error("unused")
        override suspend fun siteComparison(organizationId: String, days: Int): ApiResult<List<pe.edu.upc.sems.energy.domain.model.SiteConsumption>> = error("unused")
        override suspend fun userReadingsInRange(userId: String, from: java.time.Instant, to: java.time.Instant): ApiResult<List<EnergyReading>> = error("unused")
    }
}
