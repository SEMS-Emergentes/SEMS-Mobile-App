package pe.edu.upc.sems.iam.presentation.login

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.iam.FakeAuthRepository
import pe.edu.upc.sems.shared.util.SlowNotice
import pe.edu.upc.sems.iam.presentation.common.FieldError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val viewModel = LoginViewModel(repository)

    private fun fill(email: String = "ana@example.com", password: String = "secret-1") {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    private fun submitWith(error: ApiError) {
        repository.loginResult = ApiResult.Failure(error)
        viewModel.onSubmit()
    }

    @Test
    fun `empty fields are required and nothing is sent`() = runTest(mainRule.dispatcher) {
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FieldError.REQUIRED, state.emailError)
        assertEquals(FieldError.REQUIRED, state.passwordError)
        assertTrue(repository.loginCalls.isEmpty())
    }

    @Test
    fun `malformed email is rejected before calling the backend`() = runTest(mainRule.dispatcher) {
        fill(email = "ana@example")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(FieldError.INVALID_EMAIL, viewModel.uiState.value.emailError)
        assertTrue(repository.loginCalls.isEmpty())
    }

    @Test
    fun `login does not apply the new-password length rule`() = runTest(mainRule.dispatcher) {
        fill(password = "abc")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("ana@example.com" to "abc"), repository.loginCalls)
    }

    @Test
    fun `email is trimmed before login`() = runTest(mainRule.dispatcher) {
        fill(email = "  ana@example.com ")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("ana@example.com" to "secret-1"), repository.loginCalls)
    }

    @Test
    fun `success keeps the loading state until the app leaves the screen`() = runTest(mainRule.dispatcher) {
        fill()
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `401 shows invalid credentials`() = runTest(mainRule.dispatcher) {
        fill()
        submitWith(ApiError.Unauthorized("Invalid credentials"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoginError.INVALID_CREDENTIALS, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `unverified account shows the verification fallback`() = runTest(mainRule.dispatcher) {
        fill()
        // Real backend message when REQUIRE_VERIFICATION is on.
        submitWith(ApiError.Unauthorized("Account is not verified yet"))
        advanceUntilIdle()

        assertEquals(LoginError.NOT_VERIFIED, viewModel.uiState.value.error)
    }

    @Test
    fun `network errors map to their banners`() = runTest(mainRule.dispatcher) {
        val cases = mapOf(
            ApiError.NoConnection to LoginError.NO_CONNECTION,
            ApiError.Timeout to LoginError.TIMEOUT,
            ApiError.Http(500, null) to LoginError.GENERIC,
        )
        fill()
        cases.forEach { (apiError, expected) ->
            submitWith(apiError)
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.error)
        }
    }

    @Test
    fun `editing a field clears the banner`() = runTest(mainRule.dispatcher) {
        fill()
        submitWith(ApiError.Unauthorized(null))
        advanceUntilIdle()

        viewModel.onPasswordChange("another")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `slow request shows Conectando after the threshold`() = runTest(mainRule.dispatcher) {
        repository.gate = CompletableDeferred()
        fill()
        viewModel.onSubmit()

        advanceTimeBy(SlowNotice.THRESHOLD_MS - 1)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSlow)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(viewModel.uiState.value.isSlow)

        repository.loginResult = ApiResult.Failure(ApiError.Timeout)
        repository.gate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSlow)
    }

    @Test
    fun `submitting twice while loading sends a single request`() = runTest(mainRule.dispatcher) {
        repository.gate = CompletableDeferred()
        fill()
        viewModel.onSubmit()
        runCurrent()
        viewModel.onSubmit()
        runCurrent()

        assertEquals(1, repository.loginCalls.size)
        repository.gate?.complete(Unit)
    }
}
