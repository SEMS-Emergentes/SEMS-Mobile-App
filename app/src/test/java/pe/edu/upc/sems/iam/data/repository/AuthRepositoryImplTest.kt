package pe.edu.upc.sems.iam.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.edu.upc.sems.iam.data.remote.AuthService
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.session.AuthTokens
import pe.edu.upc.sems.shared.session.FakeSessionManager
import pe.edu.upc.sems.shared.session.Session
import pe.edu.upc.sems.shared.session.SessionUser
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** All data here is made up; real accounts are only used manually on the emulator. */
class AuthRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var repository: AuthRepositoryImpl

    private val loginResponse = """
        {"token":"jwt-1","refreshToken":"refresh-1","userId":"user-1","emailAddress":"ana@example.com",
         "roles":["ROLE_USER"],"fullName":"Ana Pérez","segment":"RETAIL_CHAIN","extraField":true}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        sessionManager = FakeSessionManager()
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
            .create(AuthService::class.java)
        repository = AuthRepositoryImpl(service, sessionManager)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun response(code: Int, body: String = "") = MockResponse.Builder().code(code).body(body).build()

    private fun session() = runBlocking { sessionManager.session.first() }

    @Test
    fun `login sends camelCase credentials and stores the session`() = runBlocking {
        server.enqueue(response(200, loginResponse))

        val result = repository.login("ana@example.com", "secret-1")

        assertEquals(ApiResult.Success(Unit), result)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/login", request.url.encodedPath)
        assertEquals("""{"emailAddress":"ana@example.com","password":"secret-1"}""", request.body?.utf8())
        assertEquals(
            Session(
                tokens = AuthTokens("jwt-1", "refresh-1"),
                user = SessionUser("user-1", "ana@example.com", "Ana Pérez", setOf("ROLE_USER"), "RETAIL_CHAIN"),
                selectedSite = null,
            ),
            session(),
        )
    }

    @Test
    fun `login accepts a response without optional fields`() = runBlocking {
        server.enqueue(
            response(200, """{"token":"t","refreshToken":"r","userId":"u","emailAddress":"e@example.com"}"""),
        )

        repository.login("e@example.com", "x")

        val user = session()!!.user
        assertNull(user.fullName)
        assertNull(user.segment)
        assertTrue(user.roles.isEmpty())
    }

    @Test
    fun `wrong credentials return Unauthorized and store nothing`() = runBlocking {
        server.enqueue(response(401, """{"message":"Invalid credentials","status":401}"""))

        val result = repository.login("ana@example.com", "wrong")

        assertEquals(ApiResult.Failure(ApiError.Unauthorized("Invalid credentials")), result)
        assertNull(session())
    }

    @Test
    fun `logout revokes the refresh token and clears the session`() = runBlocking {
        server.enqueue(response(200, loginResponse))
        repository.login("ana@example.com", "secret-1")
        server.takeRequest(5, TimeUnit.SECONDS)
        server.enqueue(response(200))

        repository.logout()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/logout", request.url.encodedPath)
        assertEquals("""{"refreshToken":"refresh-1"}""", request.body?.utf8())
        assertNull(session())
    }

    @Test
    fun `logout clears the session even if the backend fails`() = runBlocking {
        server.enqueue(response(200, loginResponse))
        repository.login("ana@example.com", "secret-1")
        server.enqueue(response(503))

        repository.logout()

        assertNull(session())
    }

    @Test
    fun `logout clears the session even without network`() = runBlocking {
        server.enqueue(response(200, loginResponse))
        repository.login("ana@example.com", "secret-1")
        server.close()

        repository.logout()

        assertNull(session())
    }

    @Test
    fun `register sends role STAFF without segment and stores the session`() = runBlocking {
        server.enqueue(response(200, loginResponse))

        val result = repository.register("Ana Pérez", "ana@example.com", "secret-1")

        assertEquals(ApiResult.Success(Unit), result)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/register", request.url.encodedPath)
        assertEquals(
            """{"emailAddress":"ana@example.com","password":"secret-1","role":"STAFF","fullName":"Ana Pérez"}""",
            request.body?.utf8(),
        )
        assertEquals("user-1", session()?.user?.userId)
    }

    @Test
    fun `register with a taken email returns 409 and stores nothing`() = runBlocking {
        server.enqueue(response(409, """{"code":"CONFLICT","message":"Email already exists"}"""))

        val result = repository.register("Ana", "ana@example.com", "secret-1")

        assertEquals(ApiResult.Failure(ApiError.Http(409, "Email already exists")), result)
        assertNull(session())
    }

    @Test
    fun `verify returns the account but only startSession stores it`() = runBlocking {
        server.enqueue(response(200, loginResponse))

        val result = repository.verifyAccount("code-123")

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/verify", request.url.encodedPath)
        assertEquals("""{"token":"code-123"}""", request.body?.utf8())
        assertNull(session())

        val account = (result as ApiResult.Success).data
        repository.startSession(account)
        assertEquals(AuthTokens("jwt-1", "refresh-1"), session()?.tokens)
    }

    @Test
    fun `password reset request and reset send camelCase bodies`() = runBlocking {
        server.enqueue(response(200, """{"message":"If that email is registered, you will receive a link."}"""))
        server.enqueue(response(200, """{"message":"Contrasena actualizada correctamente."}"""))

        assertEquals(ApiResult.Success(Unit), repository.requestPasswordReset("ana@example.com"))
        assertEquals(ApiResult.Success(Unit), repository.resetPassword("tok-1", "NuevaClave8"))

        val forgot = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/forgot-password", forgot.url.encodedPath)
        assertEquals("""{"emailAddress":"ana@example.com"}""", forgot.body?.utf8())
        val reset = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/reset-password", reset.url.encodedPath)
        assertEquals("""{"token":"tok-1","newPassword":"NuevaClave8"}""", reset.body?.utf8())
        // Resetting does not log in.
        assertNull(session())
    }
}
