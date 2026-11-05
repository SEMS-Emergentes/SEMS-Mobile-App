package pe.edu.upc.sems.energy.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pe.edu.upc.sems.energy.data.remote.EnergyService
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** A device or meter that has not reported yet is not an error: the backend answers 404 and the app shows "sin lecturas". */
class NoReadingsTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: EnergyRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
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
            .create(EnergyService::class.java)
        repository = EnergyRepositoryImpl(service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `a device with no readings is not an error`() = runBlocking {
        enqueue(404, """{"code":"NOT_FOUND","message":"No readings found for device 'dev-1'"}""")

        assertEquals(ApiResult.Success(null), repository.currentDeviceReading("dev-1"))
    }

    @Test
    fun `a meter with no readings is not an error`() = runBlocking {
        enqueue(404, """{"code":"NOT_FOUND","message":"No readings for meter 'meter-1'"}""")

        assertEquals(ApiResult.Success(null), repository.latestMeterReading("meter-1"))
    }

    @Test
    fun `other failures are still errors`() = runBlocking {
        enqueue(500, """{"code":"ERROR","message":"boom"}""")

        val result = repository.currentDeviceReading("dev-1")

        assertEquals(ApiError.Http(500, "boom"), (result as ApiResult.Failure).error)
    }

    @Test
    fun `a reading that exists is returned`() = runBlocking {
        enqueue(200, """{"meter_id":"meter-1","device_id":"dev-1","timestamp":"2026-11-05T15:00:00Z","power_watts":5200.0,"energy_kwh":1.3,"estimated_cost":0.78}""")

        val reading = (repository.currentDeviceReading("dev-1") as ApiResult.Success).data

        assertEquals(5.2, reading!!.powerKw!!, 1e-9)
        assertNull(null)
    }
}
