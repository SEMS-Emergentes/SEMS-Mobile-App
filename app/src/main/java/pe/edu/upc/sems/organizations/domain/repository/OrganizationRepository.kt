package pe.edu.upc.sems.organizations.domain.repository

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
import pe.edu.upc.sems.shared.network.ApiResult

interface OrganizationRepository {

    /** The user's organization and role, or null when they have none yet (like the web app: the first one). */
    suspend fun getMyOrganization(userId: String): ApiResult<OrganizationAccess?>

    /** The owner becomes ORG_ADMIN automatically. 409 when the RUC is already registered. */
    suspend fun createOrganization(ownerUserId: String, organization: NewOrganization): ApiResult<Organization>

    suspend fun updateOrganization(organizationId: String, details: OrganizationDetails): ApiResult<Organization>

    /** Active sites the user's membership reaches. */
    suspend fun listSites(organizationId: String): ApiResult<List<Site>>

    /** 409 when the site code already exists in the organization. */
    suspend fun createSite(organizationId: String, site: SiteDraft): ApiResult<Site>

    /** The site code cannot be changed and is not sent. */
    suspend fun updateSite(siteId: String, site: SiteDraft): ApiResult<Site>

    /** Archives the site (the backend changes its status, it does not delete it). */
    suspend fun archiveSite(siteId: String): ApiResult<Unit>

    suspend fun listZones(siteId: String): ApiResult<List<Zone>>

    /** 409 when a zone with that name already exists in the site. */
    suspend fun createZone(siteId: String, zone: ZoneDraft): ApiResult<Zone>

    suspend fun updateZone(zoneId: String, zone: ZoneDraft): ApiResult<Zone>

    suspend fun archiveZone(zoneId: String): ApiResult<Unit>

    suspend fun listMembers(organizationId: String): ApiResult<List<Member>>

    /** 404 when nobody is registered with that email. */
    suspend fun grantMember(organizationId: String, grant: MemberGrant): ApiResult<Member>

    suspend fun revokeMember(organizationId: String, membershipId: String): ApiResult<Unit>
}
