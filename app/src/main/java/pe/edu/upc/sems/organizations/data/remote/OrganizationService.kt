package pe.edu.upc.sems.organizations.data.remote

import pe.edu.upc.sems.organizations.data.remote.dto.CreateOrganizationRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.CreateSiteRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.GrantMembershipRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.MembershipDto
import pe.edu.upc.sems.organizations.data.remote.dto.MyOrganizationDto
import pe.edu.upc.sems.organizations.data.remote.dto.OrganizationDto
import pe.edu.upc.sems.organizations.data.remote.dto.SiteDto
import pe.edu.upc.sems.organizations.data.remote.dto.UpdateOrganizationRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.UpdateSiteRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.ZoneDto
import pe.edu.upc.sems.organizations.data.remote.dto.ZoneRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Organizations bounded context (backend OrganizationController, base /api/v1). */
interface OrganizationService {

    @GET("users/{userId}/organizations")
    suspend fun getMyOrganizations(@Path("userId") userId: String): List<MyOrganizationDto>

    @POST("organizations")
    suspend fun createOrganization(@Body body: CreateOrganizationRequestDto): OrganizationDto

    @PUT("organizations/{organizationId}")
    suspend fun updateOrganization(
        @Path("organizationId") organizationId: String,
        @Body body: UpdateOrganizationRequestDto,
    ): OrganizationDto

    @GET("organizations/{organizationId}/sites")
    suspend fun listSites(@Path("organizationId") organizationId: String): List<SiteDto>

    @POST("organizations/{organizationId}/sites")
    suspend fun createSite(
        @Path("organizationId") organizationId: String,
        @Body body: CreateSiteRequestDto,
    ): SiteDto

    @PUT("sites/{siteId}")
    suspend fun updateSite(@Path("siteId") siteId: String, @Body body: UpdateSiteRequestDto): SiteDto

    /** Archives the site. */
    @DELETE("sites/{siteId}")
    suspend fun archiveSite(@Path("siteId") siteId: String)

    @GET("sites/{siteId}/zones")
    suspend fun listZones(@Path("siteId") siteId: String): List<ZoneDto>

    @POST("sites/{siteId}/zones")
    suspend fun createZone(@Path("siteId") siteId: String, @Body body: ZoneRequestDto): ZoneDto

    @PUT("zones/{zoneId}")
    suspend fun updateZone(@Path("zoneId") zoneId: String, @Body body: ZoneRequestDto): ZoneDto

    /** Archives the zone. */
    @DELETE("zones/{zoneId}")
    suspend fun archiveZone(@Path("zoneId") zoneId: String)

    @GET("organizations/{organizationId}/members")
    suspend fun listMembers(@Path("organizationId") organizationId: String): List<MembershipDto>

    @POST("organizations/{organizationId}/members")
    suspend fun grantMember(
        @Path("organizationId") organizationId: String,
        @Body body: GrantMembershipRequestDto,
    ): MembershipDto

    @DELETE("organizations/{organizationId}/members/{membershipId}")
    suspend fun revokeMember(
        @Path("organizationId") organizationId: String,
        @Path("membershipId") membershipId: String,
    )
}
