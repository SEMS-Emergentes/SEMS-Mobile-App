package pe.edu.upc.sems.organizations

import pe.edu.upc.sems.organizations.domain.model.BusinessType
import pe.edu.upc.sems.organizations.domain.model.Member
import pe.edu.upc.sems.organizations.domain.model.MemberGrant
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.NewOrganization
import pe.edu.upc.sems.organizations.domain.model.OrgStatus
import pe.edu.upc.sems.organizations.domain.model.Organization
import pe.edu.upc.sems.organizations.domain.model.OrganizationAccess
import pe.edu.upc.sems.organizations.domain.model.OrganizationDetails
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.domain.model.SiteDraft
import pe.edu.upc.sems.organizations.domain.model.TariffCategory
import pe.edu.upc.sems.organizations.domain.model.Zone
import pe.edu.upc.sems.organizations.domain.model.ZoneDraft
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult

/** Configurable fake. Calls that a test does not set up fail with a generic error. */
open class FakeOrganizationRepository : OrganizationRepository {

    val myOrganizationCalls = mutableListOf<String>()
    var myOrganizationResult: ApiResult<OrganizationAccess?> = ApiResult.Success(null)

    private val notConfigured = ApiResult.Failure(ApiError.Http(500, "not configured in the fake"))

    override suspend fun getMyOrganization(userId: String): ApiResult<OrganizationAccess?> {
        myOrganizationCalls += userId
        return myOrganizationResult
    }

    val createOrganizationCalls = mutableListOf<Pair<String, NewOrganization>>()
    var createOrganizationResult: ApiResult<Organization> = ApiResult.Success(ORGANIZATION)

    override suspend fun createOrganization(ownerUserId: String, organization: NewOrganization): ApiResult<Organization> {
        createOrganizationCalls += ownerUserId to organization
        return createOrganizationResult
    }

    override suspend fun updateOrganization(organizationId: String, details: OrganizationDetails): ApiResult<Organization> = notConfigured
    val listSitesCalls = mutableListOf<String>()
    var listSitesResult: ApiResult<List<Site>> = ApiResult.Success(emptyList())

    override suspend fun listSites(organizationId: String): ApiResult<List<Site>> {
        listSitesCalls += organizationId
        return listSitesResult
    }

    val createSiteCalls = mutableListOf<Pair<String, SiteDraft>>()
    var createSiteResult: ApiResult<Site> = ApiResult.Success(SITE)

    override suspend fun createSite(organizationId: String, site: SiteDraft): ApiResult<Site> {
        createSiteCalls += organizationId to site
        return createSiteResult
    }

    override suspend fun updateSite(siteId: String, site: SiteDraft): ApiResult<Site> = notConfigured
    override suspend fun archiveSite(siteId: String): ApiResult<Unit> = notConfigured
    override suspend fun listZones(siteId: String): ApiResult<List<Zone>> = notConfigured
    override suspend fun createZone(siteId: String, zone: ZoneDraft): ApiResult<Zone> = notConfigured
    override suspend fun updateZone(zoneId: String, zone: ZoneDraft): ApiResult<Zone> = notConfigured
    override suspend fun archiveZone(zoneId: String): ApiResult<Unit> = notConfigured
    override suspend fun listMembers(organizationId: String): ApiResult<List<Member>> = notConfigured
    override suspend fun grantMember(organizationId: String, grant: MemberGrant): ApiResult<Member> = notConfigured
    override suspend fun revokeMember(organizationId: String, membershipId: String): ApiResult<Unit> = notConfigured

    companion object {
        val ORGANIZATION = Organization(
            id = "org-1",
            legalName = "Cadena Flores S.A.C.",
            tradeName = null,
            taxId = "20123456789",
            businessType = BusinessType.SUPERMARKET,
            status = OrgStatus.ACTIVE,
        )

        val SITE = Site(
            id = "site-1",
            organizationId = "org-1",
            siteCode = "LIM-001",
            name = "Local San Isidro",
            address = null,
            district = "San Isidro",
            floorAreaM2 = null,
            contractedPowerKw = 95.0,
            tariffCategory = TariffCategory.BT3,
            chargesForDemand = true,
            excludesSundaysFromPeak = false,
            status = OrgStatus.ACTIVE,
        )

        fun access(role: MembershipRole, siteId: String? = null) = OrganizationAccess(ORGANIZATION, role, siteId)
    }
}
