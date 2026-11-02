package pe.edu.upc.sems.iam.presentation.register

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.edu.upc.sems.iam.FakeAuthRepository
import pe.edu.upc.sems.iam.presentation.common.FieldError
import pe.edu.upc.sems.shared.network.ApiError
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val viewModel = RegisterViewModel(repository)

    private fun fillValid(password: String = "secret") {
        viewModel.onFullNameChange("  Valeria Rojas ")
        viewModel.onEmailChange("valeria@example.com ")
        viewModel.onPasswordChange(password)
        viewModel.onTermsAcceptedChange(true)
    }

    @Test
    fun `all fields and the terms are validated locally`() = runTest(mainRule.dispatcher) {
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FieldError.REQUIRED, state.fullNameError)
        assertEquals(FieldError.REQUIRED, state.emailError)
        assertEquals(FieldError.REQUIRED, state.passwordError)
        assertEquals(FieldError.TERMS_REQUIRED, state.termsError)
        assertTrue(repository.registerCalls.isEmpty())
    }

    @Test
    fun `password needs at least 6 characters, like the web app and the backend`() = runTest(mainRule.dispatcher) {
        fillValid(password = "12345")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals(FieldError.PASSWORD_TOO_SHORT, viewModel.uiState.value.passwordError)

        viewModel.onPasswordChange("123456")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.passwordError)
        assertEquals(1, repository.registerCalls.size)
    }

    @Test
    fun `valid form registers with trimmed name and email`() = runTest(mainRule.dispatcher) {
        fillValid()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf(Triple("Valeria Rojas", "valeria@example.com", "secret")), repository.registerCalls)
        // Success: the session is stored and the app leaves the screen, so loading stays on.
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `409 means the email already has an account`() = runTest(mainRule.dispatcher) {
        repository.registerResult = ApiResult.Failure(ApiError.Http(409, "Email already exists"))
        fillValid()
        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RegisterError.EMAIL_TAKEN, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `other failures map to their banners`() = runTest(mainRule.dispatcher) {
        val cases = mapOf(
            ApiError.NoConnection to RegisterError.NO_CONNECTION,
            ApiError.Timeout to RegisterError.TIMEOUT,
            ApiError.Http(400, "Validation") to RegisterError.GENERIC,
        )
        fillValid()
        cases.forEach { (apiError, expected) ->
            repository.registerResult = ApiResult.Failure(apiError)
            viewModel.onSubmit()
            advanceUntilIdle()
            assertEquals(expected, viewModel.uiState.value.error)
        }
    }

    @Test
    fun `accepting the terms clears its error`() = runTest(mainRule.dispatcher) {
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onTermsAcceptedChange(true)

        assertNull(viewModel.uiState.value.termsError)
    }

    @Test
    fun `name longer than the backend limit is not accepted`() {
        viewModel.onFullNameChange("a".repeat(121))
        assertEquals("", viewModel.uiState.value.fullName)
    }
}
