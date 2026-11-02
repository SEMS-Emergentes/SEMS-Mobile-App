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
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    /** Answers each login with [nextResult]; can be held open with [gate] to observe the loading state. */
    private class FakeAuthRepository : AuthRepository {
        val calls = mutableListOf<Pair<String, String>>()
        var nextResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun login(emailAddress: String, password: String): ApiResult<Unit> {
            calls += emailAddress to password
            gate?.await()
            return nextResult
        }

        override suspend fun logout() = Unit
    }

    private val repository = FakeAuthRepository()
    private val viewModel = LoginViewModel(repository)

    private fun fill(email: String = "ana@example.com", password: String = "secret-1") {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun `empty fields are required and nothing is sent`() = runTest(mainRule.dispatcher) {
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FieldError.REQUIRED, state.emailError)
        assertEquals(FieldError.REQUIRED, state.passwordError)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `malformed email is rejected before calling the backend`() = runTest(mainRule.dispatcher) {
        fill(email = "ana@example")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(FieldError.INVALID_EMAIL, viewModel.uiState.value.emailError)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `email is trimmed before login`() = runTest(mainRule.dispatcher) {
        fill(email = "  ana@example.com ")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("ana@example.com" to "secret-1"), repository.calls)
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
        repository.nextResult = ApiResult.Failure(ApiError.Unauthorized("Invalid credentials"))
        fill()
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LoginError.INVALID_CREDENTIALS, state.error)
        assertFalse(state.isLoading)
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
            repository.nextResult = ApiResult.Failure(apiError)
            viewModel.onSubmit()
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.error)
        }
    }

    @Test
    fun `editing a field clears the banner`() = runTest(mainRule.dispatcher) {
        repository.nextResult = ApiResult.Failure(ApiError.Unauthorized(null))
        fill()
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onPasswordChange("another")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `slow request shows Conectando after the threshold`() = runTest(mainRule.dispatcher) {
        repository.gate = CompletableDeferred()
        fill()
        viewModel.onSubmit()

        advanceTimeBy(LoginViewModel.SLOW_REQUEST_MS - 1)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSlow)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(viewModel.uiState.value.isSlow)

        repository.nextResult = ApiResult.Failure(ApiError.Timeout)
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

        assertEquals(1, repository.calls.size)
        repository.gate?.complete(Unit)
    }

    @Test
    fun `toggle shows and hides the password`() {
        viewModel.onTogglePasswordVisibility()
        assertTrue(viewModel.uiState.value.passwordVisible)
        viewModel.onTogglePasswordVisibility()
        assertFalse(viewModel.uiState.value.passwordVisible)
    }
}
