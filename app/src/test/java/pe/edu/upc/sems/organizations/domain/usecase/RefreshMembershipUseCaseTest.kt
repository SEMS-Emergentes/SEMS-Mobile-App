package pe.edu.upc.sems.organizations.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionMembership
import pe.edu.upc.sems.shared.session.SessionUser

class RefreshMembershipUseCaseTest {

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val repository = FakeOrganizationRepository()
    private val refresh = RefreshMembershipUseCase(repository, sessionManager)

    private fun membership() = runBlocking { sessionManager.session.first()?.membership }

    @Test
    fun `stores the organization name and the membership role`() = runBlocking {
        repository.myOrganizationResult = ApiResult.Success(FakeOrganizationRepository.access(MembershipRole.SUPERVISOR, "site-1"))

        refresh()

        assertEquals(listOf("user-1"), repository.myOrganizationCalls)
        assertEquals(SessionMembership("org-1", "Cadena Flores S.A.C.", "SUPERVISOR", "site-1"), membership())
    }

    @Test
    fun `no organization clears the cached membership`() = runBlocking {
        sessionManager.saveMembership(SessionMembership("org-old", "Old", "ORG_ADMIN", null))
        repository.myOrganizationResult = ApiResult.Success(null)

        refresh()

        assertNull(membership())
    }

    @Test
    fun `a network error keeps the cached membership`() = runBlocking {
        val cached = SessionMembership("org-1", "Cadena Flores S.A.C.", "ORG_ADMIN", null)
        sessionManager.saveMembership(cached)
        repository.myOrganizationResult = ApiResult.Failure(ApiError.Timeout)

        refresh()

        assertEquals(cached, membership())
    }

    @Test
    fun `does nothing when logged out`() = runBlocking {
        sessionManager.clear()

        refresh()

        assertTrue(repository.myOrganizationCalls.isEmpty())
    }
}
