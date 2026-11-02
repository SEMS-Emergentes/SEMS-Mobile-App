package pe.edu.upc.sems.shared.network.auth

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionEvent
import pe.edu.upc.sems.shared.session.SessionEvents
import pe.edu.upc.sems.shared.session.SessionUser
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Wires AuthInterceptor + TokenAuthenticator exactly like NetworkModule, against a fake server. */
class AuthNetworkTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var sessionEvents: SessionEvents
    private lateinit var client: OkHttpClient

    private val user = SessionUser("user-1", "ana@example.com", "Ana", setOf("USER"), "RETAIL_CHAIN")
    private val oldTokens = AuthTokens("jwt-1", "refresh-1")

    private val refreshOk =
        """{"token":"jwt-2","refreshToken":"refresh-2","userId":"user-1","emailAddress":"ana@example.com"}"""
    private val refreshRejected =
        """{"status":401,"error":"Unauthorized","message":"Invalid refresh token"}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        sessionManager = FakeSessionManager(Session(oldTokens, user, selectedSite = null))
        sessionEvents = SessionEvents()

        val json = Json { ignoreUnknownKeys = true }
        val baseClient = OkHttpClient.Builder().build()
        val refreshApi = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(baseClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TokenRefreshApi::class.java)

        client = baseClient.newBuilder()
            .apply { interceptors().add(0, AuthInterceptor(sessionManager)) }
            .authenticator(TokenAuthenticator(sessionManager, refreshApi, sessionEvents))
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun response(code: Int, body: String = "") = MockResponse.Builder().code(code).body(body).build()

    private fun get(path: String, configure: Request.Builder.() -> Unit = {}): Response =
        client.newCall(Request.Builder().url(server.url(path)).apply(configure).build()).execute()

    private fun takeRequest(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    private fun currentSession() = runBlocking { sessionManager.session.first() }

    private fun nextEvent() = runBlocking { withTimeoutOrNull(500) { sessionEvents.events.first() } }

    @Test
    fun `adds the bearer token to protected endpoints`() {
        server.enqueue(response(200))

        get("/api/v1/users/me").close()

        assertEquals("Bearer jwt-1", takeRequest().headers["Authorization"])
    }

    @Test
    fun `does not add the token to auth endpoints`() {
        server.enqueue(response(200))

        get("/api/v1/auth/login").close()

        assertNull(takeRequest().headers["Authorization"])
    }

    @Test
    fun `sends no token when logged out`() {
        runBlocking { sessionManager.clear() }
        server.enqueue(response(200))

        get("/api/v1/subscription-plans").close()

        assertNull(takeRequest().headers["Authorization"])
    }

    @Test
    fun `on 401 refreshes once, stores the new tokens and retries`() {
        server.enqueue(response(401))
        server.enqueue(response(200, refreshOk))
        server.enqueue(response(200, """{"ok":true}"""))

        val result = get("/api/v1/users/me")

        assertEquals(200, result.code)
        result.close()
        assertEquals("Bearer jwt-1", takeRequest().headers["Authorization"])
        val refresh = takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.url.encodedPath)
        assertNull(refresh.headers["Authorization"])
        assertEquals("""{"refreshToken":"refresh-1"}""", refresh.body?.utf8())
        assertEquals("Bearer jwt-2", takeRequest().headers["Authorization"])
        assertEquals(AuthTokens("jwt-2", "refresh-2"), currentSession()?.tokens)
    }

    @Test
    fun `401 from login is returned as is, without refreshing`() {
        server.enqueue(response(401, """{"message":"Invalid credentials"}"""))

        val result = get("/api/v1/auth/login")

        assertEquals(401, result.code)
        result.close()
        assertEquals(1, server.requestCount)
        assertEquals(oldTokens, currentSession()?.tokens)
    }

    @Test
    fun `rejected refresh clears the session and emits Expired`() {
        server.enqueue(response(401))
        server.enqueue(response(401, refreshRejected))

        val result = get("/api/v1/users/me")

        assertEquals(401, result.code)
        result.close()
        assertEquals(2, server.requestCount)
        assertNull(currentSession())
        assertEquals(SessionEvent.Expired, nextEvent())
    }

    @Test
    fun `refresh server error keeps the session`() {
        server.enqueue(response(401))
        server.enqueue(response(503))

        val result = get("/api/v1/users/me")

        assertEquals(401, result.code)
        result.close()
        assertEquals(oldTokens, currentSession()?.tokens)
        assertEquals(0, sessionManager.clearCount.get())
        assertNull(nextEvent())
    }

    @Test
    fun `second 401 after the retry does not refresh again`() {
        server.enqueue(response(401))
        server.enqueue(response(200, refreshOk))
        server.enqueue(response(401))

        val result = get("/api/v1/users/me")

        assertEquals(401, result.code)
        result.close()
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `reuses a token already refreshed by another request`() {
        // Sent with an outdated token; the session already holds a newer one.
        server.enqueue(response(401))
        server.enqueue(response(200))

        val result = get("/api/v1/users/me") { header("Authorization", "Bearer jwt-0") }

        assertEquals(200, result.code)
        result.close()
        assertEquals("Bearer jwt-0", takeRequest().headers["Authorization"])
        assertEquals("Bearer jwt-1", takeRequest().headers["Authorization"])
        assertEquals(2, server.requestCount) // no refresh call
    }

    @Test
    fun `concurrent 401s trigger a single refresh`() {
        val refreshCalls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath == "/api/v1/auth/refresh" -> {
                    refreshCalls.incrementAndGet()
                    Thread.sleep(100) // widen the race window
                    response(200, refreshOk)
                }
                request.headers["Authorization"] == "Bearer jwt-2" -> response(200)
                else -> response(401)
            }
        }
        val pool = Executors.newFixedThreadPool(5)

        val codes = (1..5)
            .map { pool.submit(Callable { get("/api/v1/sites/$it").use { r -> r.code } }) }
            .map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertTrue(codes.all { it == 200 })
        assertEquals(1, refreshCalls.get())
    }
}
