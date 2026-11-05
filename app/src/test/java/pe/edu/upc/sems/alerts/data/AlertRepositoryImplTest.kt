package pe.edu.upc.sems.alerts.data

import java.time.Instant
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
import pe.edu.upc.sems.alerts.data.remote.AlertService
import pe.edu.upc.sems.alerts.data.repository.AlertRepositoryImpl
import pe.edu.upc.sems.alerts.domain.model.AlertSeverity
import pe.edu.upc.sems.alerts.domain.model.AlertStatus
import pe.edu.upc.sems.alerts.domain.model.AlertType
import pe.edu.upc.sems.shared.network.ApiResult
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** JSON shapes follow the backend's AlertResources (snake_case, lower case statuses). All data is made up. */
class AlertRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AlertRepositoryImpl

    private val alertJson = """
        {"alert_id":"alert-1","user_id":"user-1","device_id":"dev-1","alert_type":"threshold_exceeded",
         "title":"Consumption above threshold","message":"Too much","severity":"high","status":"active",
         "triggered_at":"2026-11-05T15:00:00Z"}
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
            .create(AlertService::class.java)
        repository = AlertRepositoryImpl(service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    @Test
    fun `list alerts maps the user's alerts`() = runBlocking {
        enqueue(200, "[$alertJson]")

        val alert = (repository.listAlerts("user-1") as ApiResult.Success).data.single()

        assertEquals("/api/v1/users/user-1/alerts", take().url.encodedPath)
        assertEquals("alert-1", alert.id)
        assertEquals("dev-1", alert.deviceId)
        assertEquals(AlertType.THRESHOLD, alert.type)
        assertEquals(AlertSeverity.HIGH, alert.severity)
        assertEquals(AlertStatus.ACTIVE, alert.status)
        assertEquals(Instant.parse("2026-11-05T15:00:00Z"), alert.triggeredAt)
        assertNull(alert.resolvedAt)
    }

    @Test
    fun `an alert with only the required fields still maps`() = runBlocking {
        enqueue(200, """[{"alert_id":"alert-2"}]""")

        val alert = (repository.listAlerts("user-1") as ApiResult.Success).data.single()

        assertNull(alert.deviceId)
        assertEquals("", alert.title)
        // Same defaults as the web app: unknown severity is medium, unknown status is active.
        assertEquals(AlertSeverity.MEDIUM, alert.severity)
        assertEquals(AlertStatus.ACTIVE, alert.status)
    }

    @Test
    fun `statuses and severities are read like the web app does`() {
        assertEquals(AlertStatus.RESOLVED, AlertStatus.from("closed"))
        assertEquals(AlertStatus.RESOLVED, AlertStatus.from("dismissed"))
        assertEquals(AlertStatus.RESOLVED, AlertStatus.from("RESOLVED"))
        assertEquals(AlertStatus.ACKNOWLEDGED, AlertStatus.from("acknowledged"))
        assertEquals(AlertStatus.ACTIVE, AlertStatus.from("open"))
        assertEquals(AlertStatus.ACTIVE, AlertStatus.from("pending"))
        assertEquals(AlertSeverity.HIGH, AlertSeverity.from("critical"))
        assertEquals(AlertSeverity.HIGH, AlertSeverity.from("HIGH"))
        assertEquals(AlertSeverity.LOW, AlertSeverity.from("low"))
        assertEquals(AlertType.INACTIVITY, AlertType.from("device_inactivity"))
        assertEquals(AlertType.ANOMALY, AlertType.from("anomaly_detected"))
    }

    @Test
    fun `acknowledging sends the lower case status and no resolved_at`() = runBlocking {
        enqueue(200, alertJson.replace("\"active\"", "\"acknowledged\""))

        val updated = (repository.updateStatus("alert-1", AlertStatus.ACKNOWLEDGED) as ApiResult.Success).data

        val request = take()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/alerts/alert-1/status", request.url.encodedPath)
        assertEquals("""{"status":"acknowledged"}""", request.body!!.utf8())
        assertEquals(AlertStatus.ACKNOWLEDGED, updated.status)
    }

    @Test
    fun `resolving sends resolved_at`() = runBlocking {
        enqueue(200, alertJson.replace("\"active\"", "\"resolved\""))

        repository.updateStatus("alert-1", AlertStatus.RESOLVED, Instant.parse("2026-11-05T18:40:00Z"))

        assertEquals(
            """{"status":"resolved","resolved_at":"2026-11-05T18:40:00Z"}""",
            take().body!!.utf8(),
        )
    }
}
