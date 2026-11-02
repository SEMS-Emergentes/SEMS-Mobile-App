package pe.edu.upc.sems.organizations.data.repository

import javax.inject.Inject
import pe.edu.upc.sems.organizations.data.mapper.toCreateRequest
import pe.edu.upc.sems.organizations.data.mapper.toDomain
import pe.edu.upc.sems.organizations.data.mapper.toRequest
import pe.edu.upc.sems.organizations.data.mapper.toUpdateRequest
import pe.edu.upc.sems.organizations.data.remote.OrganizationService
import pe.edu.upc.sems.organizations.domain.model.Member
import pe.edu.upc.sems.organizations.domain.model.MemberGrant
import pe.edu.upc.sems.organizations.domain.model.NewOrganization
import pe.edu.upc.sems.organizations.domain.model.Organization
import pe.edu.upc.sems.organizations.domain.model.OrganizationAccess
import pe.edu.upc.sems.organizations.domain.model.OrganizationDetails
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.domain.model.SiteDraft
import pe.edu.upc.sems.organizations.domain.model.Zone
import pe.edu.upc.sems.organizations.domain.model.ZoneDraft
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.map
import pe.edu.upc.sems.shared.network.safeApiCall

class OrganizationRepositoryImpl @Inject constructor(
    private val service: OrganizationService,
) : OrganizationRepository {

    override suspend fun getMyOrganization(userId: String): ApiResult<OrganizationAccess?> =
        // Same as the web app: one organization per user for now, so the first membership is used.
        safeApiCall { service.getMyOrganizations(userId) }.map { it.firstOrNull()?.toDomain() }

    override suspend fun createOrganization(
        ownerUserId: String,
        organization: NewOrganization,
    ): ApiResult<Organization> =
        safeApiCall { service.createOrganization(organization.toRequest(ownerUserId)) }.map { it.toDomain() }

    override suspend fun updateOrganization(
        organizationId: String,
        details: OrganizationDetails,
    ): ApiResult<Organization> =
        safeApiCall { service.updateOrganization(organizationId, details.toRequest()) }.map { it.toDomain() }

    override suspend fun listSites(organizationId: String): ApiResult<List<Site>> =
        safeApiCall { service.listSites(organizationId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun getSite(siteId: String): ApiResult<Site> =
        safeApiCall { service.getSite(siteId) }.map { it.toDomain() }

    override suspend fun createSite(organizationId: String, site: SiteDraft): ApiResult<Site> =
        safeApiCall { service.createSite(organizationId, site.toCreateRequest()) }.map { it.toDomain() }

    override suspend fun updateSite(siteId: String, site: SiteDraft): ApiResult<Site> =
        safeApiCall { service.updateSite(siteId, site.toUpdateRequest()) }.map { it.toDomain() }

    override suspend fun archiveSite(siteId: String): ApiResult<Unit> =
        safeApiCall { service.archiveSite(siteId) }

    override suspend fun listZones(siteId: String): ApiResult<List<Zone>> =
        safeApiCall { service.listZones(siteId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun createZone(siteId: String, zone: ZoneDraft): ApiResult<Zone> =
        safeApiCall { service.createZone(siteId, zone.toRequest()) }.map { it.toDomain() }

    override suspend fun updateZone(zoneId: String, zone: ZoneDraft): ApiResult<Zone> =
        safeApiCall { service.updateZone(zoneId, zone.toRequest()) }.map { it.toDomain() }

    override suspend fun archiveZone(zoneId: String): ApiResult<Unit> =
        safeApiCall { service.archiveZone(zoneId) }

    override suspend fun listMembers(organizationId: String): ApiResult<List<Member>> =
        safeApiCall { service.listMembers(organizationId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun grantMember(organizationId: String, grant: MemberGrant): ApiResult<Member> =
        safeApiCall { service.grantMember(organizationId, grant.toRequest()) }.map { it.toDomain() }

    override suspend fun revokeMember(organizationId: String, membershipId: String): ApiResult<Unit> =
        safeApiCall { service.revokeMember(organizationId, membershipId) }
}
