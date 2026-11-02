package pe.edu.upc.sems.organizations.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pe.edu.upc.sems.organizations.data.remote.OrganizationService
import pe.edu.upc.sems.organizations.domain.model.BusinessType
import pe.edu.upc.sems.organizations.domain.model.MemberGrant
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.NewOrganization
import pe.edu.upc.sems.organizations.domain.model.OrgStatus
import pe.edu.upc.sems.organizations.domain.model.SiteDraft
import pe.edu.upc.sems.organizations.domain.model.TariffCategory
import pe.edu.upc.sems.organizations.domain.model.ZoneDraft
import pe.edu.upc.sems.organizations.domain.model.ZoneType
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** JSON shapes follow the backend's OrganizationResources (snake_case). All data is made up. */
class OrganizationRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: OrganizationRepositoryImpl

    private val organizationJson = """
        {"organization_id":"org-1","legal_name":"Cadena Flores S.A.C.","tax_id":"20123456789",
         "business_type":"SUPERMARKET","status":"ACTIVE","created_at":"2026-11-02T10:00:00Z"}
    """.trimIndent()

    private val siteJson = """
        {"site_id":"site-1","organization_id":"org-1","site_code":"LIM-001","name":"Local San Isidro",
         "address":"Av. Javier Prado 1234","district":"San Isidro","floor_area_m2":600,
         "contracted_power_kw":95.5,"tariff_category":"BT3","charges_for_demand":true,
         "excludes_sundays_from_peak":false,"status":"ACTIVE"}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        // Same Json settings as NetworkModule.
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
        val service = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OrganizationService::class.java)
        repository = OrganizationRepositoryImpl(service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    @Test
    fun `my organization comes with the membership role and site`() = runBlocking {
        enqueue(200, """[{"organization":$organizationJson,"role":"SUPERVISOR","site_id":"site-1"}]""")

        val access = (repository.getMyOrganization("user-1") as ApiResult.Success).data!!

        assertEquals("/api/v1/users/user-1/organizations", take().url.encodedPath)
        assertEquals("Cadena Flores S.A.C.", access.organization.legalName)
        assertNull(access.organization.tradeName)
        assertEquals(BusinessType.SUPERMARKET, access.organization.businessType)
        assertEquals(MembershipRole.SUPERVISOR, access.role)
        assertEquals("site-1", access.siteId)
    }

    @Test
    fun `no organization yet returns null`() = runBlocking {
        enqueue(200, "[]")
        assertEquals(ApiResult.Success(null), repository.getMyOrganization("user-1"))
    }

    @Test
    fun `create organization sends snake_case with the owner and omits a blank trade name`() = runBlocking {
        enqueue(201, organizationJson)

        val result = repository.createOrganization(
            ownerUserId = "user-1",
            organization = NewOrganization(" Cadena Flores S.A.C. ", "  ", "20123456789", BusinessType.SUPERMARKET),
        )

        assertEquals("org-1", (result as ApiResult.Success).data.id)
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/organizations", request.url.encodedPath)
        assertEquals(
            """{"legal_name":"Cadena Flores S.A.C.","tax_id":"20123456789","business_type":"SUPERMARKET","owner_user_id":"user-1"}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `duplicated RUC returns 409`() = runBlocking {
        enqueue(409, """{"code":"CONFLICT","message":"an organization with that tax_id already exists"}""")

        val result = repository.createOrganization("user-1", NewOrganization("X", null, "20123456789", BusinessType.OTHER))

        assertEquals(
            ApiResult.Failure(ApiError.Http(409, "an organization with that tax_id already exists")),
            result,
        )
    }

    @Test
    fun `sites are mapped, including the backend's charges_for_demand`() = runBlocking {
        enqueue(200, "[$siteJson]")

        val site = (repository.listSites("org-1") as ApiResult.Success).data.single()

        assertEquals("/api/v1/organizations/org-1/sites", take().url.encodedPath)
        assertEquals("LIM-001", site.siteCode)
        assertEquals(95.5, site.contractedPowerKw, 0.0)
        assertEquals(600.0, site.floorAreaM2!!, 0.0)
        assertEquals(TariffCategory.BT3, site.tariffCategory)
        assertEquals(true, site.chargesForDemand)
        assertEquals(OrgStatus.ACTIVE, site.status)
    }

    private val draft = SiteDraft(
        siteCode = " lim-001 ",
        name = "Local San Isidro",
        address = "",
        district = "San Isidro",
        floorAreaM2 = null,
        contractedPowerKw = 95.0,
        tariffCategory = TariffCategory.BT3,
        excludesSundaysFromPeak = false,
    )

    @Test
    fun `create site sends every required field, even false booleans`() = runBlocking {
        enqueue(201, siteJson)

        repository.createSite("org-1", draft)

        val request = take()
        assertEquals("/api/v1/organizations/org-1/sites", request.url.encodedPath)
        assertEquals(
            """{"site_code":"LIM-001","name":"Local San Isidro","district":"San Isidro","contracted_power_kw":95.0,""" +
                """"tariff_category":"BT3","excludes_sundays_from_peak":false}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `update site does not send the site code`() = runBlocking {
        enqueue(200, siteJson)

        repository.updateSite("site-1", draft.copy(excludesSundaysFromPeak = true))

        val request = take()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/sites/site-1", request.url.encodedPath)
        assertEquals(
            """{"name":"Local San Isidro","district":"San Isidro","contracted_power_kw":95.0,""" +
                """"tariff_category":"BT3","excludes_sundays_from_peak":true}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `archive site and zone use DELETE`() = runBlocking {
        enqueue(200)
        enqueue(200)

        assertEquals(ApiResult.Success(Unit), repository.archiveSite("site-1"))
        assertEquals(ApiResult.Success(Unit), repository.archiveZone("zone-1"))

        take().let { assertEquals("DELETE /api/v1/sites/site-1", "${it.method} ${it.url.encodedPath}") }
        take().let { assertEquals("DELETE /api/v1/zones/zone-1", "${it.method} ${it.url.encodedPath}") }
    }

    @Test
    fun `zones are created with their type and unknown types fall back to OTHER`() = runBlocking {
        enqueue(
            200,
            """{"zone_id":"zone-1","site_id":"site-1","name":"Refrigeración","zone_type":"COLD_STORAGE","operates_off_hours":true,"status":"ACTIVE"}""",
        )
        enqueue(200, """[{"zone_id":"zone-2","site_id":"site-1","name":"Patio","zone_type":"ROOFTOP"}]""")

        val created = (repository.createZone("site-1", ZoneDraft("Refrigeración", ZoneType.COLD_STORAGE, true)) as ApiResult.Success).data
        val listed = (repository.listZones("site-1") as ApiResult.Success).data.single()

        assertEquals(
            """{"name":"Refrigeración","zone_type":"COLD_STORAGE","operates_off_hours":true}""",
            take().body?.utf8(),
        )
        assertEquals(ZoneType.COLD_STORAGE, created.zoneType)
        assertEquals(ZoneType.OTHER, listed.zoneType)
    }

    @Test
    fun `members show email and role, and granting an unknown email returns 404`() = runBlocking {
        enqueue(
            200,
            """[{"membership_id":"m-1","organization_id":"org-1","user_id":"user-1","email":"ana@example.com","role":"ORG_ADMIN","status":"ACTIVE"}]""",
        )
        enqueue(404, """{"code":"NOT_FOUND","message":"no user registered with that email"}""")

        val member = (repository.listMembers("org-1") as ApiResult.Success).data.single()
        val grant = repository.grantMember("org-1", MemberGrant("nadie@example.com", MembershipRole.OPERATOR, null))

        assertEquals(MembershipRole.ORG_ADMIN, member.role)
        assertNull(member.siteId)
        take()
        assertEquals("""{"email":"nadie@example.com","role":"OPERATOR"}""", take().body?.utf8())
        assertEquals(ApiResult.Failure(ApiError.Http(404, "no user registered with that email")), grant)
    }

    @Test
    fun `unknown role is treated as the most restricted one`() {
        assertEquals(MembershipRole.UNKNOWN, MembershipRole.from("OWNER"))
        assertEquals(false, MembershipRole.UNKNOWN.canEditSites)
        assertEquals(true, MembershipRole.SUPERVISOR.canEditSites)
        assertEquals(false, MembershipRole.SUPERVISOR.canManageOrganization)
    }
}
