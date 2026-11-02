package pe.edu.upc.sems.shared.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SafeApiCallTest {

    private fun httpError(code: Int, body: String) =
        HttpException(Response.error<Unit>(code, body.toResponseBody()))

    @Test
    fun `success wraps the value`() = runBlocking {
        assertEquals(ApiResult.Success(42), safeApiCall { 42 })
    }

    @Test
    fun `401 with Spring error body maps to Unauthorized with message`() {
        // Real response of POST /auth/login with wrong credentials.
        val body = """{"timestamp":"2026-11-02T01:59:06Z","message":"Invalid credentials","error":"Unauthorized","status":401}"""
        assertEquals(ApiError.Unauthorized("Invalid credentials"), httpError(401, body).toApiError())
    }

    @Test
    fun `400 with code-message body maps to Http with message`() {
        // Real response of POST /auth/login with an empty body.
        val body = """{"code":"VALIDATION_ERROR","message":"emailAddress must not be blank; password must not be blank"}"""
        assertEquals(
            ApiError.Http(400, "emailAddress must not be blank; password must not be blank"),
            httpError(400, body).toApiError(),
        )
    }

    @Test
    fun `empty or non-JSON error body gives null message`() {
        assertEquals(ApiError.Unauthorized(null), httpError(401, "").toApiError())
        assertNull(parseErrorMessage("<html>Bad gateway</html>"))
        assertNull(parseErrorMessage("""{"message":""}"""))
    }

    @Test
    fun `IO failures map to Timeout or NoConnection`() {
        assertEquals(ApiError.Timeout, SocketTimeoutException().toApiError())
        assertEquals(ApiError.NoConnection, UnknownHostException().toApiError())
        assertEquals(ApiError.NoConnection, IOException().toApiError())
    }

    @Test
    fun `parsing failure maps to Serialization`() {
        assertTrue(SerializationException("bad").toApiError() is ApiError.Serialization)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown`(): Unit = runBlocking {
        safeApiCall { throw CancellationException("cancelled") }
    }
}
