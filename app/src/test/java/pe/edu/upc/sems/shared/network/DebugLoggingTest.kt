package pe.edu.upc.sems.shared.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugLoggingTest {

    private lateinit var server: MockWebServer
    private val lines = mutableListOf<String>()
    private val client = OkHttpClient.Builder()
        .addInterceptor(debugLoggingInterceptor { lines += it })
        .build()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun post(path: String, body: String, responseBody: String) {
        server.enqueue(MockResponse.Builder().code(200).body(responseBody).build())
        val request = Request.Builder()
            .url(server.url(path))
            .header("Authorization", "Bearer jwt-secret")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().close()
    }

    @Test
    fun `auth requests are logged without bodies`() {
        post(
            path = "/api/v1/auth/login",
            body = """{"emailAddress":"ana@example.com","password":"p4ssw0rd-made-up"}""",
            responseBody = """{"token":"jwt-made-up","refreshToken":"refresh-made-up"}""",
        )

        val log = lines.joinToString("\n")
        assertTrue(log.contains("--> POST"))
        assertFalse(log.contains("p4ssw0rd-made-up"))
        assertFalse(log.contains("refresh-made-up"))
        assertFalse(log.contains("jwt-secret"))
    }

    @Test
    fun `other requests keep their bodies, with the JWT redacted`() {
        post(path = "/api/v1/organizations", body = """{"name":"Cadena Flores"}""", responseBody = """{"id":"org-1"}""")

        val log = lines.joinToString("\n")
        assertTrue(log.contains("Cadena Flores"))
        assertTrue(log.contains("org-1"))
        assertFalse(log.contains("jwt-secret"))
    }
}
