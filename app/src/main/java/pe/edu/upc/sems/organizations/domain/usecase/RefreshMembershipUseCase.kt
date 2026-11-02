package pe.edu.upc.sems.organizations.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pe.edu.upc.sems.organizations.domain.model.OrganizationAccess
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.SessionManager
import pe.edu.upc.sems.shared.session.SessionMembership

/**
 * Loads the user's organization and role and caches them in the session (drawer header, role-based menu).
 * Called after login, at startup and after creating the organization. On a network error the cached
 * membership is kept, so the menu does not change while offline or while Render wakes up.
 */
class RefreshMembershipUseCase @Inject constructor(
    private val repository: OrganizationRepository,
    private val sessionManager: SessionManager,
) {
    suspend operator fun invoke(): ApiResult<OrganizationAccess?> {
        val userId = sessionManager.session.first()?.user?.userId ?: return ApiResult.Success(null)
        val result = repository.getMyOrganization(userId)
        if (result is ApiResult.Success) {
            sessionManager.saveMembership(result.data?.toSessionMembership())
        }
        return result
    }

    private fun OrganizationAccess.toSessionMembership() = SessionMembership(
        organizationId = organization.id,
        organizationName = organization.legalName,
        role = role.name,
        siteId = siteId,
    )
}
