package pe.edu.upc.sems.organizations.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.organizations.FakeOrganizationRepository
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.presentation.common.FormError
import pe.edu.upc.sems.organizations.presentation.members.MemberFieldError
import pe.edu.upc.sems.organizations.presentation.members.MembersViewModel
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import pe.edu.upc.sems.testing.MainDispatcherRule

/** "Quién tiene acceso" (web: MembersCard.vue). */
@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("STAFF"), null)
    private val sessionManager = FakeSessionManager(Session(AuthTokens("jwt", "refresh"), user, selectedSite = null))
    private val repository = FakeOrganizationRepository()

    private val me = FakeOrganizationRepository.MEMBER
    private val luis = me.copy(membershipId = "m-2", userId = "user-2", email = "luis@example.com", role = MembershipRole.SUPERVISOR, siteId = "site-1")

    private fun viewModel(canManage: Boolean = true) =
        MembersViewModel(repository, sessionManager).apply { start("org-1", canManage) }

    @Test
    fun `lists the members and knows who is the signed-in user`() = runTest(mainRule.dispatcher) {
        repository.listMembersResult = ApiResult.Success(listOf(me, luis))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("m-1", "m-2"), state.members.map { it.membershipId })
        assertEquals("user-1", state.currentUserId)
        assertEquals(listOf("org-1"), repository.listMembersCalls)
    }

    @Test
    fun `supervisors and operators see the members read only`() = runTest(mainRule.dispatcher) {
        // Backend: GET members needs any active membership; POST and DELETE need ORG_ADMIN.
        repository.listMembersResult = ApiResult.Success(listOf(me, luis))
        val viewModel = viewModel(canManage = false)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.members.size)

        viewModel.onGrantClick()
        viewModel.onRevokeRequest(luis)
        assertNull(viewModel.uiState.value.grantForm)
        assertNull(viewModel.uiState.value.confirmingRevoke)
        assertTrue(repository.grantMemberCalls.isEmpty())
        assertTrue(repository.revokeMemberCalls.isEmpty())
    }

    @Test
    fun `the email is required and must look like an email`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onGrantClick()

        viewModel.onGrantSubmit()
        assertEquals(MemberFieldError.REQUIRED, viewModel.uiState.value.grantForm?.emailError)

        viewModel.onEmailChange("luis@")
        viewModel.onGrantSubmit()
        assertEquals(MemberFieldError.INVALID_EMAIL, viewModel.uiState.value.grantForm?.emailError)
        assertTrue(repository.grantMemberCalls.isEmpty())
    }

    @Test
    fun `a supervisor needs a site and an administrator cannot have one`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onGrantClick()
        viewModel.onEmailChange("luis@example.com")

        viewModel.onRoleChange(MembershipRole.SUPERVISOR)
        viewModel.onGrantSubmit()
        assertEquals(MemberFieldError.SITE_REQUIRED, viewModel.uiState.value.grantForm?.siteError)
        assertTrue(repository.grantMemberCalls.isEmpty())

        viewModel.onSiteChange("site-1")
        viewModel.onRoleChange(MembershipRole.ORG_ADMIN)
        assertNull(viewModel.uiState.value.grantForm?.siteId)
        viewModel.onGrantSubmit()
        advanceUntilIdle()

        val (organizationId, grant) = repository.grantMemberCalls.single()
        assertEquals("org-1", organizationId)
        assertEquals(MembershipRole.ORG_ADMIN, grant.role)
        assertNull(grant.siteId)
    }

    @Test
    fun `granting access closes the sheet and reloads the list`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onGrantClick()
        viewModel.onEmailChange("  luis@example.com ")
        viewModel.onRoleChange(MembershipRole.SUPERVISOR)
        viewModel.onSiteChange("site-1")
        repository.listMembersResult = ApiResult.Success(listOf(me, luis))
        viewModel.onGrantSubmit()
        advanceUntilIdle()

        val grant = repository.grantMemberCalls.single().second
        assertEquals("luis@example.com", grant.email)
        assertEquals("site-1", grant.siteId)
        assertNull(viewModel.uiState.value.grantForm)
        assertEquals(2, viewModel.uiState.value.members.size)
    }

    @Test
    fun `an email without an account shows not found and keeps the sheet open`() = runTest(mainRule.dispatcher) {
        repository.grantMemberResult = ApiResult.Failure(ApiError.Http(404, "No user is registered with 'nadie@example.com'"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onGrantClick()
        viewModel.onEmailChange("nadie@example.com")
        viewModel.onGrantSubmit()
        advanceUntilIdle()

        val form = viewModel.uiState.value.grantForm
        assertNotNull(form)
        assertEquals(FormError.NOT_FOUND, form?.error)
        assertEquals("nadie@example.com", form?.email)
    }

    @Test
    fun `removing access asks first and never applies to yourself`() = runTest(mainRule.dispatcher) {
        repository.listMembersResult = ApiResult.Success(listOf(me, luis))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRevokeRequest(me)
        assertNull(viewModel.uiState.value.confirmingRevoke)

        viewModel.onRevokeRequest(luis)
        assertEquals(luis, viewModel.uiState.value.confirmingRevoke)
        assertTrue(repository.revokeMemberCalls.isEmpty())

        repository.listMembersResult = ApiResult.Success(listOf(me))
        viewModel.onRevokeConfirm()
        advanceUntilIdle()
        assertEquals(listOf("org-1" to "m-2"), repository.revokeMemberCalls)
        assertEquals(listOf("m-1"), viewModel.uiState.value.members.map { it.membershipId })
    }

    @Test
    fun `the last administrator cannot be removed`() = runTest(mainRule.dispatcher) {
        val otherAdmin = luis.copy(role = MembershipRole.ORG_ADMIN, siteId = null)
        repository.listMembersResult = ApiResult.Success(listOf(me, otherAdmin))
        repository.revokeMemberResult = ApiResult.Failure(ApiError.Http(409, "the organization would be left without an administrator"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRevokeRequest(otherAdmin)
        viewModel.onRevokeConfirm()
        advanceUntilIdle()

        assertEquals(FormError.CONFLICT, viewModel.uiState.value.revokeError)
        assertNull(viewModel.uiState.value.revokingId)
    }
}
