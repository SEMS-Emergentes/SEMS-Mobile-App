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
import pe.edu.upc.sems.organizations.domain.model.ZoneType
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import pe.edu.upc.sems.shared.network.ApiResult

/** Configurable fake: every call is recorded and answers with the matching `...Result`. */
open class FakeOrganizationRepository : OrganizationRepository {

    val myOrganizationCalls = mutableListOf<String>()
    var myOrganizationResult: ApiResult<OrganizationAccess?> = ApiResult.Success(null)

    val createOrganizationCalls = mutableListOf<Pair<String, NewOrganization>>()
    var createOrganizationResult: ApiResult<Organization> = ApiResult.Success(ORGANIZATION)

    val updateOrganizationCalls = mutableListOf<Pair<String, OrganizationDetails>>()
    var updateOrganizationResult: ApiResult<Organization> = ApiResult.Success(ORGANIZATION)

    val listSitesCalls = mutableListOf<String>()
    var listSitesResult: ApiResult<List<Site>> = ApiResult.Success(emptyList())

    val getSiteCalls = mutableListOf<String>()
    var getSiteResult: ApiResult<Site> = ApiResult.Success(SITE)

    val createSiteCalls = mutableListOf<Pair<String, SiteDraft>>()
    var createSiteResult: ApiResult<Site> = ApiResult.Success(SITE)

    val updateSiteCalls = mutableListOf<Pair<String, SiteDraft>>()
    var updateSiteResult: ApiResult<Site> = ApiResult.Success(SITE)

    val archiveSiteCalls = mutableListOf<String>()
    var archiveSiteResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val listZonesCalls = mutableListOf<String>()
    var listZonesResult: ApiResult<List<Zone>> = ApiResult.Success(emptyList())

    val createZoneCalls = mutableListOf<Pair<String, ZoneDraft>>()
    var createZoneResult: ApiResult<Zone> = ApiResult.Success(ZONE)

    val updateZoneCalls = mutableListOf<Pair<String, ZoneDraft>>()
    var updateZoneResult: ApiResult<Zone> = ApiResult.Success(ZONE)

    val archiveZoneCalls = mutableListOf<String>()
    var archiveZoneResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val listMembersCalls = mutableListOf<String>()
    var listMembersResult: ApiResult<List<Member>> = ApiResult.Success(emptyList())

    val grantMemberCalls = mutableListOf<Pair<String, MemberGrant>>()
    var grantMemberResult: ApiResult<Member> = ApiResult.Success(MEMBER)

    val revokeMemberCalls = mutableListOf<Pair<String, String>>()
    var revokeMemberResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun getMyOrganization(userId: String): ApiResult<OrganizationAccess?> {
        myOrganizationCalls += userId
        return myOrganizationResult
    }

    override suspend fun createOrganization(ownerUserId: String, organization: NewOrganization): ApiResult<Organization> {
        createOrganizationCalls += ownerUserId to organization
        return createOrganizationResult
    }

    override suspend fun updateOrganization(organizationId: String, details: OrganizationDetails): ApiResult<Organization> {
        updateOrganizationCalls += organizationId to details
        return updateOrganizationResult
    }

    override suspend fun listSites(organizationId: String): ApiResult<List<Site>> {
        listSitesCalls += organizationId
        return listSitesResult
    }

    override suspend fun getSite(siteId: String): ApiResult<Site> {
        getSiteCalls += siteId
        return getSiteResult
    }

    override suspend fun createSite(organizationId: String, site: SiteDraft): ApiResult<Site> {
        createSiteCalls += organizationId to site
        return createSiteResult
    }

    override suspend fun updateSite(siteId: String, site: SiteDraft): ApiResult<Site> {
        updateSiteCalls += siteId to site
        return updateSiteResult
    }

    override suspend fun archiveSite(siteId: String): ApiResult<Unit> {
        archiveSiteCalls += siteId
        return archiveSiteResult
    }

    override suspend fun listZones(siteId: String): ApiResult<List<Zone>> {
        listZonesCalls += siteId
        return listZonesResult
    }

    override suspend fun createZone(siteId: String, zone: ZoneDraft): ApiResult<Zone> {
        createZoneCalls += siteId to zone
        return createZoneResult
    }

    override suspend fun updateZone(zoneId: String, zone: ZoneDraft): ApiResult<Zone> {
        updateZoneCalls += zoneId to zone
        return updateZoneResult
    }

    override suspend fun archiveZone(zoneId: String): ApiResult<Unit> {
        archiveZoneCalls += zoneId
        return archiveZoneResult
    }

    override suspend fun listMembers(organizationId: String): ApiResult<List<Member>> {
        listMembersCalls += organizationId
        return listMembersResult
    }

    override suspend fun grantMember(organizationId: String, grant: MemberGrant): ApiResult<Member> {
        grantMemberCalls += organizationId to grant
        return grantMemberResult
    }

    override suspend fun revokeMember(organizationId: String, membershipId: String): ApiResult<Unit> {
        revokeMemberCalls += organizationId to membershipId
        return revokeMemberResult
    }

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

        val ZONE = Zone(
            id = "zone-1",
            siteId = "site-1",
            name = "Refrigeración",
            zoneType = ZoneType.COLD_STORAGE,
            operatesOffHours = true,
            status = OrgStatus.ACTIVE,
        )

        val MEMBER = Member(
            membershipId = "m-1",
            userId = "user-1",
            email = "ana@example.com",
            role = MembershipRole.ORG_ADMIN,
            siteId = null,
            status = OrgStatus.ACTIVE,
        )

        fun access(role: MembershipRole, siteId: String? = null) = OrganizationAccess(ORGANIZATION, role, siteId)
    }
}
