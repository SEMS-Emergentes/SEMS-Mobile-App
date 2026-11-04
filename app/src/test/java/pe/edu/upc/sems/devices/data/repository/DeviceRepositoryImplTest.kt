package pe.edu.upc.sems.devices.data.repository

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.edu.upc.sems.devices.data.remote.DeviceService
import pe.edu.upc.sems.devices.domain.model.ConnectionProtocol
import pe.edu.upc.sems.devices.domain.model.DeviceDraft
import pe.edu.upc.sems.devices.domain.model.DeviceStatus
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** JSON shapes follow the backend's DeviceResources (camelCase). All data is made up. */
class DeviceRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DeviceRepositoryImpl

    private val deviceJson = """
        {"deviceId":"dev-1","externalDeviceCode":"CAM-001","userId":"user-1","siteId":"site-1",
         "zoneId":"zone-1","deviceName":"Cámaras de frío","deviceType":"cold_room","brand":"Carrier",
         "model":"X1","connectionProtocol":"WIFI","status":"ACTIVE","registeredAt":"2026-11-02T10:00:00Z"}
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
            .create(DeviceService::class.java)
        repository = DeviceRepositoryImpl(service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    private val draft = DeviceDraft(
        externalCode = " CAM-001 ",
        siteId = "site-1",
        zoneId = "zone-1",
        name = " Cámaras de frío ",
        type = "cold_room",
        brand = "  ",
        model = "X1",
        protocol = ConnectionProtocol.BLUETOOTH,
    )

    @Test
    fun `list devices maps the user's devices`() = runBlocking {
        enqueue(200, "[$deviceJson]")

        val devices = (repository.listDevices("user-1") as ApiResult.Success).data

        assertEquals("/api/v1/device-management/users/user-1/devices", take().url.encodedPath)
        assertEquals(1, devices.size)
        with(devices.single()) {
            assertEquals("dev-1", id)
            assertEquals("Cámaras de frío", name)
            assertEquals("zone-1", zoneId)
            assertEquals(ConnectionProtocol.WIFI, protocol)
            assertEquals(DeviceStatus.ACTIVE, status)
        }
    }

    @Test
    fun `a device without zone, brand or model and with an unknown status still maps`() = runBlocking {
        enqueue(
            200,
            """[{"deviceId":"dev-2","siteId":"site-1","deviceName":"Horno","status":"SOMETHING_NEW"}]""",
        )

        val device = (repository.listDevices("user-1") as ApiResult.Success).data.single()

        assertNull(device.zoneId)
        assertNull(device.brand)
        assertEquals(DeviceStatus.UNKNOWN, device.status)
    }

    @Test
    fun `create device sends camelCase with the user and omits blank optionals`() = runBlocking {
        enqueue(201, deviceJson)

        val result = repository.createDevice("user-1", draft)

        assertEquals("dev-1", (result as ApiResult.Success).data.id)
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/device-management/devices", request.url.encodedPath)
        assertEquals(
            """{"externalDeviceCode":"CAM-001","userId":"user-1","siteId":"site-1","zoneId":"zone-1",""" +
                """"deviceName":"Cámaras de frío","deviceType":"cold_room","model":"X1","connectionProtocol":"BLUETOOTH"}""",
            request.body!!.utf8(),
        )
    }

    @Test
    fun `update device does not send the site or the code`() = runBlocking {
        enqueue(200, deviceJson)

        repository.updateDevice("dev-1", draft)

        val request = take()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/device-management/devices/dev-1", request.url.encodedPath)
        val body = request.body!!.utf8()
        assertTrue(body.contains(""""deviceName":"Cámaras de frío""""))
        assertTrue(!body.contains("siteId") && !body.contains("externalDeviceCode") && !body.contains("userId"))
    }

    @Test
    fun `delete device calls the endpoint`() = runBlocking {
        enqueue(204)

        assertEquals(ApiResult.Success(Unit), repository.deleteDevice("dev-1"))
        val request = take()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/device-management/devices/dev-1", request.url.encodedPath)
    }

    @Test
    fun `a server error becomes an ApiError`() = runBlocking {
        enqueue(409, """{"code":"CONFLICT","message":"device code already exists"}""")

        val failure = repository.createDevice("user-1", draft) as ApiResult.Failure

        assertEquals(ApiError.Http(409, "device code already exists"), failure.error)
    }
}
